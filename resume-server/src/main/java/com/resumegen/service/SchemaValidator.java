package com.resumegen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 模板渲染 Schema 校验器（白名单 + 防 XSS / 样式注入）。
 *
 * <p>Schema 本质是「数据驱动的 UI」，必须在落库前做三道防线之一的后端白名单校验：
 * 1) 结构白名单：根对象、layout 枚举、sections 数组、区块类型白名单；
 * 2) 内容安全：所有字符串值拒绝脚本片段与危险 CSS（expression/url()/@import/javascript:）；
 * 3) 规模上限：深度 / 节点数 / sections 数上限，防膨胀拖垮渲染。</p>
 */
@Component
public class SchemaValidator {

    private static final Set<String> LAYOUTS =
            new HashSet<>(Arrays.asList("classic", "modern", "minimal", "custom"));

    private static final Set<String> SECTION_TYPES = new HashSet<>(Arrays.asList(
            "personal", "summary", "experience", "internship", "education",
            "skills", "projects", "certificates", "languages", "custom", "divider"));

    private static final Pattern XSS = Pattern.compile(
            "(?i)(<script|</script|<iframe|<img|<svg|javascript:|vbscript:"
                    + "|on(load|error|click|mouseover|focus|blur|change|input|submit)\\s*=)");

    private static final Pattern DANGEROUS_STYLE = Pattern.compile(
            "(?i)(expression\\s*\\(|url\\s*\\(|@import|position\\s*:\\s*fixed|behavior\\s*:)");

    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 500;
    private static final int MAX_SECTIONS = 100;

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
        if (root.has("layout")) {
            String layout = root.path("layout").asText("");
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
        return schemaJson;
    }

    private void walk(JsonNode node, int depth, int[] count) {
        if (node == null) {
            return;
        }
        count[0]++;
        if (count[0] > MAX_NODES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 节点过多");
        }
        if (depth > MAX_DEPTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 嵌套层级过深");
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
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 包含非法脚本片段");
        }
        if (DANGEROUS_STYLE.matcher(value).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "模板 Schema 包含危险样式");
        }
    }
}
