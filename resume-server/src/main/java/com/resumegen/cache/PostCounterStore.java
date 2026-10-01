package com.resumegen.cache;

import java.util.List;
import java.util.Map;

/**
 * 社区互动计数「写扩散」抽象：写时聚合、读时直读。
 *
 * <p>redis 实现以 Redis Hash 作为读模型（最终一致，读穿透自愈）；none 实现为空操作，
 * 由 CommunityService 直接读写 {@code community_post} 计数列（强一致，零额外依赖）。
 */
public interface PostCounterStore {

    /** 可扩散的计数维度（不含浏览，浏览仍走 DB incrView + 读取时 +1）。 */
    enum Field {
        LIKE("like"),
        COLLECT("collect"),
        COMMENT("comment"),
        REPORT("report");

        private final String key;

        Field(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** 写扩散：原子增减计数（redis：HINCRBY；none：no-op，由调用方写 DB）。 */
    void incr(Long postId, Field field, long delta);

    /** 读直读：单帖子全维度计数；无缓存/缺失返回 null，调用方回退 DB 实体值。 */
    Map<Field, Long> getAll(Long postId);

    /** 读直读（批量）：键为 postId；无缓存返回 null。 */
    Map<Long, Map<Field, Long>> getAll(List<Long> postIds);

    /** 读穿透回填/自愈：以 DB 聚合值重建缓存（none：no-op）。 */
    void saveAll(Long postId, Map<Field, Long> counters);

    /** 帖子删除时清理计数缓存（none：no-op）。 */
    void delete(Long postId);
}