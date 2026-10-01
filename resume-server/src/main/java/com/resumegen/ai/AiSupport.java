package com.resumegen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.dto.ResumeDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * AI 文本/JSON 处理工具（prompt 填充、脱敏、截断、哈希、列表解析）。
 */
public final class AiSupport {

    private AiSupport() {
    }

    /** 用 {{key}} 占位符填充模板。 */
    public static String fill(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            result = result.replace("{{" + e.getKey() + "}}", v);
        }
        return result;
    }

    /** 截断超长文本。 */
    public static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    /** 截取首个 JSON 片段，容忍模型输出 Markdown 代码块或前后缀说明文字。 */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = raw.trim();
        int start = indexOfFirst(s, '{', '[');
        int end = indexOfLast(s, '}', ']');
        if (start < 0 || end < 0 || end <= start) {
            return s;
        }
        return s.substring(start, end + 1);
    }

    private static int indexOfFirst(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) return ib;
        if (ib < 0) return ia;
        return Math.min(ia, ib);
    }

    private static int indexOfLast(String s, char a, char b) {
        int ia = s.lastIndexOf(a);
        int ib = s.lastIndexOf(b);
        return Math.max(ia, ib);
    }

    /** 个人信息脱敏后序列化为 JSON（LLM 调用前，去掉联系方式）。 */
    public static String desensitizedResumeJson(ObjectMapper om, ResumeDTO dto, int maxChars) {
        ResumeDTO copy = om.convertValue(dto, ResumeDTO.class);
        if (copy.getPersonal() != null) {
            copy.getPersonal().setPhone(null);
            copy.getPersonal().setEmail(null);
            copy.getPersonal().setWebsite(null);
            copy.getPersonal().setAvatar(null);
        }
        try {
            return truncate(om.writeValueAsString(copy), maxChars);
        } catch (Exception e) {
            throw new IllegalStateException("简历序列化失败", e);
        }
    }

    /** 解析 JSON 数组。 */
    public static <T> List<T> parseList(ObjectMapper om, String json, Class<T> itemType) {
        try {
            return om.readValue(json, om.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (Exception e) {
            throw new IllegalStateException("题库解析失败", e);
        }
    }

    /** SHA-256 前 16 位，作为输入指纹。 */
    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** 粗略 token 估算（中文/英文混合，按 ~3 字符/token 折中）。 */
    public static int estimateTokens(String... parts) {
        int total = 0;
        for (String p : parts) {
            total += (p == null ? 0 : p.length());
        }
        return total / 3;
    }
}