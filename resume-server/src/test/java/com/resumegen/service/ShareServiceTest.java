package com.resumegen.service;

import com.resumegen.common.BusinessException;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.PublicShareVO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ShareCreateRequest;
import com.resumegen.dto.ShareOgVO;
import com.resumegen.dto.ShareVO;
import com.resumegen.entity.ResumeShare;
import com.resumegen.mapper.ResumeShareMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ResumeShareMapper shareMapper;
    @Mock
    private ResumeService resumeService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final ResumeProperties props = new ResumeProperties();
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(shareMapper, resumeService, props, passwordEncoder);
    }

    private ShareCreateRequest req(long resumeId) {
        ShareCreateRequest r = new ShareCreateRequest();
        r.setResumeId(resumeId);
        return r;
    }

    @Test
    void createGeneratesShareWithUrlAndPermanentExpiry() {
        when(resumeService.get(1L, "9")).thenReturn(new ResumeDetailVO());

        ShareCreateRequest r = req(9L);
        r.setExpireDays(0);
        ShareVO vo = shareService.create(1L, r);

        assertThat(vo.getShareKey()).hasSize(8);
        assertThat(vo.getUrl()).isEqualTo("http://localhost:5173/#/s/" + vo.getShareKey());
        assertThat(vo.isHasPassword()).isFalse();
        assertThat(vo.getExpireAt()).isNull();

        ArgumentCaptor<ResumeShare> captor = ArgumentCaptor.forClass(ResumeShare.class);
        verify(shareMapper).insert(captor.capture());
        ResumeShare saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getResumeId()).isEqualTo(9L);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getViewCount()).isEqualTo(0L);
        assertThat(saved.getExpireAt()).isNull();
    }

    @Test
    void createEncodesPasswordExpiryAndShowContact() {
        when(resumeService.get(1L, "1")).thenReturn(new ResumeDetailVO());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        ShareCreateRequest r = req(1L);
        r.setPassword("secret");
        r.setExpireDays(7);
        r.setShowContact(true);

        ShareVO vo = shareService.create(1L, r);

        assertThat(vo.isHasPassword()).isTrue();
        assertThat(vo.isShowContact()).isTrue();
        assertThat(vo.getExpireAt()).isNotNull();
        verify(passwordEncoder).encode("secret");

        ArgumentCaptor<ResumeShare> captor = ArgumentCaptor.forClass(ResumeShare.class);
        verify(shareMapper).insert(captor.capture());
        ResumeShare saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("hashed");
        assertThat(saved.getShowContact()).isEqualTo(1);
        assertThat(saved.getExpireAt()).isNotNull();
    }

    @Test
    void createValidatesResumeOwnership() {
        when(resumeService.get(1L, "99")).thenThrow(new BusinessException(com.resumegen.common.ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> shareService.create(1L, req(99L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
        verify(shareMapper, never()).insert(any());
    }

    @Test
    void listReturnsUserShares() {
        ResumeShare s = new ResumeShare();
        s.setId(1L);
        s.setShareKey("abc12345");
        s.setViewCount(5L);
        s.setStatus(1);
        s.setShowContact(0);
        when(shareMapper.selectList(any())).thenReturn(List.of(s));

        List<ShareVO> list = shareService.list(1L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getShareKey()).isEqualTo("abc12345");
        assertThat(list.get(0).getViewCount()).isEqualTo(5L);
    }

    @Test
    void revokeSetsStatusZero() {
        ResumeShare s = new ResumeShare();
        s.setId(1L);
        s.setShareKey("abc12345");
        s.setStatus(1);
        when(shareMapper.selectOne(any())).thenReturn(s);

        shareService.revoke(1L, "abc12345");

        verify(shareMapper).updateById(s);
        assertThat(s.getStatus()).isEqualTo(0);
    }

    @Test
    void revokeUnknownKeyThrowsNotFound() {
        when(shareMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> shareService.revoke(1L, "bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    private ResumeShare share(long id, long userId, long resumeId, Integer showContact) {
        ResumeShare s = new ResumeShare();
        s.setId(id);
        s.setUserId(userId);
        s.setResumeId(resumeId);
        s.setShareKey("abc12345");
        s.setShowContact(showContact);
        s.setViewCount(10L);
        s.setStatus(1);
        return s;
    }

    private ResumeDetailVO resumeWithContact() {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId("3");
        vo.getPersonal().setName("张三");
        vo.getPersonal().setEmail("a@b.c");
        vo.getPersonal().setPhone("13800000000");
        return vo;
    }

    @Test
    void publicReadDesensitizesContactByDefault() {
        when(shareMapper.selectOne(any())).thenReturn(share(1L, 2L, 3L, 0));
        when(resumeService.get(2L, "3")).thenReturn(resumeWithContact());

        PublicShareVO vo = shareService.publicRead("abc12345", null);

        assertThat(vo.isShowContact()).isFalse();
        assertThat(vo.getResume().getPersonal().getName()).isEqualTo("张三");
        assertThat(vo.getResume().getPersonal().getEmail()).isNull();
        assertThat(vo.getResume().getPersonal().getPhone()).isNull();
        assertThat(vo.getViewCount()).isEqualTo(11L);
        verify(shareMapper).incrementViewCount(1L);
    }

    @Test
    void publicReadKeepsContactWhenEnabled() {
        when(shareMapper.selectOne(any())).thenReturn(share(1L, 2L, 3L, 1));
        when(resumeService.get(2L, "3")).thenReturn(resumeWithContact());

        PublicShareVO vo = shareService.publicRead("abc12345", null);

        assertThat(vo.isShowContact()).isTrue();
        assertThat(vo.getResume().getPersonal().getEmail()).isEqualTo("a@b.c");
        assertThat(vo.getResume().getPersonal().getPhone()).isEqualTo("13800000000");
    }

    @Test
    void publicReadInvalidOrRevokedThrowsNotFound() {
        when(shareMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> shareService.publicRead("bad", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void publicReadExpiredThrowsNotFound() {
        ResumeShare s = share(1L, 2L, 3L, 0);
        s.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(shareMapper.selectOne(any())).thenReturn(s);

        assertThatThrownBy(() -> shareService.publicRead("abc12345", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
        verify(shareMapper, never()).incrementViewCount(any());
    }

    @Test
    void publicReadRequiresPasswordWhenMissing() {
        ResumeShare s = share(1L, 2L, 3L, 0);
        s.setPassword("hashed");
        when(shareMapper.selectOne(any())).thenReturn(s);

        assertThatThrownBy(() -> shareService.publicRead("abc12345", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(shareMapper, never()).incrementViewCount(any());
    }

    @Test
    void publicReadWrongPasswordForbidden() {
        ResumeShare s = share(1L, 2L, 3L, 0);
        s.setPassword("hashed");
        when(shareMapper.selectOne(any())).thenReturn(s);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> shareService.publicRead("abc12345", "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(shareMapper, never()).incrementViewCount(any());
    }

    @Test
    void publicReadCorrectPasswordSucceeds() {
        ResumeShare s = share(1L, 2L, 3L, 0);
        s.setPassword("hashed");
        when(shareMapper.selectOne(any())).thenReturn(s);
        when(passwordEncoder.matches("right", "hashed")).thenReturn(true);
        when(resumeService.get(2L, "3")).thenReturn(resumeWithContact());

        PublicShareVO vo = shareService.publicRead("abc12345", "right");

        assertThat(vo.getResume().getId()).isEqualTo("3");
        verify(shareMapper).incrementViewCount(1L);
    }

    @Test
    void publicOgReturnsDesensitizedMetadata() {
        when(shareMapper.selectOne(any())).thenReturn(share(1L, 2L, 3L, 0));
        ResumeDetailVO resume = resumeWithContact();
        resume.getPersonal().setSummary("资深后端工程师");
        when(resumeService.get(2L, "3")).thenReturn(resume);

        ShareOgVO og = shareService.publicOg("abc12345");

        assertThat(og.getTitle()).isEqualTo("张三");
        assertThat(og.getDescription()).isEqualTo("资深后端工程师");
        assertThat(og.getUrl()).isEqualTo("http://localhost:5173/#/s/abc12345");
        assertThat(og.getSiteName()).isEqualTo("简历生成器");
        verify(shareMapper, never()).incrementViewCount(any());
    }

    @Test
    void publicOgInvalidKeyThrowsNotFound() {
        when(shareMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> shareService.publicOg("bad"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }
}