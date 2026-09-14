package com.nona.inf.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.util.JacksonUtil;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.layout.template.json.JsonTemplateLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code log_config.xml} 真实环境装配面测试：进程环境解析、独立上下文装载与真实文件落盘。
 * <p>
 * 装配面覆盖：
 * <ul>
 *   <li>进程环境解析：未重写的配置中 {@code ${env:LOG_PATH:-./logs}} 按真实进程环境变量展开
 *       （由 surefire 注入），文件 appender 的落盘路径随之重定向</li>
 *   <li>classpath 装配：未重写的配置可装载，文件 appender 由 classpath 模板资源构建出
 *       {@code JsonTemplateLayout}；异步接线以 context selector 组件属性声明为准</li>
 *   <li>真实持久化：带 MDC 的记录经真实文件 appender 落盘为单行 JSON 对象，
 *       OTel 字段集齐全且 trace 字段取 MDC 值</li>
 * </ul>
 * <p>
 * 不启动 Spring 容器；装载使用独立 {@link LoggerContext}，不影响同 JVM 内其他测试类的日志上下文。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class LogConfigRealEnvironmentIntegrationTest {

    private static final String CONFIG_RESOURCE = "log_config.xml";
    private static final String COMPONENT_PROPERTIES_RESOURCE = "log4j2.component.properties";
    private static final String ASYNC_SELECTOR_NAME = "AsyncLoggerContextSelector";
    private static final String CONTEXT_NAME = "log-config-real-environment-integration-test";
    private static final String PROBE_LOGGER = "com.nona.inf.logging.LogConfigRealEnvironmentIntegrationTest";
    private static final String TRACE_ID_KEY = "trace_id";
    private static final String SPAN_ID_KEY = "span_id";
    private static final String TRACE_FLAGS_KEY = "trace_flags";
    private static final String TRACE_ID_VALUE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID_VALUE = "00f067aa0ba902b7";
    private static final String TRACE_FLAGS_VALUE = "01";
    private static final List<String> OTEL_FIELDS = List.of(
            "timestamp", "level", "logger", "thread", "message",
            TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY);
    private static final Duration LOG_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MILLIS = 100L;

    /**
     * 未重写配置副本的工作目录。
     */
    @TempDir
    Path workDir;

    private LoggerContext loggerContext;

    // ========== fixtures ==========

    /**
     * 以未重写的 classpath 配置装载本测试方法专属的日志上下文。
     *
     * @throws Exception 配置资源读取或装载失败
     */
    @BeforeEach
    void setUp() throws Exception {
        loggerContext = loadRealConfig(workDir);
    }

    /**
     * 释放本测试方法专属的日志上下文。
     */
    @AfterEach
    void tearDown() {
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    // ========== assembly faces ==========

    /**
     * 进程环境解析：LOG_PATH 由 surefire 注入测试 JVM，文件 appender 的落盘路径以该值开头。
     */
    @Test
    void shouldResolveLogPathFromRealProcessEnvironment() {
        final String envLogPath = System.getenv("LOG_PATH");
        assertThat(envLogPath)
                .as("surefire must inject LOG_PATH into the test JVM process environment")
                .isNotBlank();

        final Optional<RollingFileAppender> fileAppender = findFileAppender(loggerContext);
        assertThat(fileAppender)
                .as("unrewritten config must register the rolling file appender")
                .isPresent();
        assertThat(fileAppender.orElseThrow().getFileName())
                .as("file appender must resolve LOG_PATH from the real process environment")
                .startsWith(envLogPath);
    }

    /**
     * classpath 装配：文件 appender 由 classpath 模板资源构建出 {@code JsonTemplateLayout}，
     * 异步接线由 context selector 组件属性声明。
     *
     * @throws IOException 组件属性资源读取失败
     */
    @Test
    void shouldAssembleClasspathJsonTemplateLayoutWithAsyncSelectorBinding() throws IOException {
        final Optional<RollingFileAppender> fileAppender = findFileAppender(loggerContext);
        assertThat(fileAppender)
                .as("unrewritten config must register the rolling file appender")
                .isPresent();
        final Object layout = fileAppender.orElseThrow().getLayout();
        assertThat(layout)
                .as("classpath template resource must build a JsonTemplateLayout on the file appender")
                .isInstanceOf(JsonTemplateLayout.class);

        try (InputStream in = openResource(COMPONENT_PROPERTIES_RESOURCE)) {
            final String componentProperties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(componentProperties)
                    .as("async logging must be bound through the context selector component property")
                    .contains(ASYNC_SELECTOR_NAME);
        }
    }

    /**
     * 真实持久化：带 MDC 的记录经真实文件 appender 落盘为单行 JSON 对象，OTel 字段集齐全且
     * trace 字段取 MDC 值。
     *
     * @throws Exception 记录读取或 JSON 解析失败
     */
    @Test
    void shouldPersistOtelJsonRecordThroughRealConfiguredAppender() throws Exception {
        final String envLogPath = System.getenv("LOG_PATH");
        assertThat(envLogPath)
                .as("surefire must inject LOG_PATH into the test JVM process environment")
                .isNotBlank();

        final String probeMessage = "real-env-otel-probe-" + UUID.randomUUID();
        ThreadContext.put(TRACE_ID_KEY, TRACE_ID_VALUE);
        ThreadContext.put(SPAN_ID_KEY, SPAN_ID_VALUE);
        ThreadContext.put(TRACE_FLAGS_KEY, TRACE_FLAGS_VALUE);
        try {
            loggerContext.getLogger(PROBE_LOGGER).info(probeMessage);
        } finally {
            ThreadContext.remove(TRACE_ID_KEY);
            ThreadContext.remove(SPAN_ID_KEY);
            ThreadContext.remove(TRACE_FLAGS_KEY);
        }

        final Optional<String> recordLine = awaitRecordLine(Path.of(envLogPath), probeMessage, LOG_WAIT_TIMEOUT);
        assertThat(recordLine)
                .as("file appender must persist the probe record under the injected LOG_PATH within %s",
                        LOG_WAIT_TIMEOUT)
                .isPresent();
        final String line = recordLine.orElseThrow();
        assertThat(JacksonUtil.isObject(line))
                .as("persisted record must be a single-line JSON object, but was: %s", line)
                .isTrue();

        final JsonNode record = JacksonUtil.DEFAULT_MAPPER.readTree(line);
        for (final String field : OTEL_FIELDS) {
            assertThat(record.hasNonNull(field))
                    .as("persisted record must carry OTel field '%s': %s", field, record)
                    .isTrue();
        }
        assertThat(record.path(TRACE_ID_KEY).asText())
                .as("trace_id must carry the MDC value")
                .isEqualTo(TRACE_ID_VALUE);
        assertThat(record.path(SPAN_ID_KEY).asText())
                .as("span_id must carry the MDC value")
                .isEqualTo(SPAN_ID_VALUE);
        assertThat(record.path(TRACE_FLAGS_KEY).asText())
                .as("trace_flags must carry the MDC value")
                .isEqualTo(TRACE_FLAGS_VALUE);
        assertThat(record.path("message").asText())
                .as("probe message must be recorded")
                .isEqualTo(probeMessage);
    }

    // ========== helpers ==========

    /**
     * 装载未重写的 classpath 配置到独立日志上下文。
     *
     * @param workDir 配置副本工作目录
     * @return 已启动的日志上下文
     * @throws Exception 配置读取或装载失败
     */
    private static LoggerContext loadRealConfig(Path workDir) throws Exception {
        final Path configCopy = workDir.resolve("log_config_real_env.xml");
        try (InputStream in = openResource(CONFIG_RESOURCE)) {
            Files.copy(in, configCopy, StandardCopyOption.REPLACE_EXISTING);
        }

        final LoggerContext context = new LoggerContext(CONTEXT_NAME + "-" + UUID.randomUUID());
        try (InputStream in = Files.newInputStream(configCopy)) {
            final ConfigurationSource source = new ConfigurationSource(in, configCopy.toFile());
            final Configuration configuration = ConfigurationFactory.getInstance()
                    .getConfiguration(context, source);
            assertThat(configuration)
                    .as("unrewritten classpath config must load into the fresh logger context")
                    .isNotNull();
            context.start(configuration);
        }
        return context;
    }

    /**
     * 从已启动的上下文定位文件 appender。
     *
     * @param context 日志上下文
     * @return 滚动文件 appender；未注册时为空
     */
    private static Optional<RollingFileAppender> findFileAppender(LoggerContext context) {
        return context.getConfiguration().getAppenders().values().stream()
                .filter(RollingFileAppender.class::isInstance)
                .map(RollingFileAppender.class::cast)
                .findFirst();
    }

    /**
     * 轮询日志目录直到含探针标记的记录行出现。
     *
     * @param logDir  日志目录
     * @param marker  探针标记
     * @param timeout 等待上限
     * @return 含探针标记的行；超时为 {@link Optional#empty()}
     * @throws InterruptedException 轮询等待被中断
     */
    private static Optional<String> awaitRecordLine(Path logDir, String marker, Duration timeout)
            throws InterruptedException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final Optional<String> line = findLine(logDir, marker);
            if (line.isPresent()) {
                return line;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return Optional.empty();
    }

    /**
     * 在日志目录全部普通文件中查找含标记的行。
     *
     * @param root   目录
     * @param marker 探针标记
     * @return 匹配的行；未找到时为空
     */
    private static Optional<String> findLine(Path root, String marker) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (final Path file : paths.filter(Files::isRegularFile).toList()) {
                try {
                    final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (final String line : lines) {
                        if (line.contains(marker)) {
                            return Optional.of(line);
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return Optional.empty();
    }

    /**
     * 打开 classpath 资源流。
     *
     * @param resource 资源名
     * @return 资源输入流
     */
    private static InputStream openResource(String resource) {
        final Optional<InputStream> stream = Optional.ofNullable(
                LogConfigRealEnvironmentIntegrationTest.class.getClassLoader().getResourceAsStream(resource));
        assertThat(stream)
                .as("classpath resource '%s' must exist", resource)
                .isPresent();
        return stream.orElseThrow();
    }
}
