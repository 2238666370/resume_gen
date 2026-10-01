package com.resumegen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.dto.ResumeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiSupportTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void fillReplacesPlaceholders() {
        String out = AiSupport.fill("Hello {{name}}{{empty}}", Map.of("name", "张三", "empty", ""));
        assertThat(out).isEqualTo("Hello 张三");
    }

    @Test
    void truncateLimitsLength() {
        assertThat(AiSupport.truncate("abcdef", 3)).isEqualTo("abc");
        assertThat(AiSupport.truncate(null, 3)).isEmpty();
    }

    @Test
    void desensitizedResumeJsonStripsContact() {
        ResumeDTO dto = new ResumeDTO();
        dto.getPersonal().setName("张三");
        dto.getPersonal().setEmail("a@b.c");
        dto.getPersonal().setPhone("13800000000");
        dto.getPersonal().setWebsite("https://x.com");
        dto.getPersonal().setAvatar("data:image");

        String json = AiSupport.desensitizedResumeJson(om, dto, 100000);
        assertThat(json).contains("张三")
                .doesNotContain("a@b.c")
                .doesNotContain("13800000000")
                .doesNotContain("https://x.com");
    }

    @Test
    void parseListParsesArray() {
        List<Map> list = AiSupport.parseList(om, "[{\"a\":1}]", Map.class);
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("a", 1);
    }

    @Test
    void sha256IsStable() {
        assertThat(AiSupport.sha256("abc")).isEqualTo(AiSupport.sha256("abc")).hasSize(16);
    }

    @Test
    void estimateTokensRoughly() {
        assertThat(AiSupport.estimateTokens("aaaaaa", "bbbbbb")).isEqualTo(4);
    }
}