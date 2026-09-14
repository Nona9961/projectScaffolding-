package com.nona.inf.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.TraceIdentity;
import com.nona.util.JacksonUtil;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 隔离日志记录测试支持：把 {@code log_config.xml} 的 {@code LOG_PATH} 占位符重写为
 * 每用例独立的临时目录后装载专属 {@link LoggerContext}，并在该目录下轮询解析落盘 JSON 记录。
 * <p>
 * 供跟踪身份相关测试复用：记录断言锚定真实配置产出的 JSON 行（拉取模式行为等价验证），
 * 不触碰线程日志上下文内部。异步 / 缓冲落盘存在时序差异，读取采用轮询等待，禁止固定睡眠。
 *
 * @author nona9961
 */
@ScaffoldGenerated
public final class IsolatedLogTestSupport {

    private static final String CONFIG_RESOURCE = "log_config.xml";
    private static final String LOG_PATH_ENV_PLACEHOLDER = "${env:LOG_PATH:-./logs}";
    private static final String EFFECTIVE_CONFIG_FILE_NAME = "log_config_effective.xml";
    private static final Duration LOG_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MILLIS = 100L;

    /**
     * 工具类：禁止实例化。
     */
    private IsolatedLogTestSupport() {
    }

    /**
     * 装载隔离日志上下文：配置文本中的 {@code LOG_PATH} 占位符替换为给定目录，
     * 上下文为测试专属实例（不经全局 context selector 单例），用例间零共享。
     *
     * @param contextName 上下文名称前缀（实际名称追加随机后缀）
     * @param logDir      日志目录（通常为 {@code @TempDir}）
     * @return 已启动的日志上下文
     * @throws IOException 配置资源读取或配置文本写出失败
     */
    public static LoggerContext startIsolatedContext(String contextName, Path logDir) throws IOException {
        final String rawConfig = readConfigText();
        assertThat(rawConfig)
                .as("LOG_PATH must be environment-overridable in the form '%s'", LOG_PATH_ENV_PLACEHOLDER)
                .contains(LOG_PATH_ENV_PLACEHOLDER);

        final Path effectiveConfig = logDir.resolve(EFFECTIVE_CONFIG_FILE_NAME);
        Files.writeString(effectiveConfig,
                rawConfig.replace(LOG_PATH_ENV_PLACEHOLDER, logDir.toString()),
                StandardCharsets.UTF_8);

        final LoggerContext context = new LoggerContext(contextName + "-" + UUID.randomUUID());
        try (InputStream in = Files.newInputStream(effectiveConfig)) {
            final ConfigurationSource source = new ConfigurationSource(in, effectiveConfig.toFile());
            final Configuration configuration = ConfigurationFactory.getInstance()
                    .getConfiguration(context, source);
            assertThat(configuration)
                    .as("effective config '%s' must load into the fresh logger context", effectiveConfig)
                    .isNotNull();
            context.start(configuration);
        }
        return context;
    }

    /**
     * 轮询隔离日志目录，直到含给定标记的记录行出现并解析为 JSON 对象。
     *
     * @param logDir 日志目录
     * @param marker 用例唯一探针标记
     * @return 记录 JSON 节点
     * @throws Exception 探针记录超时未出现或落盘内容不是 JSON 对象
     */
    public static JsonNode awaitRecord(Path logDir, String marker) throws Exception {
        final Optional<String> line = awaitLogLine(logDir, marker, LOG_WAIT_TIMEOUT);
        assertThat(line)
                .as("file appender must write the '%s' probe record under the isolated LOG_PATH within %s",
                        marker, LOG_WAIT_TIMEOUT)
                .isPresent();
        final String recordLine = line.orElseThrow();
        assertThat(JacksonUtil.isObject(recordLine))
                .as("persisted record for '%s' must be a JSON object, but was: %s", marker, recordLine)
                .isTrue();
        return JacksonUtil.DEFAULT_MAPPER.readTree(recordLine);
    }

    /**
     * 断言记录携带跟踪身份三字段且取值与给定身份一致。
     *
     * @param record   记录 JSON 节点
     * @param identity 期望的跟踪身份
     */
    public static void assertTraceFieldsPresent(JsonNode record, TraceIdentity identity) {
        assertThat(record.path(TraceContextDataProvider.TRACE_ID_KEY).asText())
                .as("record must carry trace_id from the execution context: %s", record)
                .isEqualTo(identity.traceId());
        assertThat(record.path(TraceContextDataProvider.SPAN_ID_KEY).asText())
                .as("record must carry span_id from the execution context: %s", record)
                .isEqualTo(identity.spanId());
        assertThat(record.path(TraceContextDataProvider.TRACE_FLAGS_KEY).asText())
                .as("record must carry trace_flags from the execution context: %s", record)
                .isEqualTo(identity.traceFlags());
    }

    /**
     * 断言记录省略跟踪身份三字段（无可见身份时不得凭空注入）。
     *
     * @param record 记录 JSON 节点
     */
    public static void assertTraceFieldsAbsent(JsonNode record) {
        assertThat(record.hasNonNull(TraceContextDataProvider.TRACE_ID_KEY))
                .as("record must omit trace_id when no identity is visible: %s", record)
                .isFalse();
        assertThat(record.hasNonNull(TraceContextDataProvider.SPAN_ID_KEY))
                .as("record must omit span_id when no identity is visible: %s", record)
                .isFalse();
        assertThat(record.hasNonNull(TraceContextDataProvider.TRACE_FLAGS_KEY))
                .as("record must omit trace_flags when no identity is visible: %s", record)
                .isFalse();
    }

    /**
     * 轮询目录文本直到探针标记出现。
     *
     * @param logDir  日志目录
     * @param marker  探针标记
     * @param timeout 等待上限
     * @return 含标记的记录行；超时为 {@link Optional#empty()}
     * @throws InterruptedException 轮询等待被中断
     */
    private static Optional<String> awaitLogLine(Path logDir, String marker, Duration timeout)
            throws InterruptedException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final Optional<String> line = findRecordLine(logDir, marker);
            if (line.isPresent()) {
                return line;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return Optional.empty();
    }

    /**
     * 读取目录下全部普通文件文本并定位含标记的首行；容忍写入中的不一致
     * （单文件读取失败跳过，由轮询重试）。
     *
     * @param root   目录
     * @param marker 探针标记
     * @return 含标记的行；未找到时为空
     */
    private static Optional<String> findRecordLine(Path root, String marker) {
        final StringBuilder content = new StringBuilder();
        try (Stream<Path> paths = Files.walk(root)) {
            for (final Path file : paths.filter(Files::isRegularFile).toList()) {
                try {
                    content.append(Files.readString(file)).append('\n');
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return Arrays.stream(content.toString().split("\n"))
                .filter(candidate -> candidate.contains(marker))
                .findFirst();
    }

    /**
     * 从 classpath 读取配置文本（UTF-8）。
     *
     * @return 配置文本
     * @throws IOException 配置资源缺失或读取失败
     */
    private static String readConfigText() throws IOException {
        try (InputStream in = openConfigResource()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 打开配置资源流。
     *
     * @return 配置资源输入流
     * @throws IOException classpath 上缺少配置资源
     */
    private static InputStream openConfigResource() throws IOException {
        final InputStream resource =
                IsolatedLogTestSupport.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE);
        if (resource == null) {
            throw new IOException("classpath resource '" + CONFIG_RESOURCE + "' must exist");
        }
        return resource;
    }
}
