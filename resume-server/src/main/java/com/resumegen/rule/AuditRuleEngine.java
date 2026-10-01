package com.resumegen.rule;

import com.resumegen.config.ResumeProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 发布审核规则引擎：敏感词 / 违禁内容（XSS）过滤，可插拔、词库可配置。
 */
@Component
public class AuditRuleEngine {

    private static final Pattern XSS = Pattern.compile(
            "(?i)(<script|</script|<iframe|<img|<svg|javascript:|vbscript:"
                    + "|on(load|error|click|mouseover|focus|blur|change|input|submit)\\s*=)");

    private static final List<String> DEFAULT_SENSITIVE_WORDS = List.of(
            "广告", "刷单", "赌博", "博彩", "色情", "诈骗", "代开发票", "办证", "套现");

    private final ResumeProperties props;

    public AuditRuleEngine(ResumeProperties props) {
        this.props = props;
    }

    /**
     * 对发布内容执行规则过滤。
     *
     * @return 审核结果（passed=true 表示上线，否则携带拒绝原因）
     */
    public AuditResult audit(String title, String summary, List<String> tags) {
        String sum = summary == null ? "" : summary;
        String tagText = tags == null ? "" : String.join(" ", tags);
        String full = title + " " + sum + " " + tagText;

        for (String word : sensitiveWords()) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (full.contains(word)) {
                return AuditResult.rejected("包含敏感词：" + word);
            }
        }
        if (XSS.matcher(full).find()) {
            return AuditResult.rejected("包含违禁内容");
        }
        return AuditResult.ok();
    }

    private List<String> sensitiveWords() {
        List<String> configured = props.getCommunity().getSensitiveWords();
        return (configured == null || configured.isEmpty()) ? DEFAULT_SENSITIVE_WORDS : configured;
    }

    /** 审核结果。 */
    public record AuditResult(boolean passed, String reason) {
        public static AuditResult ok() {
            return new AuditResult(true, null);
        }

        public static AuditResult rejected(String reason) {
            return new AuditResult(false, reason);
        }
    }
}