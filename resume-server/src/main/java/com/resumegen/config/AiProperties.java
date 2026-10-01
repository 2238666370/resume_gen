package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 能力层配置（可配置项）。默认关闭，无密钥时优雅降级。
 * 底层模型连接走 Spring AI 原生 spring.ai.openai.*（base-url/api-key/model 均可配置）。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** AI 总开关：false 时所有 /api/ai/** 接口返回明确错误，不调用 LLM。 */
    private boolean enabled = false;

    /** 供应商标识（OpenAI 兼容协议均可）：deepseek | openai | qwen。 */
    private String provider = "deepseek";

    /** 拼入 prompt 的简历/检索原文最大字符数，防止超长上下文。 */
    private int maxContextChars = 8000;

    /** 单次 LLM HTTP 调用超时秒数（超时即失败并释放线程）。 */
    private int timeoutSeconds = 60;

    /** 预规划 + ReAct 范式配置。 */
    private React react = new React();

    /** 异步双队列配置（按任务成本分级，资源差异化分配）。 */
    private Async async = new Async();

    /** 熔断配置（连续失败短路，避免雪崩）。 */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /** 单用户频控配置（按 user_id + task_type 计数）。 */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class React {

        /** 是否启用预规划 + ReAct 范式；false 时退化为单次直出。 */
        private boolean enabled = true;

        /** ReAct 最大推理轮次。 */
        private int maxIterations = 4;
    }

    @Data
    public static class Async {

        /** 是否启用异步双队列；false 时异步端点返回明确错误。 */
        private boolean enabled = false;

        /** 高成本队列（interview.generate / resume.suggest），并发度高。 */
        private QueuePool high = new QueuePool();

        /** 低成本队列（resume.rewrite / resume.expand），并发度低。 */
        private QueuePool low = new QueuePool();

        public Async() {
            high.setConcurrency(8);
            low.setConcurrency(3);
        }
    }

    @Data
    public static class QueuePool {

        /** 该队列的消费者并发数。 */
        private int concurrency = 1;
    }

    @Data
    public static class CircuitBreaker {

        /** 是否启用熔断；false 时仅超时生效、不短路。 */
        private boolean enabled = true;

        /** 连续失败次数阈值，达到即打开熔断。 */
        private int failureThreshold = 5;

        /** 熔断打开后短路时长（秒），到期转半开试探。 */
        private int openSeconds = 30;
    }

    @Data
    public static class RateLimit {

        /** 是否启用单用户频控；false 时不限流。 */
        private boolean enabled = false;

        /** 固定窗口时长（秒）。 */
        private int windowSeconds = 60;

        /** 每个窗口内的默认次数上限。 */
        private int limit = 20;

        /** 按任务类型覆盖上限（未覆盖用 limit），如 interview.generate: 3。 */
        private Map<String, Integer> perType = new HashMap<>();
    }
}