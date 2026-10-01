package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.PublicShareVO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ShareCreateRequest;
import com.resumegen.dto.ShareOgVO;
import com.resumegen.dto.ShareVO;
import com.resumegen.entity.ResumeShare;
import com.resumegen.mapper.ResumeShareMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历定点分享业务。
 */
@Service
public class ShareService {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int KEY_LEN = 8;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResumeShareMapper shareMapper;
    private final ResumeService resumeService;
    private final ResumeProperties props;
    private final PasswordEncoder passwordEncoder;

    public ShareService(ResumeShareMapper shareMapper, ResumeService resumeService,
                        ResumeProperties props, PasswordEncoder passwordEncoder) {
        this.shareMapper = shareMapper;
        this.resumeService = resumeService;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
    }

    public ShareVO create(Long userId, ShareCreateRequest req) {
        // 校验简历归属（不存在/非本人则 NOT_FOUND）
        resumeService.get(userId, String.valueOf(req.getResumeId()));

        ResumeShare s = new ResumeShare();
        s.setUserId(userId);
        s.setResumeId(req.getResumeId());
        s.setShareKey(uniqueKey());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            s.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        Integer days = req.getExpireDays() == null ? props.getShare().getDefaultTtlDays() : req.getExpireDays();
        if (days != null && days > 0) {
            s.setExpireAt(LocalDateTime.now().plusDays(days));
        }
        s.setShowContact(Boolean.TRUE.equals(req.getShowContact()) ? 1 : 0);
        s.setViewCount(0L);
        s.setStatus(1);
        shareMapper.insert(s);
        return toVO(s);
    }

    public List<ShareVO> list(Long userId) {
        return shareMapper.selectList(
                        new LambdaQueryWrapper<ResumeShare>()
                                .eq(ResumeShare::getUserId, userId)
                                .orderByDesc(ResumeShare::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public void revoke(Long userId, String key) {
        ResumeShare s = shareMapper.selectOne(
                new LambdaQueryWrapper<ResumeShare>()
                        .eq(ResumeShare::getShareKey, key)
                        .eq(ResumeShare::getUserId, userId));
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        s.setStatus(0);
        shareMapper.updateById(s);
    }

    public PublicShareVO publicRead(String key, String password) {
        ResumeShare s = shareMapper.selectOne(
                new LambdaQueryWrapper<ResumeShare>().eq(ResumeShare::getShareKey, key));
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享不存在或已失效");
        }
        if (s.getExpireAt() != null && s.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享已过期");
        }
        if (s.getPassword() != null && !s.getPassword().isEmpty()) {
            if (password == null || password.isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "需要访问密码");
            }
            if (!passwordEncoder.matches(password, s.getPassword())) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "密码错误");
            }
        }
        shareMapper.incrementViewCount(s.getId());

        ResumeDetailVO resume = resumeService.get(s.getUserId(), String.valueOf(s.getResumeId()));
        boolean showContact = s.getShowContact() != null && s.getShowContact() == 1;
        if (!showContact && resume.getPersonal() != null) {
            resume.getPersonal().setEmail(null);
            resume.getPersonal().setPhone(null);
        }

        PublicShareVO vo = new PublicShareVO();
        vo.setResume(resume);
        vo.setShowContact(showContact);
        vo.setViewCount((s.getViewCount() == null ? 0 : s.getViewCount()) + 1);
        return vo;
    }

    /** 分享页 OpenGraph / 社交卡片元信息（脱敏，不含联系方式，供爬虫与 meta 注入）。 */
    public ShareOgVO publicOg(String key) {
        ResumeShare s = shareMapper.selectOne(
                new LambdaQueryWrapper<ResumeShare>().eq(ResumeShare::getShareKey, key));
        if (s == null || s.getStatus() == null || s.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享不存在或已失效");
        }
        if (s.getExpireAt() != null && s.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享已过期");
        }

        ResumeDetailVO resume = resumeService.get(s.getUserId(), String.valueOf(s.getResumeId()));
        String name = resume.getPersonal() == null ? null : resume.getPersonal().getName();
        String title = resume.getPersonal() == null ? null : resume.getPersonal().getTitle();
        if (isBlank(name)) {
            name = resume.getTitle();
        }
        if (isBlank(name)) {
            name = "简历分享";
        }

        ShareOgVO vo = new ShareOgVO();
        vo.setTitle(name + (isBlank(title) ? "" : " · " + title));
        String summary = resume.getPersonal() == null ? null : resume.getPersonal().getSummary();
        vo.setDescription(isBlank(summary) ? "查看这份在线简历" : truncate(summary, 200));
        String image = resume.getPersonal() == null ? null : resume.getPersonal().getAvatar();
        if (isBlank(image)) {
            image = props.getShare().getSeo().getOgImageDefault();
        }
        vo.setImage(image);
        String base = props.getShare().getBaseUrl();
        vo.setUrl((base == null || base.isBlank() ? "" : base) + "/#/s/" + s.getShareKey());
        vo.setSiteName("简历生成器");
        return vo;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String uniqueKey() {
        for (int i = 0; i < 5; i++) {
            String key = randomKey();
            Long c = shareMapper.selectCount(
                    new LambdaQueryWrapper<ResumeShare>().eq(ResumeShare::getShareKey, key));
            if (c == null || c == 0) {
                return key;
            }
        }
        throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "分享链接生成失败");
    }

    private String randomKey() {
        StringBuilder sb = new StringBuilder(KEY_LEN);
        for (int i = 0; i < KEY_LEN; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private ShareVO toVO(ResumeShare s) {
        ShareVO vo = new ShareVO();
        vo.setId(String.valueOf(s.getId()));
        vo.setShareKey(s.getShareKey());
        String base = props.getShare().getBaseUrl();
        if (base != null && !base.isBlank()) {
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            vo.setUrl(base + "/#/s/" + s.getShareKey());
        }
        vo.setHasPassword(s.getPassword() != null && !s.getPassword().isEmpty());
        vo.setShowContact(s.getShowContact() != null && s.getShowContact() == 1);
        vo.setExpireAt(s.getExpireAt() == null ? null : s.getExpireAt().format(FMT));
        vo.setViewCount(s.getViewCount() == null ? 0 : s.getViewCount());
        vo.setStatus(s.getStatus() == null ? 0 : s.getStatus());
        vo.setCreatedAt(s.getCreatedAt() == null ? null : s.getCreatedAt().format(FMT));
        return vo;
    }
}