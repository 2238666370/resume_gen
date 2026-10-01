package com.resumegen.rule;

import com.resumegen.config.ResumeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRuleEngineTest {

    private AuditRuleEngine engine;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties();
        props.getCommunity().setSensitiveWords(List.of("广告", "赌博"));
        engine = new AuditRuleEngine(props);
    }

    @Test
    void cleanContentPasses() {
        AuditRuleEngine.AuditResult r = engine.audit("前端工程师求职", "熟练掌握 React 与 Spring Boot", List.of("前端", "后端"));
        assertThat(r.passed()).isTrue();
        assertThat(r.reason()).isNull();
    }

    @Test
    void sensitiveWordInTitleRejected() {
        AuditRuleEngine.AuditResult r = engine.audit("便宜代发广告", null, null);
        assertThat(r.passed()).isFalse();
        assertThat(r.reason()).contains("广告");
    }

    @Test
    void sensitiveWordInSummaryRejected() {
        AuditRuleEngine.AuditResult r = engine.audit("正常标题", "这里可以参与赌博下注", null);
        assertThat(r.passed()).isFalse();
        assertThat(r.reason()).contains("赌博");
    }

    @Test
    void xssScriptRejected() {
        AuditRuleEngine.AuditResult r = engine.audit("标题", "<script>alert(1)</script>", null);
        assertThat(r.passed()).isFalse();
        assertThat(r.reason()).contains("违禁");
    }
}