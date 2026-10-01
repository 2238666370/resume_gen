package com.resumegen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模板 Schema 校验器单测（R8-A4 P0）：覆盖画布模板的结构白名单、数值范围、
 * 绑定路径、图片协议、成组嵌套与规模上限，以及 v1 结构化模板的兼容。
 */
class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator(new ObjectMapper());

    private static final String PAGE = "{\"width\":210,\"height\":297,\"unit\":\"mm\"}";

    private static String canvas(String... elements) {
        return "{\"schemaVersion\":2,\"layout\":\"canvas\",\"page\":" + PAGE
                + ",\"elements\":[" + String.join(",", elements) + "]}";
    }

    private static String field(String id, String path) {
        return "{\"id\":\"" + id + "\",\"type\":\"field\",\"x\":20,\"y\":20,\"w\":80,\"h\":10,\"zIndex\":1,"
                + "\"bind\":{\"path\":\"" + path + "\"},\"style\":{\"fontSize\":12,\"color\":\"#111827\"}}";
    }

    private static String shapeRect(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"shape\",\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"zIndex\":1,"
                + "\"shape\":\"rect\",\"fill\":\"#2563eb\"}";
    }

    private static String group(String id, String children) {
        return "{\"id\":\"" + id + "\",\"type\":\"group\",\"x\":10,\"y\":100,\"w\":80,\"h\":20,\"zIndex\":4,"
                + "\"children\":[" + children + "]}";
    }

    private static final String EXPERIENCE_LIST =
            "{\"id\":\"list1\",\"type\":\"list\",\"x\":20,\"y\":54,\"w\":170,\"h\":30,\"zIndex\":2,"
                    + "\"bind\":{\"path\":\"experience\"},\"itemGap\":4,\"divider\":false,"
                    + "\"itemTemplate\":[" + field("t1", "position") + "," + field("t2", "company") + "]}";

    private void assertRejected(String json) {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(json));
        assertNotNull(ex.getMessage());
    }

    @Test
    void acceptsValidCanvasSchema() {
        String json = canvas(
                field("f1", "personal.name"),
                EXPERIENCE_LIST,
                "{\"id\":\"img1\",\"type\":\"image\",\"x\":150,\"y\":18,\"w\":30,\"h\":30,\"zIndex\":3,"
                        + "\"source\":\"url\",\"url\":\"https://example.com/a.png\"}",
                group("g1", shapeRect("s1")),
                "{\"id\":\"pb1\",\"type\":\"pageBreak\",\"x\":0,\"y\":150,\"w\":210,\"h\":0,\"zIndex\":5}");
        assertEquals(json, validator.validate(json));
    }

    @Test
    void acceptsTwoLevelGrouping() {
        String json = canvas(group("g1", group("g2", shapeRect("s1"))));
        assertEquals(json, validator.validate(json));
    }

    @Test
    void rejectsGroupNestingTooDeep() {
        assertRejected(canvas(group("g1", group("g2", group("g3", shapeRect("s1"))))));
    }

    @Test
    void rejectsUnknownElementType() {
        assertRejected(canvas("{\"id\":\"x\",\"type\":\"iframe\",\"x\":0,\"y\":0,\"w\":10,\"h\":10,\"zIndex\":1}"));
    }

    @Test
    void rejectsElementOutsidePage() {
        String element = "{\"id\":\"f1\",\"type\":\"field\",\"x\":300,\"y\":20,\"w\":80,\"h\":10,\"zIndex\":1,"
                + "\"bind\":{\"path\":\"personal.name\"}}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsUnknownRootBindPath() {
        assertRejected(canvas(field("f1", "personal.secret")));
    }

    @Test
    void rejectsListItemBindOutsideArrayField() {
        String list = "{\"id\":\"l\",\"type\":\"list\",\"x\":20,\"y\":54,\"w\":170,\"h\":30,\"zIndex\":1,"
                + "\"bind\":{\"path\":\"personal.name\"},\"itemTemplate\":[" + field("t1", "position") + "]}";
        assertRejected(canvas(list));
    }

    @Test
    void rejectsListItemFieldNotInArrayWhitelist() {
        String list = "{\"id\":\"l\",\"type\":\"list\",\"x\":20,\"y\":54,\"w\":170,\"h\":30,\"zIndex\":1,"
                + "\"bind\":{\"path\":\"experience\"},\"itemTemplate\":[" + field("t1", "school") + "]}";
        assertRejected(canvas(list));
    }

    @Test
    void rejectsListNestedInGroup() {
        assertRejected(canvas(group("g1", EXPERIENCE_LIST)));
    }

    @Test
    void rejectsLocalImageSource() {
        String element = "{\"id\":\"img\",\"type\":\"image\",\"x\":10,\"y\":10,\"w\":30,\"h\":30,\"zIndex\":1,"
                + "\"source\":\"data\",\"url\":\"data:image/png;base64,AAAA\"}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsBase64ImageUrl() {
        String element = "{\"id\":\"img\",\"type\":\"image\",\"x\":10,\"y\":10,\"w\":30,\"h\":30,\"zIndex\":1,"
                + "\"source\":\"url\",\"url\":\"data:image/png;base64,AAAA\"}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsFontSizeOutOfRange() {
        String element = "{\"id\":\"f\",\"type\":\"text\",\"x\":10,\"y\":10,\"w\":80,\"h\":10,\"zIndex\":1,"
                + "\"value\":\"标题\",\"style\":{\"fontSize\":200}}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsInvalidColor() {
        String element = "{\"id\":\"f\",\"type\":\"text\",\"x\":10,\"y\":10,\"w\":80,\"h\":10,\"zIndex\":1,"
                + "\"value\":\"标题\",\"style\":{\"fontSize\":12,\"color\":\"red\"}}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsScriptInjection() {
        String element = "{\"id\":\"f\",\"type\":\"text\",\"x\":10,\"y\":10,\"w\":80,\"h\":10,\"zIndex\":1,"
                + "\"value\":\"<script>alert(1)</script>\"}";
        assertRejected(canvas(element));
    }

    @Test
    void rejectsTooManyElements() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 401; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(shapeRect("s").replace("\"id\":\"s\"", "\"id\":\"s" + i + "\""));
        }
        assertRejected(canvas(sb.toString()));
    }

    @Test
    void rejectsUnknownLayout() {
        assertRejected("{\"layout\":\"sketch\",\"elements\":[]}");
    }

    @Test
    void rejectsEmptySchema() {
        assertRejected("");
    }

    @Test
    void acceptsLegacyStructuredSchema() {
        String v1 = "{\"schemaVersion\":1,\"layout\":\"custom\",\"columns\":1,"
                + "\"sections\":[{\"id\":\"s1\",\"type\":\"summary\",\"title\":\"个人简介\"}]}";
        assertEquals(v1, validator.validate(v1));
    }

    @Test
    void rejectsCanvasWithoutElements() {
        assertRejected("{\"schemaVersion\":2,\"layout\":\"canvas\",\"page\":" + PAGE + "}");
    }
}
