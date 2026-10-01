package com.resumegen.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI 网关：封装 Spring AI ChatClient 的一次性调用与结构化输出（容错解析）。
 * ai.enabled=false 或未配置密钥时抛明确错误降级。
 */
@Service
public class AiGateway {

    /** 执行阻塞 LLM HTTP 调用的线程池（守护线程，超时后不阻塞主调用线程）。 */
    private static final ExecutorService CALL_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-http-call");
        t.setDaemon(true);
        return t;
    });

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiCircuitBreaker circuitBreaker;

    private volatile ChatClient client;

    public AiGateway(AiProperties props, ObjectMapper objectMapper,
                     ObjectProvider<ChatModel> chatModelProvider, AiCircuitBreaker circuitBreaker) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.chatModelProvider = chatModelProvider;
        this.circuitBreaker = circuitBreaker;
    }

    /** 是否可用（开关开启且模型已配置）。 */
    public boolean isAvailable() {
        return props.isEnabled() && chatModelProvider.getIfAvailable() != null;
    }

    /** 生成结构化对象。 */
    public <T> T generate(String system, String user, Class<T> type) {
        String raw = raw(system, user);
        try {
            return objectMapper.readValue(AiSupport.extractJson(raw), type);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 输出解析失败：" + e.getMessage());
        }
    }

    /** 生成结构化对象列表。 */
    public <T> List<T> generateList(String system, String user, Class<T> itemType) {
        String raw = raw(system, user);
        try {
            return objectMapper.readValue(AiSupport.extractJson(raw),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 输出解析失败：" + e.getMessage());
        }
    }

    /** 生成纯文本（备用）。 */
    public String generateText(String system, String user) {
        return raw(system, user);
    }

    private String raw(String system, String user) {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(),
                    "AI 功能未启用或未配置 API Key（ai.enabled / spring.ai.openai.api-key）");
        }
        if (!circuitBreaker.allowRequest()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 服务繁忙，请稍后重试");
        }
        Future<String> future = CALL_POOL.submit(
                () -> client().prompt().system(system).user(user).call().content());
        try {
            String result = future.get(props.getTimeoutSeconds(), TimeUnit.SECONDS);
            circuitBreaker.recordSuccess();
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            circuitBreaker.recordFailure();
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 调用超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            circuitBreaker.recordFailure();
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 调用被中断");
        } catch (Exception e) {
            future.cancel(true);
            circuitBreaker.recordFailure();
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 调用失败：" + rootMessage(e));
        }
    }

    private ChatClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = ChatClient.builder(chatModelProvider.getObject()).build();
                }
            }
        }
        return client;
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}