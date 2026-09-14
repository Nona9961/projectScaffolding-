package com.nona.inf.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.util.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code log_config.xml} 配置契约场景测试：文件持久化接线、JSON-OTel 布局、有界滚动与运行期路径覆盖。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：Root 引用文件 appender；文件 appender 使用 {@code JsonTemplateLayout}
 *       且事件模板为独立 classpath JSON 资源；模板声明 OTel 字段集与 MDC ctx 键映射</li>
 *   <li>Critical：滚动含时间与大小触发且归档有界；{@code LOG_PATH} 采用环境变量可覆盖形式</li>
 * </ul>
 * <p>
 * 本类只解析配置资源的结构与语义，不启动 Log4j2 运行时；真实加载与落盘行为由
 * {@link LogConfigFileAppenderUnitTest} 覆盖。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class LogConfigStructureUnitTest {

    private static final String CONFIG_RESOURCE = "log_config.xml";
    private static final String CLASS_PATH_PREFIX = "classpath:";
    private static final String LOG_PATH_ENV_PLACEHOLDER = "${env:LOG_PATH:-./logs}";
    private static final List<String> OTEL_FIELD_KEYS = List.of(
            "timestamp", "level", "logger", "thread", "message",
            "trace_id", "span_id", "trace_flags");
    private static final List<String> MDC_MAPPED_KEYS = List.of("trace_id", "span_id", "trace_flags");

    // ========== Happy path ==========

    /**
     * Root logger 引用文件 appender：日志同时写控制台与文件（文件持久化契约）。
     */
    @Test
    void shouldReferenceFileAppenderFromRootLogger() throws Exception {
        final Document document = parseConfigDocument();
        final String fileAppenderName = findFileAppender(document).getAttribute("name");
        final Element root = requireChild(requireChild(document.getDocumentElement(), "Loggers"), "Root");

        final List<String> appenderRefs = directChildren(root).stream()
                .filter(element -> "AppenderRef".equals(element.getTagName()))
                .map(element -> element.getAttribute("ref"))
                .toList();

        assertThat(appenderRefs)
                .as("Root logger must reference file appender '%s' for file persistence", fileAppenderName)
                .contains(fileAppenderName);
    }

    /**
     * 文件 appender 使用 {@code JsonTemplateLayout}，事件模板为独立 classpath JSON 资源——
     * 且文件侧不再声明文本 PatternLayout（布局唯一，避免双布局歧义）。
     */
    @Test
    void shouldUseJsonTemplateLayoutWithIndependentClasspathTemplate() throws Exception {
        final Element layout = requireFileAppenderLayout();

        assertThat(layout.getTagName())
                .as("file appender layout must be JsonTemplateLayout while the console keeps its text pattern")
                .isEqualTo("JsonTemplateLayout");

        final String eventTemplateUri = layout.getAttribute("eventTemplateUri");
        assertThat(eventTemplateUri)
                .as("the event template must be an independent classpath JSON resource")
                .startsWith(CLASS_PATH_PREFIX)
                .endsWith(".json");

        final Optional<URL> templateUrl = Optional.ofNullable(
                configClassLoader().getResource(eventTemplateUri.substring(CLASS_PATH_PREFIX.length())));
        assertThat(templateUrl)
                .as("event template resource referenced by '%s' must exist on the classpath", eventTemplateUri)
                .isPresent();
    }

    /**
     * 事件模板声明 OTel 字段集：基础字段与 trace 关联字段齐全。
     */
    @Test
    void shouldDeclareOtelFieldSetInEventTemplate() throws Exception {
        final JsonNode template = loadEventTemplate();

        for (final String field : OTEL_FIELD_KEYS) {
            assertThat(template.has(field))
                    .as("event template must declare OTel field '%s'", field)
                    .isTrue();
        }
    }

    /**
     * 事件模板把 MDC ctx 键映射为 trace 关联字段（ctx lookup 或 mdc resolver 任一形式）。
     */
    @Test
    void shouldMapMdcCtxKeysToOtelTraceFieldsInEventTemplate() throws Exception {
        final JsonNode template = loadEventTemplate();

        for (final String key : MDC_MAPPED_KEYS) {
            final String renderedValue = template.path(key).toString();
            final boolean ctxLookup = renderedValue.contains("ctx:" + key);
            final boolean mdcResolver = renderedValue.contains("mdc") && renderedValue.contains(key);

            assertThat(ctxLookup || mdcResolver)
                    .as("field '%s' must read MDC key '%s' (ctx lookup or mdc resolver), but was: %s", key, key, renderedValue)
                    .isTrue();
        }
    }

    // ========== Critical path ==========

    /**
     * 滚动策略含时间与大小触发，且归档有界：{@code DefaultRolloverStrategy} 以
     * max 数量上限或按总量 Delete 之一封顶；归档文件格式保持 gzip。
     */
    @Test
    void shouldDeclareBoundedRollingPoliciesOnFileAppender() throws Exception {
        final Element fileAppender = findFileAppender(parseConfigDocument());

        assertThat(fileAppender.getAttribute("filePattern"))
                .as("archive file pattern must keep gzip compression")
                .endsWith(".gz");

        final List<Element> policies = directChildren(requireChild(fileAppender, "Policies"));
        final List<String> policyTags = policies.stream().map(Element::getTagName).toList();
        assertThat(policyTags)
                .as("time-based rolling must be configured")
                .contains("TimeBasedTriggeringPolicy");
        assertThat(policyTags)
                .as("size-based rolling must be configured")
                .contains("SizeBasedTriggeringPolicy");

        final Element sizePolicy = policies.stream()
                .filter(element -> "SizeBasedTriggeringPolicy".equals(element.getTagName()))
                .findFirst()
                .orElseThrow();
        assertThat(sizePolicy.getAttribute("size"))
                .as("size trigger must declare a size limit")
                .isNotBlank();

        final Element rollover = requireChild(fileAppender, "DefaultRolloverStrategy");
        final String maxArchives = rollover.getAttribute("max");
        final boolean archiveCountBounded = maxArchives.matches("\\d+") && Integer.parseInt(maxArchives) > 0;
        final boolean totalSizeBounded = directChildren(rollover).stream()
                .anyMatch(element -> "Delete".equals(element.getTagName()));

        assertThat(archiveCountBounded || totalSizeBounded)
                .as("rollover must bound retained archives by max count or size-based deletion")
                .isTrue();
    }

    /**
     * {@code LOG_PATH} 采用环境变量可覆盖形式，默认值回落到 {@code ./logs}——
     * 部署可按挂载卷重定向日志目录。
     */
    @Test
    void shouldOverrideDefaultLogPathFromEnvironment() throws Exception {
        assertThat(readConfigText())
                .as("LOG_PATH must be environment-overridable in the form '%s'", LOG_PATH_ENV_PLACEHOLDER)
                .contains(LOG_PATH_ENV_PLACEHOLDER);
    }

    // ========== helpers ==========

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
     * 解析配置为 DOM 文档。
     *
     * @return 配置文档
     */
    private static Document parseConfigDocument() throws Exception {
        final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        try (InputStream in = openConfigResource()) {
            return builder.parse(in);
        }
    }

    /**
     * 打开配置资源流。
     *
     * @return 配置资源输入流
     */
    private static InputStream openConfigResource() {
        final Optional<InputStream> resource = Optional.ofNullable(
                configClassLoader().getResourceAsStream(CONFIG_RESOURCE));
        assertThat(resource)
                .as("classpath resource '%s' must exist", CONFIG_RESOURCE)
                .isPresent();
        return resource.orElseThrow();
    }

    /**
     * 读取事件模板 JSON；模板缺失时映射 resolver 形式由断言信息呈现。
     *
     * @return 事件模板根节点
     */
    private static JsonNode loadEventTemplate() throws Exception {
        final Element layout = requireFileAppenderLayout();
        final String eventTemplateUri = layout.getAttribute("eventTemplateUri");
        assertThat(eventTemplateUri)
                .as("the event template must be an independent classpath JSON resource")
                .startsWith(CLASS_PATH_PREFIX);

        final Optional<URL> templateUrl = Optional.ofNullable(
                configClassLoader().getResource(eventTemplateUri.substring(CLASS_PATH_PREFIX.length())));
        assertThat(templateUrl)
                .as("event template resource referenced by '%s' must exist on the classpath", eventTemplateUri)
                .isPresent();
        try (InputStream in = templateUrl.orElseThrow().openStream()) {
            return JacksonUtil.DEFAULT_MAPPER.readTree(in.readAllBytes());
        }
    }

    /**
     * 定位文件 appender 的布局元素：文件 appender 的直接子布局必须唯一。
     *
     * @return 文件 appender 的布局元素
     */
    private static Element requireFileAppenderLayout() throws Exception {
        final Element fileAppender = findFileAppender(parseConfigDocument());
        final List<Element> layouts = directChildren(fileAppender).stream()
                .filter(element -> element.getTagName().endsWith("Layout"))
                .toList();
        assertThat(layouts)
                .as("file appender must declare exactly one layout")
                .hasSize(1);
        return layouts.get(0);
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

    /**
     * 配置资源的 class 加载器。
     *
     * @return 加载器
     */
    private static ClassLoader configClassLoader() {
        return LogConfigStructureUnitTest.class.getClassLoader();
    }
}
