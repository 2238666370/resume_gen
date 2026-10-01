package com.resumegen.search;

import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.CommunityPost;

/**
 * 检索抽象（R10-O4）：mysql（LIKE 兜底）/ elasticsearch（IK 全文）双实现，
 * 随 {@code search.engine} 切换。使用方只依赖本接口。
 *
 * <p>语义：{@link #active()} 为 false（engine=mysql）时检索返回 null，调用方回退 MySQL LIKE；
 * ES 调用异常时同样返回 null 并记录日志，保证检索不因 ES 故障而不可用。
 */
public interface SearchService {

    /** 是否由 ES 接管检索（engine=elasticsearch）。 */
    boolean active();

    /** 简历检索（有序 id）；返回 null 表示未接管，调用方回退。 */
    SearchHits<Long> searchResumes(Long userId, String keyword, long page, long size);

    /** 社区帖检索（有序 id）；返回 null 表示未接管，调用方回退。 */
    SearchHits<Long> searchPosts(String keyword, String tag, String sort, long page, long size);

    /** 索引一份简历（active=false 时 no-op）。 */
    void indexResume(Long userId, Long resumeId, ResumeDTO dto);

    /** 删除简历索引。 */
    void deleteResume(Long resumeId);

    /** 索引一条社区帖。 */
    void indexPost(CommunityPost post);

    /** 删除社区帖索引。 */
    void deletePost(Long postId);
}
