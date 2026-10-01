package com.resumegen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 模板渲染 Schema 校验器（白名单 + 防 XSS / 样式注入）。
 *
 * <p>Schema 本质是「数据驱动的 UI」，必须在落库前做白名单校验：
 * 1) 结构白名单：根对象、layout 枚举、sections/elements 数组、元素类型白名单；
 * 2) 内容安全：所有字符串值拒绝脚本片段与危险 CSS（expression/url()/@import/javascript:）；
 * 3) 规模上限：深度 / 节点数 / 板块数 / 元素数上限，防膨胀拖垮渲染；
 * 4) 画布模板（R8-A4, schemaVersion=2）额外校验：元素数值范围、绑定路径白名单、
 *    颜色格式、图片协议、成组嵌套深度。</p>
 *
 * <p>注意：画布绑定路径白名单需与前端 {@code src/types/canvasSchema.ts} 的
 * {@code ROOT_BIND_PATHS} / {@code ITEM_BIND_PATHS} 保持一致。</p>
 */
@Component
public class SchemaValidator {

    private static final Set<String> LAYOUTS =
            new HashSet<>(Arrays.asList("classic", "modern", "minimal", "custom", "canvas"));

    private static final Set<String> SECTION_TYPES = new HashSet<>(Arrays.asList(
            "personal", "summary", "experience", "internship", "education",
            "skills", "projects", "certificates", "languages", "custom", "divider"));

    private static final Pattern XSS = Pattern.compile(
            "(?i)(<script|</script|<iframe|<img|<svg|javascript:|vbscript:"
                    + "|on(load|error|click|mouseover|focus|blur|change|input|submit)\\s*=)");

    private static final Pattern DANGEROUS_STYLE = Pattern.compile(
            "(?i)(expression\\s*\\(|url\\s*\\(|@import|position\\s*:\\s*fixed|behavior\\s*:)");

    /* ───────────── 画布模板（canvas）白名单 ───────────── */

    private static final Set<String> ELEMENT_TYPES = new HashSet<>(Arrays.asList(
            "text", "field", "heading", "list", "image", "shape", "group", "pageBreak"));

    private static final Set<String> IMAGE_SOURCES = new HashSet<>(Arrays.asList("avatar", "url"));

    /** 顶层可绑定路径（与前端 ROOT_BIND_PATHS 对齐）。 */
    private static final Set<String> ROOT_BIND_PATHS = new HashSet<>(Arrays.asList(
            "personal.name", "personal.title", "personal.email", "personal.phone",
            "personal.location", "personal.website", "personal.avatar", "personal.summary",
            "education", "experience", "internship", "skills",
            "projects", "certificates", "languages", "customSections"));

    /** 列表条目内可绑定路径（与前端 ITEM_BIND_PATHS 对齐）。 */
    private static final Map<String, Set<String>> ITEM_BIND_PATHS = new HashMap<>();

