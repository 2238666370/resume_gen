package com.resumegen.search;

import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.CommunityPost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MysqlSearchServiceTest {

    private final MysqlSearchService service = new MysqlSearchService();

    @Test
    void notActive() {
        assertThat(service.active()).isFalse();
    }

    @Test
    void searchReturnsNullSoCallerFallsBackToLike() {
        assertThat(service.searchResumes(1L, "java", 1, 10)).isNull();
        assertThat(service.searchPosts("java", null, "latest", 1, 10)).isNull();
    }

    @Test
    void indexOperationsAreNoop() {
        assertThatCode(() -> {
            service.indexResume(1L, 9L, new ResumeDTO());
            service.indexPost(new CommunityPost());
            service.deleteResume(9L);
            service.deletePost(9L);
        }).doesNotThrowAnyException();
    }
}
