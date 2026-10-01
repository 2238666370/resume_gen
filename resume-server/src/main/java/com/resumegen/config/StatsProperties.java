package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 埋点/统计配置（R10-O2）。
 */
@Data
@ConfigurationProperties(prefix = "stats")
public class StatsProperties {

    private Async async = new Async();

    @Data
    public static class Async {
        /** 埋点是否异步化（true 走消息队列，false 同步直写）。 */
        private boolean enabled = true;
    }
}
