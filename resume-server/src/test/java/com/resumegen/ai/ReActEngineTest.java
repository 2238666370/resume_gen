package com.resumegen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.InterviewQuestionVO;
import com.resumegen.dto.ResumeRewriteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReActEngineTest {

    @Mock private AiGateway gateway;
    @Mock private AiKnowledgeService knowledge;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
    }

    private ReActEngine engine(AiProperties props) {
        return new ReActEngine(gateway, knowledge, props, om);
    }

    @Test
    void reactDisabledDegradesToSingleShot() {
        AiProperties props = new AiProperties();
        props.getReact().setEnabled(false);
        ReActEngine engine = engine(props);

        when(gateway.generateText(anyString(), anyString())).thenReturn("{\"revised\":\"直出\"}");

        JsonNode node = engine.execute("sys", "task", "writing_style");

        assertThat(node.get("revised").asText()).isEqualTo("直出");
        verify(gateway, times(1)).generateText(anyString(), anyString());
        verifyNoInteractions(knowledge);
    }

    @Test
    void reactLoopSearchesThenReturnsFinalAnswer() {
        when(gateway.generateText(anyString(), anyString()))
                .thenReturn("1. 检索写作范式\n2. 组织答案")
                .thenReturn("{\"thought\":\"需参考\",\"action\":\"KB_SEARCH\",\"action_input\":\"STAR\"}")
                .thenReturn("{\"thought\":\"已足够\",\"final_answer\":{\"original\":\"原文\",\"revised\":\"润色后\"}}");
        when(knowledge.search("writing_style", "STAR")).thenReturn("STAR 写作范式示例");

        JsonNode node = engine(new AiProperties()).execute("sys", "task", "writing_style");

        assertThat(node.get("revised").asText()).isEqualTo("润色后");
        verify(knowledge).search("writing_style", "STAR");
    }

    @Test
    void fallbackForcesFinalAnswerAfterFailedTurns() {
        when(gateway.generateText(anyString(), anyString()))
                .thenReturn("计划（非 JSON）")
                .thenReturn("解析失败 1")
                .thenReturn("解析失败 2")
                .thenReturn("解析失败 3")
                .thenReturn("解析失败 4")
                .thenReturn("{\"revised\":\"兜底\"}");

        JsonNode node = engine(new AiProperties()).execute("sys", "task", "writing_style");

        assertThat(node.get("revised").asText()).isEqualTo("兜底");
        verify(gateway, times(6)).generateText(anyString(), anyString());
    }

    @Test
    void executeObjectMapsToType() {
        AiProperties props = new AiProperties();
        props.getReact().setEnabled(false);
        when(gateway.generateText(anyString(), anyString()))
                .thenReturn("{\"original\":\"a\",\"revised\":\"b\"}");

        ResumeRewriteVO vo = engine(props).executeObject("sys", "task", "writing_style", ResumeRewriteVO.class);

        assertThat(vo.getRevised()).isEqualTo("b");
    }

    @Test
    void executeListMapsArray() {
        when(gateway.generateText(anyString(), anyString()))
                .thenReturn("计划")
                .thenReturn("{\"thought\":\"ok\",\"final_answer\":[{\"category\":\"技术原理\",\"question\":\"q\",\"answer\":\"a\",\"tips\":\"t\"}]}");

        List<InterviewQuestionVO> list = engine(new AiProperties())
                .executeList("sys", "task", "interview_q", InterviewQuestionVO.class);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCategory()).isEqualTo("技术原理");
        assertThat(list.get(0).getQuestion()).isEqualTo("q");
    }
}