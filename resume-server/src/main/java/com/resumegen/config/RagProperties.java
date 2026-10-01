package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索增强配置（可配置项）。默认关闭，关闭时直连 LLM 不走检索。
 */
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 是否启用检索增强。 */
    private boolean enabled = false;

    private Store store = new Store();

    /** 相似检索返回条数。 */
    private int topK = 5;

    /** 相似度阈值，低于该值不注入上下文。 */
    private double minScore = 0.65;

    @Data
    public static class Store {
        /** 向量存储后端: memory | redis | pgvector（当前实现 memory）。 */
        private String type = "memory";
    }
}