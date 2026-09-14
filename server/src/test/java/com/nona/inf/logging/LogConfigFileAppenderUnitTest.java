package com.nona.inf.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.util.JacksonUtil;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code log_config.xml} 文件 appender 行为场景测试：真实加载配置、隔离日志目录、落盘 JSON 记录。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：带 MDC ctx 键的日志记录落盘为 JSON，含 OTel 字段集且 trace 字段取 MDC 值</li>
 *   <li>Critical：无 MDC 上下文的记录仍落盘为合法 JSON（缺失 ctx 键不破坏写入）</li>
 * </ul>
 * <p>
 * 隔离方式：JVM 内无法设置进程环境变量，故将配置文本中的 {@code ${env:LOG_PATH:-./logs}}
 * 替换为每测试独立的 {@link TempDir} 后加载；日志上下文同样按测试方法独立创建（不经全局
 * context selector 单例），避免同一 JVM 内用例间复用污染；{@code LOG_PATH} 的进程级环境
 * 解析语义由装配面用例在真实进程中验证。
 * <p>
 * 落盘断言采用轮询等待：日志写入到文件可见存在时序差异（异步 appender 配置与文件 I/O），
 * 禁止固定睡眠。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class LogConfigFileAppenderUnitTest {

    private static final String CONFIG_RESOURCE = "log_config.xml";
    private static final String LOG_PATH_ENV_PLACEHOLDER = "${env:LOG_PATH:-./logs}";
    private static final String CONTEXT_NAME = "log-config-file-appender-unit-test";
    private static final String PROBE_LOGGER = "com.nona.inf.logging.LogConfigFileAppenderUnitTest";
    private static final String MDC_PROBE_MESSAGE = "otel-mdc-probe";
    private static final String PLAIN_PROBE_MESSAGE = "otel-plain-probe";
    private static final String TRACE_ID_KEY = "trace_id";
    private static final String SPAN_ID_KEY = "span_id";
    private static final String TRACE_FLAGS_KEY = "trace_flags";
    private static final String TRACE_ID_VALUE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID_VALUE = "00f067aa0ba902b7";
    private static final String TRACE_FLAGS_VALUE = "01";
    private static final Duration LOG_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MILLIS = 100L;
    private static final Duration TIMESTAMP_WINDOW = Duration.ofHours(1);

    /**
     * 毫秒量级时间戳判定的下界：小于该值的数字视为非毫秒量级（如秒），跳过窗口校验。
     */
    private static final long EPOCH_MILLIS_LOWER_BOUND = 1_000_000_000_000L;

    /**
     * 按测试方法隔离的日志目录（{@code LOG_PATH} 替换目标）。
     */
    @TempDir
    Path logDir;

    private LoggerContext loggerContext;

    private String fileAppenderName;

    // ========== fixtures ==========

    /**
     * 装配：校验 LOG_PATH 环境覆盖形式、重写为隔离目录、以本方法专属上下文加载配置并确认
     * 文件 appender 已注册。
     */
    @BeforeEach
    void setUp() throws Exception {
        final String rawConfig = readConfigText();
        assertThat(rawConfig)
                .as("LOG_PATH must be environment-overridable in the form '%s' so tests can isolate the log directory",
                        LOG_PATH_ENV_PLACEHOLDER)
                .contains(LOG_PATH_ENV_PLACEHOLDER);

        final Path effectiveConfig = logDir.resolve("log_config_effective.xml");
        Files.writeString(effectiveConfig,
                rawConfig.replace(LOG_PATH_ENV_PLACEHOLDER, logDir.toString()),
                StandardCharsets.UTF_8);

        loggerContext = new LoggerContext(CONTEXT_NAME + "-" + UUID.randomUUID());
        try (InputStream in = Files.newInputStream(effectiveConfig)) {
            final ConfigurationSource source = new ConfigurationSource(in, effectiveConfig.toFile());
            final Configuration configuration = ConfigurationFactory.getInstance()
                    .getConfiguration(loggerContext, source);
            assertThat(configuration)
                    .as("effective config '%s' must load into the fresh logger context", effectiveConfig)
                    .isNotNull();
            loggerContext.start(configuration);
        }

        fileAppenderName = findFileAppender(parseDocument(rawConfig)).getAttribute("name");
        final Appender appender = loggerContext.getConfiguration().getAppender(fileAppenderName);
        assertThat(appender)
                .as("file appender '%s' must be registered after loading the effective config", fileAppenderName)
                .isNotNull();
    }

    /**
     * 释放本测试方法专属的日志上下文；装配前置失败时上下文尚未创建，跳过。
     */
    @AfterEach
    void tearDown() {
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    // ========== Happy path ==========

    /**
     * 带 MDC ctx 键的日志记录落盘为 JSON：基础字段与 trace 关联字段齐全，
     * trace 字段值等于写入的 MDC 值，时间戳落在相对当前时刻的窗口内。
     */
    @Test
    void shouldPersistOtelJsonRecordWithMdcFieldsUnderIsolatedLogPath() throws Exception {
        ThreadContext.put(TRACE_ID_KEY, TRACE_ID_VALUE);
        ThreadContext.put(SPAN_ID_KEY, SPAN_ID_VALUE);
        ThreadContext.put(TRACE_FLAGS_KEY, TRACE_FLAGS_VALUE);
        try {
            loggerContext.getLogger(PROBE_LOGGER).info(MDC_PROBE_MESSAGE);
        } finally {
            ThreadContext.remove(TRACE_ID_KEY);
            ThreadContext.remove(SPAN_ID_KEY);
            ThreadContext.remove(TRACE_FLAGS_KEY);
        }

        final JsonNode record = awaitRecord(logDir, MDC_PROBE_MESSAGE);
        assertFieldsPresent(record, "timestamp", "level", "logger", "thread", "message",
                TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY);
        assertThat(record.path("level").toString())
                .as("level must record the INFO severity")
                .contains("INFO");
        assertThat(record.path("logger").toString())
                .as("logger name must be recorded")
                .contains(PROBE_LOGGER);
        assertThat(record.path("message").toString())
                .as("probe message must be recorded")
                .contains(MDC_PROBE_MESSAGE);
        assertThat(record.path("thread").asText())
                .as("thread name must be recorded")
                .isNotBlank();
        assertThat(record.path(TRACE_ID_KEY).asText())
                .as("trace_id must carry the MDC value")
                .isEqualTo(TRACE_ID_VALUE);
        assertThat(record.path(SPAN_ID_KEY).asText())
                .as("span_id must carry the MDC value")
                .isEqualTo(SPAN_ID_VALUE);
        assertThat(record.path(TRACE_FLAGS_KEY).asText())
                .as("trace_flags must carry the MDC value")
                .isEqualTo(TRACE_FLAGS_VALUE);
        assertTimestampWithinRelativeWindow(record);
    }

    // ========== Critical path ==========

    /**
     * 无 MDC 上下文时记录仍落盘为合法 JSON 对象且基础字段齐全——
     * 缺失 ctx 键不影响文件写入。
     */
    @Test
    void shouldPersistValidJsonRecordWithoutMdcContext() throws Exception {
        loggerContext.getLogger(PROBE_LOGGER).info(PLAIN_PROBE_MESSAGE);

        final JsonNode record = awaitRecord(logDir, PLAIN_PROBE_MESSAGE);
        assertFieldsPresent(record, "timestamp", "level", "logger", "thread", "message");
        assertThat(record.path("message").toString())
                .as("probe message must be recorded")
                .contains(PLAIN_PROBE_MESSAGE);
    }

    // ========== helpers ==========

    /**
     * 轮询隔离目录，直到探针标记出现并解析为 JSON 对象。
     *
     * @param dir    日志目录
     * @param marker 探针标记
     * @return 记录 JSON 节点
     */
    private static JsonNode awaitRecord(Path dir, String marker) throws Exception {
        final Optional<String> content = awaitLogContent(dir, LOG_WAIT_TIMEOUT, marker);
        assertThat(content)
                .as("file appender must write the '%s' probe record under the isolated LOG_PATH within %s",
                        marker, LOG_WAIT_TIMEOUT)
                .isPresent();
        return parseJsonRecord(content.orElseThrow(), marker);
    }

    /**
     * 轮询目录文本直到全部标记出现。
     *
     * @param dir             日志目录
     * @param timeout         等待上限
     * @param requiredMarkers 必须出现的内容标记
     * @return 含全部标记的文本；超时为 {@link Optional#empty()}
     */
    private static Optional<String> awaitLogContent(Path dir, Duration timeout, String... requiredMarkers)
            throws InterruptedException {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final String content = readAllText(dir);
            if (Arrays.stream(requiredMarkers).allMatch(content::contains)) {
                return Optional.of(content);
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return Optional.empty();
    }

    /**
     * 读取目录下全部普通文件文本；容忍写入中的不一致（单文件读取失败跳过，由轮询重试）。
     *
     * @param root 目录
     * @return 已读取到的文本拼接
     */
    private static String readAllText(Path root) {
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
        return content.toString();
    }

    /**
     * 从目录文本快照中提取标记所在行并解析为 JSON 对象。
     *
     * @param content 目录文本快照
     * @param marker  探针标记
     * @return 记录 JSON 节点
     */
    private static JsonNode parseJsonRecord(String content, String marker) throws Exception {
        final String line = Arrays.stream(content.split("\n"))
                .filter(candidate -> candidate.contains(marker))
                .findFirst()
                .orElseThrow();
        assertThat(JacksonUtil.isObject(line))
                .as("persisted record for '%s' must be a JSON object, but was: %s", marker, line)
                .isTrue();
        return JacksonUtil.DEFAULT_MAPPER.readTree(line);
    }

    /**
     * 断言记录包含全部字段且值非 null。
     *
     * @param record 记录节点
     * @param fields 字段名
     */
    private static void assertFieldsPresent(JsonNode record, String... fields) {
        for (final String field : fields) {
            assertThat(record.hasNonNull(field))
                    .as("persisted record must carry field '%s': %s", field, record)
                    .isTrue();
        }
    }

    /**
     * 断言时间戳落在测试执行时刻的相对窗口内；无法识别的格式（非毫秒数字、非 ISO 文本）跳过值校验。
     *
     * @param record 记录节点
     */
    private static void assertTimestampWithinRelativeWindow(JsonNode record) {
        final JsonNode timestamp = record.path("timestamp");
        assertThat(timestamp.isMissingNode() || timestamp.isNull())
                .as("timestamp value must be present, but was: %s", timestamp)
                .isFalse();
        resolveInstant(timestamp).ifPresent(instant ->
                assertThat(Duration.between(instant, Instant.now()).abs())
                        .as("timestamp %s must lie within %s of test execution", instant, TIMESTAMP_WINDOW)
                        .isLessThan(TIMESTAMP_WINDOW));
    }

    /**
     * 把时间戳节点解析为时刻：毫秒量级数字按 epoch 毫秒处理，文本按 ISO-8601 处理。
     *
     * @param timestamp 时间戳节点
     * @return 解析出的时刻；无法识别时为空
     */
    private static Optional<Instant> resolveInstant(JsonNode timestamp) {
        if (timestamp.isNumber()) {
            final long epochMillis = timestamp.asLong();
            return epochMillis >= EPOCH_MILLIS_LOWER_BOUND
                    ? Optional.of(Instant.ofEpochMilli(epochMillis))
                    : Optional.empty();
        }
        if (timestamp.isTextual()) {
            return parseIsoInstant(timestamp.asText());
        }
        return Optional.empty();
    }

    /**
     * 解析 ISO-8601 时刻文本。
     *
     * @param value 文本值
     * @return 解析出的时刻；不匹配 ISO-8601 时为空
     */
    private static Optional<Instant> parseIsoInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 从 classpath 读取配置文本（UTF-8）。
     *
     * @return 配置文本
     */
    private static String readConfigText() throws Exception {
        try (InputStream in = openConfigResource()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 打开配置资源流。
     *
     * @return 配置资源输入流
     */
    private static InputStream openConfigResource() {
        final Optional<InputStream> resource = Optional.ofNullable(
                LogConfigFileAppenderUnitTest.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE));
        assertThat(resource)
                .as("classpath resource '%s' must exist", CONFIG_RESOURCE)
                .isPresent();
        return resource.orElseThrow();
    }

    /**
     * 解析配置文本为 DOM 文档。
     *
     * @param xmlText 配置文本
     * @return 配置文档
     */
    private static Document parseDocument(String xmlText) throws Exception {
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        try (InputStream in = new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8))) {
            return builder.parse(in);
        }
    }

    /**
     * 定位文件 appender：带 {@code fileName} 属性且唯一，且必须声明 name。
     *
     * @param document 配置文档
     * @return 文件 appender 元素
     */
    private static Element findFileAppender(Document document) {
        final Element appenders = requireChild(document.getDocumentElement(), "Appenders");
        final List<Element> candidates = directChildren(appenders).stream()
                .filter(element -> !element.getAttribute("fileName").isBlank())
                .toList();
        assertThat(candidates)
                .as("exactly one file appender (an appender carrying a fileName attribute) is expected")
                .hasSize(1);

        final Element fileAppender = candidates.get(0);
        assertThat(fileAppender.getAttribute("name"))
                .as("file appender must declare a name")
                .isNotBlank();
        return fileAppender;
    }

    /**
     * 取父元素下必须存在的直接子元素。
     *
     * @param parent  父元素
     * @param tagName 子元素标签名
     * @return 匹配的子元素
     */
    private static Element requireChild(Element parent, String tagName) {
        final Optional<Element> child = directChildren(parent).stream()
                .filter(element -> tagName.equals(element.getTagName()))
                .findFirst();
        assertThat(child)
                .as("element <%s> must be declared under <%s>", tagName, parent.getTagName())
                .isPresent();
        return child.orElseThrow();
    }

    /**
     * 列举元素的直接子元素（跳过文本节点）。
     *
     * @param parent 父元素
     * @return 直接子元素列表
     */
    private static List<Element> directChildren(Element parent) {
        final List<Element> children = new ArrayList<>();
        final NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node instanceof Element element) {
                children.add(element);
            }
        }
        return children;
    }
}
