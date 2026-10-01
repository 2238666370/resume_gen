package com.resumegen.search;

import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.CommunityPost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL 检索实现（默认兜底）：不接管检索（active=false），检索返回 null 由调用方走 LIKE；
 * 索引操作 no-op。{@code search.engine=mysql}（或未配置）时生效。
 */
@Component
@ConditionalOnProperty(name = "search.engine", havingValue = "mysql", matchIfMissing = true)
public class MysqlSearchService implements SearchService {

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public SearchHits<Long> searchResumes(Long userId, String keyword, long page, long size) {
        return null;
    }

    @Override
    public SearchHits<Long> searchPosts(String keyword, String tag, String sort, long page, long size) {
        return null;
    }

    @Override
    public void indexResume(Long userId, Long resumeId, ResumeDTO dto) {
        // no-op：MySQL 引擎不做索引
    }

    @Override
    public void deleteResume(Long resumeId) {
        // no-op
    }

    @Override
    public void indexPost(CommunityPost post) {
        // no-op
    }

    @Override
    public void deletePost(Long postId) {
        // no-op
    }
}