    static {
        ITEM_BIND_PATHS.put("education", set("school", "degree", "major", "startDate", "endDate", "gpa", "description"));
        ITEM_BIND_PATHS.put("experience", set("company", "position", "startDate", "endDate", "current", "description"));
        ITEM_BIND_PATHS.put("internship", set("company", "position", "startDate", "endDate", "current", "description"));
        ITEM_BIND_PATHS.put("skills", set("name", "level"));
        ITEM_BIND_PATHS.put("projects", set("name", "role", "startDate", "endDate", "description", "link"));
        ITEM_BIND_PATHS.put("certificates", set("name", "issuer", "date", "link"));
        ITEM_BIND_PATHS.put("languages", set("name", "level"));
        ITEM_BIND_PATHS.put("customSections", set("title", "content"));
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static final Pattern COLOR = Pattern.compile(
            "(?i)^(#[0-9a-f]{3}|#[0-9a-f]{6}|rgb\\([^)]*\\)|rgba\\([^)]*\\)|var\\(--[a-z0-9-]+\\))$");

    private static final Pattern HTTP_URL = Pattern.compile("(?i)^https?://");

    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 3000;
    private static final int MAX_SECTIONS = 100;
    private static final int MAX_ELEMENTS = 400;
    /** 元素允许越出页面的容差（mm），便于贴边与轻微出血。 */
    private static final double PAGE_TOLERANCE = 10;
    private static final double FONT_SIZE_MIN = 6;
    private static final double FONT_SIZE_MAX = 72;
    private static final int Z_INDEX_MAX = 999;

    private final ObjectMapper om;

    public SchemaValidator(ObjectMapper om) {
        this.om = om;
    }

    /** 校验 schema JSON 字符串；非法则抛 400，合法返回原串。 */
    public String validate(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 不能为空");
        }
        JsonNode root;
        try {
            root = om.readTree(schemaJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 必须是 JSON 对象");
        }
        String layout = "";
        if (root.has("layout")) {
            layout = root.path("layout").asText("");
            if (!LAYOUTS.contains(layout)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的模板布局: " + layout);
            }
        }
        JsonNode sections = root.path("sections");
        if (sections.isArray() && sections.size() > MAX_SECTIONS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板板块数量超过上限");
        }
        // 递归校验：规模 + 内容安全
        int[] count = new int[1];
        walk(root, 0, count);
        // 画布模板（Schema v2）额外校验
        if ("canvas".equals(layout)) {
            validateCanvas(root);
        }
        return schemaJson;
    }

    /** 读取 schema 中声明的 schemaVersion（画布模板为 2）；缺省或非法返回 1。 */
    public int schemaVersionOf(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return 1;
        }
        try {
            int v = om.readTree(schemaJson).path("schemaVersion").asInt(1);
            return v <= 0 ? 1 : v;
        } catch (Exception e) {
            return 1;
        }
    }

    /* ───────────── 通用校验 ───────────── */

