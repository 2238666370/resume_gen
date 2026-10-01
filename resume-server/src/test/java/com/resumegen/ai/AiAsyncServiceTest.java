package com.resumegen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.config.AiProperties;
import com.resumegen.dto.AiTaskSubmitVO;
import com.resumegen.dto.AiTaskVO;
import com.resumegen.dto.InterviewGenerateRequest;
import com.resumegen.dto.InterviewSetVO;
import com.resumegen.dto.ResumeAiRequest;
import com.resumegen.mq.MessageQueue;
import com.resumegen.service.InterviewService;
import com.resumegen.service.ResumeAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAsyncServiceTest {

    @Mock private MessageQueue queue;
    @Mock private InterviewService interviewService;
    @Mock private ResumeAiService resumeAiService;

    private final ObjectMapper om = new ObjectMapper();
    private AiProperties props;
    private AiAsyncService service;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.getAsync().setEnabled(true);
        service = new AiAsyncService(queue, interviewService, resumeAiService, om, props, new InMemoryAiTaskStore());
    }

    @Test
    void interviewSubmitsToHighQueue() {
        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");

        AiTaskSubmitVO vo = service.submitInterview(1L, req);

        assertThat(vo.getTaskId()).isNotBlank();
        assertThat(vo.getQueue()).isEqualTo(AiAsyncService.QUEUE_HIGH);
        verify(queue).publish(eq(AiAsyncService.QUEUE_HIGH), anyString());
    }

    @Test
    void suggestSubmitsToHighRewriteToLow() {
        service.submitSuggest(1L, new ResumeAiRequest());
        service.submitRewrite(1L, new ResumeAiRequest());
        service.submitExpand(1L, new ResumeAiRequest());

        verify(queue).publish(eq(AiAsyncService.QUEUE_HIGH), anyString());
        verify(queue, org.mockito.Mockito.times(2)).publish(eq(AiAsyncService.QUEUE_LOW), anyString());
    }

    @Test
    void submitRejectsWhenDisabled() {
        AiProperties disabled = new AiProperties();
        disabled.getAsync().setEnabled(false);
        AiAsyncService s = new AiAsyncService(queue, interviewService, resumeAiService, om, disabled, new InMemoryAiTaskStore());

        assertThatThrownBy(() -> s.submitInterview(1L, new InterviewGenerateRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void handleDispatchesAndStoresResult() throws Exception {
        InterviewSetVO result = new InterviewSetVO();
        result.setId("9");
        result.setTitle("题集");
        when(interviewService.generate(eq(1L), any(InterviewGenerateRequest.class))).thenReturn(result);

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);

        AiAsyncService.AiTaskMessage msg = new AiAsyncService.AiTaskMessage();
        msg.taskId = sub.getTaskId();
        msg.taskType = AiAsyncService.TASK_INTERVIEW;
        msg.userId = 1L;
        msg.body = om.valueToTree(req);
        service.handle(om.writeValueAsString(msg));

        AiTaskVO vo = service.get(sub.getTaskId());
        assertThat(vo.getStatus()).isEqualTo("SUCCESS");
        assertThat(vo.getResult().get("id").asText()).isEqualTo("9");
    }

    @Test
    void handleFailureStoresError() throws Exception {
        when(interviewService.generate(any(), any(InterviewGenerateRequest.class)))
                .thenThrow(new BusinessException(500, "AI 不可用"));

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);

        AiAsyncService.AiTaskMessage msg = new AiAsyncService.AiTaskMessage();
        msg.taskId = sub.getTaskId();
        msg.taskType = AiAsyncService.TASK_INTERVIEW;
        msg.userId = 1L;
        msg.body = om.valueToTree(req);
        service.handle(om.writeValueAsString(msg));

        AiTaskVO vo = service.get(sub.getTaskId());
        assertThat(vo.getStatus()).isEqualTo("FAILED");
        assertThat(vo.getError()).contains("AI 不可用");
    }

    @Test
    void getMissingTaskThrows404() {
        assertThatThrownBy(() -> service.get("no-such"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void subscribeNotifiesOnCompletion() throws Exception {
        InterviewSetVO result = new InterviewSetVO();
        result.setId("9");
        when(interviewService.generate(eq(1L), any(InterviewGenerateRequest.class))).thenReturn(result);

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);

        AtomicReference<AiTaskVO> received = new AtomicReference<>();
        assertThat(service.subscribe(sub.getTaskId(), 1L, received::set)).isFalse();

        AiAsyncService.AiTaskMessage msg = new AiAsyncService.AiTaskMessage();
        msg.taskId = sub.getTaskId();
        msg.taskType = AiAsyncService.TASK_INTERVIEW;
        msg.userId = 1L;
        msg.body = om.valueToTree(req);
        service.handle(om.writeValueAsString(msg));

        assertThat(received.get()).isNotNull();
        assertThat(received.get().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void subscribeImmediateWhenAlreadyDone() throws Exception {
        when(interviewService.generate(any(), any(InterviewGenerateRequest.class)))
                .thenReturn(new InterviewSetVO());
        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);
        AiAsyncService.AiTaskMessage msg = new AiAsyncService.AiTaskMessage();
        msg.taskId = sub.getTaskId();
        msg.taskType = AiAsyncService.TASK_INTERVIEW;
        msg.userId = 1L;
        msg.body = om.valueToTree(req);
        service.handle(om.writeValueAsString(msg));

        AtomicReference<AiTaskVO> received = new AtomicReference<>();
        assertThat(service.subscribe(sub.getTaskId(), 1L, received::set)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void subscribeRejectsWrongUser() {
        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);

        assertThatThrownBy(() -> service.subscribe(sub.getTaskId(), 2L, v -> { }))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    void handleIsIdempotentOnDuplicateDelivery() throws Exception {
        InterviewSetVO result = new InterviewSetVO();
        result.setId("9");
        when(interviewService.generate(eq(1L), any(InterviewGenerateRequest.class))).thenReturn(result);

        InterviewGenerateRequest req = new InterviewGenerateRequest();
        req.setResumeId("9");
        AiTaskSubmitVO sub = service.submitInterview(1L, req);

        AiAsyncService.AiTaskMessage msg = new AiAsyncService.AiTaskMessage();
        msg.taskId = sub.getTaskId();
        msg.taskType = AiAsyncService.TASK_INTERVIEW;
        msg.userId = 1L;
        msg.body = om.valueToTree(req);
        String payload = om.writeValueAsString(msg);

        service.handle(payload);
        service.handle(payload); // at-least-once 重复投递，应跳过重复 dispatch

        verify(interviewService, times(1)).generate(eq(1L), any(InterviewGenerateRequest.class));
    }
}