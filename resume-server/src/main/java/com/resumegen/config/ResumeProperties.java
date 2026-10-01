package com.resumegen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "resume")
public class ResumeProperties {

    private Storage storage = new Storage();
    private Cache cache = new Cache();
    private Cors cors = new Cors();
    private Share share = new Share();
    private Community community = new Community();
    private Watermark watermark = new Watermark();
    private PublicRateLimit publicRateLimit = new PublicRateLimit();

    @Data
    public static class Storage {
        /** 存储后端: mysql | json */
        private String type = "mysql";
        /** json 模式下的数据目录 */
        private String jsonPath = "./data/resumes";
        /** 若为 true，json 模式也存 MySQL 的 JSON 列 */
        private boolean jsonInMysql = false;
    }

    @Data
    public static class Cache {
        /** 缓存后端: redis | none */
        private String type = "none";
        private int ttlSeconds = 3600;
        private String prefix = "resume:";
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "";
    }

    @Data
    public static class Share {
        /** 短链域名，前端根据此拼接分享链接。 */
        private String baseUrl = "http://localhost:5173";
        /** 默认有效期（天），0=永久。 */
        private int defaultTtlDays = 30;
        /** 分享页 SEO / 社交卡片配置。 */
        private Seo seo = new Seo();
    }

    @Data
    public static class Seo {
        /** 是否启用 SEO 元信息接口（false 时不影响现有分享功能）。 */
        private boolean enabled = true;
        /** 兜底 og:image 地址。 */
        private String ogImageDefault = "";
    }

    @Data
    public static class Community {
        /** 敏感词库（可配置；为空则用内置默认词库）。 */
        private List<String> sensitiveWords = new ArrayList<>();
        /** 举报数达到该阈值触发人工复审。 */
        private int reportReviewThreshold = 5;
        /** 点赞数达到该阈值触发热帖复审（异常高热度）。 */
        private long likeReviewThreshold = 1000;
        /** 浏览数达到该阈值触发热帖复审。 */
        private long viewReviewThreshold = 10000;
    }

    @Data
    public static class Watermark {
        /** 水印总开关（false 时不叠加水印）。 */
        private boolean enabled = false;
        private Visible visible = new Visible();
        private Blind blind = new Blind();
    }

    @Data
    public static class Visible {
        /** 可视水印文案模板，占位符 {user}/{date}/{device}。 */
        private String text = "{user}_{date}";
        private double opacity = 0.08;
        /** light / medium / heavy。 */
        private String density = "medium";
    }

    @Data
    public static class Blind {
        /** 盲水印开关。 */
        private boolean enabled = false;
        /** 盲水印编码信息（user_id|device_id）。 */
        private String payload = "user_id|device_id";
    }

    @Data
    public static class PublicRateLimit {
        /** 公开接口（分享查看/社区信息流）限流开关。 */
        private boolean enabled = false;
        /** 每窗口上限。 */
        private int limit = 60;
        /** 窗口时长（秒）。 */
        private int windowSeconds = 60;
    }
}