    private void walk(JsonNode node, int depth, int[] count) {
        if (node == null) {
            return;
        }
        count[0]++;
        if (count[0] > MAX_NODES) {
            throw bad("模板 Schema 节点过多");
        }
        if (depth > MAX_DEPTH) {
            throw bad("模板 Schema 嵌套层级过深");
        }
        if (node.isTextual()) {
            checkText(node.asText());
            return;
        }
        if (node.isObject()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                walk(it.next(), depth + 1, count);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, depth + 1, count);
            }
        }
    }

    private void checkText(String value) {
        if (XSS.matcher(value).find()) {
            throw bad("模板 Schema 包含非法脚本片段");
        }
        if (DANGEROUS_STYLE.matcher(value).find()) {
            throw bad("模板 Schema 包含危险样式");
        }
    }

    /* ───────────── 画布模板校验 ───────────── */

    private void validateCanvas(JsonNode root) {
        JsonNode elements = root.path("elements");
        if (!elements.isArray()) {
            throw bad("画布模板必须包含 elements 数组");
        }
        if (elements.size() > MAX_ELEMENTS) {
            throw bad("画布元素数量超过上限");
        }
        double pageW = root.path("page").path("width").asDouble(210);
        double pageH = root.path("page").path("height").asDouble(297);
        for (JsonNode el : elements) {
            validateElement(el, pageW, pageH, 1, null, false);
        }
    }

    /**
     * @param groupDepth      当前成组嵌套深度（顶层成组为 1，最多 2 层）
     * @param allowedBinds    当前上下文允许的绑定路径；null 表示顶层（用 ROOT_BIND_PATHS）
     * @param insideContainer 是否位于组或列表条目模板内部（内部不允许再放列表）
     */
    private void validateElement(JsonNode el, double pageW, double pageH, int groupDepth,
                                 Set<String> allowedBinds, boolean insideContainer) {
        if (!el.isObject()) {
            throw bad("画布元素必须是对象");
        }
        String type = el.path("type").asText("");
        if (!ELEMENT_TYPES.contains(type)) {
            throw bad("不支持的画布元素类型: " + type);
        }
        checkRange(el, "x", 0, pageW + PAGE_TOLERANCE, "元素横坐标 x");
        checkRange(el, "y", 0, pageH + PAGE_TOLERANCE, "元素纵坐标 y");
        checkRange(el, "w", 0, pageW + PAGE_TOLERANCE, "元素宽度 w");
        checkRange(el, "h", 0, pageH + PAGE_TOLERANCE, "元素高度 h");
        checkRange(el, "zIndex", 0, Z_INDEX_MAX, "元素层级 zIndex");
        checkStyle(el.path("style"));

        switch (type) {
            case "field":
                checkBind(el.path("bind"), allowedBinds != null ? allowedBinds : ROOT_BIND_PATHS, "字段绑定路径");
                break;
            case "list":
                if (insideContainer) {
                    throw bad("列表元素不能嵌套在组或列表内");
                }
                validateList(el, pageW, pageH);
                break;
            case "image":
                validateImage(el);
                break;
            case "shape":
                checkColor(el.get("fill"), "形状填充色");
                checkColor(el.get("stroke"), "形状描边色");
                break;
            case "group": {
                if (groupDepth > 2) {
                    throw bad("元素成组嵌套不能超过 2 层");
                }
                JsonNode children = el.path("children");
                if (!children.isArray() || children.isEmpty()) {
                    throw bad("group 元素必须包含非空的 children 数组");
                }
                for (JsonNode child : children) {
                    validateElement(child, pageW, pageH, groupDepth + 1, allowedBinds, true);
                }
                break;
            }
            default:
                break;
        }
    }

    private void validateList(JsonNode el, double pageW, double pageH) {
        String path = el.path("bind").path("path").asText("");
        Set<String> itemPaths = ITEM_BIND_PATHS.get(path);
        if (itemPaths == null) {
            throw bad("列表元素必须绑定到数组字段，当前: " + path);
        }
        JsonNode template = el.path("itemTemplate");
        if (!template.isArray() || template.isEmpty()) {
            throw bad("列表元素必须包含非空的 itemTemplate");
        }
        for (JsonNode child : template) {
            validateElement(child, pageW, pageH, 2, itemPaths, true);
        }
    }

    private void validateImage(JsonNode el) {
        String source = el.path("source").asText("");
        if (!IMAGE_SOURCES.contains(source)) {
            throw bad("图片元素仅支持头像或外链（不支持本地图片）");
        }
        JsonNode url = el.get("url");
        if (url != null && !url.isNull()) {
            if (!url.isTextual()) {
                throw bad("图片地址必须是字符串");
            }
            String value = url.asText().trim();
            if (!value.isEmpty() && !HTTP_URL.matcher(value).find()) {
                throw bad("图片地址必须是 http(s) 链接（不支持本地图片 / base64）");
            }
        }
    }

    private void checkStyle(JsonNode style) {
        if (!style.isObject()) {
            return;
        }
        JsonNode fontSize = style.get("fontSize");
        if (fontSize != null && !fontSize.isNull()) {
            if (!fontSize.isNumber()) {
                throw bad("字号 fontSize 必须是数字（单位 pt）");
            }
            double v = fontSize.asDouble();
            if (v < FONT_SIZE_MIN || v > FONT_SIZE_MAX) {
                throw bad("字号必须在 " + (int) FONT_SIZE_MIN + "~" + (int) FONT_SIZE_MAX + "pt 之间");
            }
        }
        checkColor(style.get("color"), "文字颜色");
        checkColor(style.get("background"), "背景色");
        JsonNode border = style.path("border");
        if (border.isObject()) {
            checkColor(border.get("color"), "边框颜色");
        }
    }

    private void checkColor(JsonNode node, String label) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual()) {
            throw bad(label + " 必须是颜色字符串");
        }
        String value = node.asText().trim();
        if (!value.isEmpty() && !COLOR.matcher(value).matches()) {
            throw bad(label + " 格式非法: " + value);
        }
    }

    private void checkBind(JsonNode bind, Set<String> allowed, String label) {
        if (!bind.isObject()) {
            throw bad(label + " 缺失");
        }
        String path = bind.path("path").asText("");
        if (path.isEmpty()) {
            throw bad(label + " 不能为空");
        }
        if (!allowed.contains(path)) {
            throw bad(label + " 不在允许的字段白名单内: " + path);
        }
    }

    private void checkRange(JsonNode node, String field, double min, double max, String label) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isNumber()) {
            throw bad(label + " 必须是数字");
        }
        double v = value.asDouble();
        if (Double.isNaN(v) || Double.isInfinite(v) || v < min || v > max) {
            throw bad(label + " 超出允许范围");
        }
    }

    private BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message);
    }
}
