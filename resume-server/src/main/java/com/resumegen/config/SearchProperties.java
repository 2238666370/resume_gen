package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索配置（R10-O4）：mysql（LIKE，默认兜底）| elasticsearch（IK 分词全文检索）。
 * 沿袭「可开关 + 可降级」风格：elasticsearch 不可用时自动回退 MySQL LIKE。
 */
@Data
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    /** 检索引擎：mysql | elasticsearch。 */
    private String engine = "mysql";

    private Elasticsearch elasticsearch = new Elasticsearch();

    @Data
    public static class Elasticsearch {
        /** ES 节点地址。 */
        private String url = "http://localhost:9200";
        /** 索引名前缀（便于多环境隔离）。 */
        private String indexPrefix = "resume_gen_";
        /** 连接超时（毫秒）。 */
        private int connectTimeoutMs = 2000;
        /** 读超时（毫秒）。 */
        private int readTimeoutMs = 5000;
        /** 是否启动时自动建索引（mapping + IK 分词）。 */
        private boolean autoCreateIndex = true;
    }
}
