package com.resumegen.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDTO.*;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import com.resumegen.entity.*;
import com.resumegen.mapper.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "resume.storage.type", havingValue = "mysql")
public class MysqlResumeRepository implements ResumeRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ResumeMapper resumeMapper;
    private final ResumePersonalMapper personalMapper;
    private final ResumeEducationMapper educationMapper;
    private final ResumeExperienceMapper experienceMapper;
    private final ResumeSkillMapper skillMapper;
    private final ResumeProjectMapper projectMapper;
    private final ResumeCertificateMapper certificateMapper;
    private final ResumeLanguageMapper languageMapper;
    private final ResumeCustomSectionMapper customSectionMapper;
    private final ResumeSectionMapper sectionMapper;

    public MysqlResumeRepository(ResumeMapper resumeMapper, ResumePersonalMapper personalMapper,
                                 ResumeEducationMapper educationMapper, ResumeExperienceMapper experienceMapper,
                                 ResumeSkillMapper skillMapper, ResumeProjectMapper projectMapper,
                                 ResumeCertificateMapper certificateMapper, ResumeLanguageMapper languageMapper,
                                 ResumeCustomSectionMapper customSectionMapper, ResumeSectionMapper sectionMapper) {
        this.resumeMapper = resumeMapper;
        this.personalMapper = personalMapper;
        this.educationMapper = educationMapper;
        this.experienceMapper = experienceMapper;
        this.skillMapper = skillMapper;
        this.projectMapper = projectMapper;
        this.certificateMapper = certificateMapper;
        this.languageMapper = languageMapper;
        this.customSectionMapper = customSectionMapper;
        this.sectionMapper = sectionMapper;
    }

    @Override
    @Transactional
    public ResumeDetailVO create(Long userId, ResumeDTO dto) {
        Resume r = new Resume();
        r.setUserId(userId);
        r.setTitle(nvl(dto.getTitle(), "未命名简历"));
        r.setTemplateId(nvl(dto.getTemplateId(), "classic"));
        r.setAccentColor(nvl(dto.getAccentColor(), "#2563eb"));
        r.setVersion(0);
        resumeMapper.insert(r);

        saveDetails(userId, r.getId(), dto);
        r.setUpdatedAt(LocalDateTime.now());
        return buildDetail(r, dto);
    }

    @Override
    public ResumeDetailVO get(Long userId, String resumeId) {
        Resume r = getOwned(userId, resumeId);
        return buildDetail(r, assemble(userId, r.getId()));
    }

    @Override
    @Transactional
    public boolean update(Long userId, String resumeId, ResumeDTO dto, Integer expectedVersion) {
        Resume r = getOwned(userId, resumeId);
        if (expectedVersion != null && !expectedVersion.equals(r.getVersion())) {
            return false; // 乐观锁冲突
        }
        r.setTitle(nvl(dto.getTitle(), "未命名简历"));
        r.setTemplateId(nvl(dto.getTemplateId(), "classic"));
        r.setAccentColor(nvl(dto.getAccentColor(), "#2563eb"));
        r.setVersion(r.getVersion() + 1);
        resumeMapper.updateById(r);

        clearDetails(r.getId());
        saveDetails(userId, r.getId(), dto);
        return true;
    }

    @Override
    @Transactional
    public boolean delete(Long userId, String resumeId) {
        Resume r = getOwned(userId, resumeId);
        clearDetails(r.getId());
        resumeMapper.deleteById(r.getId());
        return true;
    }

    @Override
    public List<ResumeListItemVO> list(Long userId, long page, long size, String keyword) {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Resume::getTitle, keyword.trim());
        }
        Page<Resume> p = resumeMapper.selectPage(new Page<>(page, size), qw);
        List<ResumeListItemVO> out = new ArrayList<>();
        for (Resume r : p.getRecords()) {
            out.add(toListItem(r));
        }
        return out;
    }

    @Override
    public long count(Long userId, String keyword) {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Resume::getTitle, keyword.trim());
        }
        return resumeMapper.selectCount(qw);
    }

    @Override
    public List<ResumeListItemVO> listByIds(Long userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> longIds = new ArrayList<>();
        for (String id : ids) {
            try {
                longIds.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // 忽略非法 id
            }
        }
        if (longIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, ResumeListItemVO> byId = new LinkedHashMap<>();
        for (Resume r : resumeMapper.selectBatchIds(longIds)) {
            if (userId.equals(r.getUserId())) {
                byId.put(r.getId(), toListItem(r));
            }
        }
        List<ResumeListItemVO> out = new ArrayList<>();
        for (Long id : longIds) {
            ResumeListItemVO vo = byId.get(id);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    // ---------- 内部工具 ----------

    private Resume getOwned(Long userId, String resumeId) {
        long id;
        try {
            id = Long.parseLong(resumeId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Resume r = resumeMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return r;
    }

    private ResumeListItemVO toListItem(Resume r) {
        ResumeListItemVO vo = new ResumeListItemVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setTitle(r.getTitle());
        vo.setTemplateId(r.getTemplateId());
        vo.setUpdatedAt(r.getUpdatedAt() == null ? null : FMT.format(r.getUpdatedAt()));
        return vo;
    }

    private ResumeDetailVO buildDetail(Resume r, ResumeDTO content) {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId(String.valueOf(r.getId()));
        vo.setVersion(r.getVersion());
        vo.setUpdatedAt(r.getUpdatedAt() == null ? null : FMT.format(r.getUpdatedAt()));
        vo.setTitle(r.getTitle());
        vo.setTemplateId(r.getTemplateId());
        vo.setAccentColor(r.getAccentColor());
        if (content != null) {
            vo.setPersonal(content.getPersonal());
            vo.setEducation(content.getEducation());
            vo.setExperience(content.getExperience());
            vo.setInternship(content.getInternship());
            vo.setSkills(content.getSkills());
            vo.setProjects(content.getProjects());
            vo.setCertificates(content.getCertificates());
            vo.setLanguages(content.getLanguages());
            vo.setCustomSections(content.getCustomSections());
            vo.setSections(content.getSections());
        }
        return vo;
    }

    private ResumeDTO assemble(Long userId, Long resumeId) {
        ResumeDTO dto = new ResumeDTO();

        ResumePersonal p = personalMapper.selectOne(
                new LambdaQueryWrapper<ResumePersonal>().eq(ResumePersonal::getResumeId, resumeId));
        if (p != null) {
            Personal pd = new Personal();
            pd.setName(p.getName());
            pd.setTitle(p.getTitle());
            pd.setEmail(p.getEmail());
            pd.setPhone(p.getPhone());
            pd.setLocation(p.getLocation());
            pd.setWebsite(p.getWebsite());
            pd.setAvatar(p.getAvatar());
            pd.setSummary(p.getSummary());
            dto.setPersonal(pd);
        }

        List<EducationItem> edu = new ArrayList<>();
        for (ResumeEducation e : educationMapper.selectList(
                new LambdaQueryWrapper<ResumeEducation>().eq(ResumeEducation::getResumeId, resumeId)
                        .orderByAsc(ResumeEducation::getSortOrder))) {
            EducationItem it = new EducationItem();
            it.setId(String.valueOf(e.getId()));
            it.setSchool(e.getSchool());
            it.setDegree(e.getDegree());
            it.setMajor(e.getMajor());
            it.setStartDate(e.getStartDate());
            it.setEndDate(e.getEndDate());
            it.setGpa(e.getGpa());
            it.setDescription(e.getDescription());
            edu.add(it);
        }
        dto.setEducation(edu);

        List<ExperienceItem> exp = new ArrayList<>();
        List<ExperienceItem> intern = new ArrayList<>();
        for (ResumeExperience e : experienceMapper.selectList(
                new LambdaQueryWrapper<ResumeExperience>().eq(ResumeExperience::getResumeId, resumeId)
                        .orderByAsc(ResumeExperience::getSortOrder))) {
            ExperienceItem it = new ExperienceItem();
            it.setId(String.valueOf(e.getId()));
            it.setCompany(e.getCompany());
            it.setPosition(e.getPosition());
            it.setStartDate(e.getStartDate());
            it.setEndDate(e.getEndDate());
            it.setCurrent(Boolean.TRUE.equals(e.getCurrent()));
            it.setDescription(e.getDescription());
            if ("INTERNSHIP".equals(e.getItemType())) {
                intern.add(it);
            } else {
                exp.add(it);
            }
        }
        dto.setExperience(exp);
        dto.setInternship(intern);

        List<SkillItem> skills = new ArrayList<>();
        for (ResumeSkill e : skillMapper.selectList(
                new LambdaQueryWrapper<ResumeSkill>().eq(ResumeSkill::getResumeId, resumeId)
                        .orderByAsc(ResumeSkill::getSortOrder))) {
            SkillItem it = new SkillItem();
            it.setId(String.valueOf(e.getId()));
            it.setName(e.getName());
            it.setLevel(e.getLevel() == null ? 3 : e.getLevel());
            skills.add(it);
        }
        dto.setSkills(skills);

        List<ProjectItem> projects = new ArrayList<>();
        for (ResumeProject e : projectMapper.selectList(
                new LambdaQueryWrapper<ResumeProject>().eq(ResumeProject::getResumeId, resumeId)
                        .orderByAsc(ResumeProject::getSortOrder))) {
            ProjectItem it = new ProjectItem();
            it.setId(String.valueOf(e.getId()));
            it.setName(e.getName());
            it.setRole(e.getRole());
            it.setStartDate(e.getStartDate());
            it.setEndDate(e.getEndDate());
            it.setDescription(e.getDescription());
            it.setLink(e.getLink());
            projects.add(it);
        }
        dto.setProjects(projects);

        List<CertificateItem> certs = new ArrayList<>();
        for (ResumeCertificate e : certificateMapper.selectList(
                new LambdaQueryWrapper<ResumeCertificate>().eq(ResumeCertificate::getResumeId, resumeId)
                        .orderByAsc(ResumeCertificate::getSortOrder))) {
            CertificateItem it = new CertificateItem();
            it.setId(String.valueOf(e.getId()));
            it.setName(e.getName());
            it.setIssuer(e.getIssuer());
            it.setDate(e.getDate());
            it.setLink(e.getLink());
            certs.add(it);
        }
        dto.setCertificates(certs);

        List<LanguageItem> langs = new ArrayList<>();
        for (ResumeLanguage e : languageMapper.selectList(
                new LambdaQueryWrapper<ResumeLanguage>().eq(ResumeLanguage::getResumeId, resumeId)
                        .orderByAsc(ResumeLanguage::getSortOrder))) {
            LanguageItem it = new LanguageItem();
            it.setId(String.valueOf(e.getId()));
            it.setName(e.getName());
            it.setLevel(e.getLevel());
            langs.add(it);
        }
        dto.setLanguages(langs);

        List<CustomSection> customs = new ArrayList<>();
        for (ResumeCustomSection e : customSectionMapper.selectList(
                new LambdaQueryWrapper<ResumeCustomSection>().eq(ResumeCustomSection::getResumeId, resumeId)
                        .orderByAsc(ResumeCustomSection::getSortOrder))) {
            CustomSection it = new CustomSection();
            it.setId(String.valueOf(e.getId()));
            it.setTitle(e.getTitle());
            it.setContent(e.getContent());
            customs.add(it);
        }
        dto.setCustomSections(customs);

        List<Section> sections = new ArrayList<>();
        for (ResumeSection e : sectionMapper.selectList(
                new LambdaQueryWrapper<ResumeSection>().eq(ResumeSection::getResumeId, resumeId)
                        .orderByAsc(ResumeSection::getSortOrder))) {
            Section it = new Section();
            String customId = e.getCustomId();
            it.setType(e.getSectionType());
            it.setCustomId(customId);
            it.setVisible(Boolean.TRUE.equals(e.getVisible()));
            it.setOrder(e.getSortOrder() == null ? 0 : e.getSortOrder());
            it.setId("custom".equals(e.getSectionType()) && customId != null ? customId : e.getSectionType());
            sections.add(it);
        }
        dto.setSections(sections);

        return dto;
    }

    private void saveDetails(Long userId, Long resumeId, ResumeDTO dto) {
        if (dto.getPersonal() != null) {
            Personal ps = dto.getPersonal();
            ResumePersonal p = new ResumePersonal();
            p.setResumeId(resumeId);
            p.setUserId(userId);
            p.setName(ps.getName());
            p.setTitle(ps.getTitle());
            p.setEmail(ps.getEmail());
            p.setPhone(ps.getPhone());
            p.setLocation(ps.getLocation());
            p.setWebsite(ps.getWebsite());
            p.setAvatar(ps.getAvatar());
            p.setSummary(ps.getSummary());
            personalMapper.insert(p);
        }

        if (dto.getEducation() != null) {
            int i = 0;
            for (EducationItem it : dto.getEducation()) {
                ResumeEducation e = new ResumeEducation();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setSchool(it.getSchool());
                e.setDegree(it.getDegree());
                e.setMajor(it.getMajor());
                e.setStartDate(it.getStartDate());
                e.setEndDate(it.getEndDate());
                e.setGpa(it.getGpa());
                e.setDescription(it.getDescription());
                e.setSortOrder(i++);
                educationMapper.insert(e);
            }
        }

        if (dto.getExperience() != null) {
            int i = 0;
            for (ExperienceItem it : dto.getExperience()) {
                experienceMapper.insert(toExperience(userId, resumeId, "EXPERIENCE", it, i++));
            }
        }
        if (dto.getInternship() != null) {
            int i = 0;
            for (ExperienceItem it : dto.getInternship()) {
                experienceMapper.insert(toExperience(userId, resumeId, "INTERNSHIP", it, i++));
            }
        }

        if (dto.getSkills() != null) {
            int i = 0;
            for (SkillItem it : dto.getSkills()) {
                ResumeSkill e = new ResumeSkill();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setName(it.getName());
                e.setLevel(it.getLevel() == 0 ? 3 : it.getLevel());
                e.setSortOrder(i++);
                skillMapper.insert(e);
            }
        }

        if (dto.getProjects() != null) {
            int i = 0;
            for (ProjectItem it : dto.getProjects()) {
                ResumeProject e = new ResumeProject();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setName(it.getName());
                e.setRole(it.getRole());
                e.setStartDate(it.getStartDate());
                e.setEndDate(it.getEndDate());
                e.setDescription(it.getDescription());
                e.setLink(it.getLink());
                e.setSortOrder(i++);
                projectMapper.insert(e);
            }
        }

        if (dto.getCertificates() != null) {
            int i = 0;
            for (CertificateItem it : dto.getCertificates()) {
                ResumeCertificate e = new ResumeCertificate();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setName(it.getName());
                e.setIssuer(it.getIssuer());
                e.setDate(it.getDate());
                e.setLink(it.getLink());
                e.setSortOrder(i++);
                certificateMapper.insert(e);
            }
        }

        if (dto.getLanguages() != null) {
            int i = 0;
            for (LanguageItem it : dto.getLanguages()) {
                ResumeLanguage e = new ResumeLanguage();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setName(it.getName());
                e.setLevel(it.getLevel());
                e.setSortOrder(i++);
                languageMapper.insert(e);
            }
        }

        Map<String, String> customIdMap = new LinkedHashMap<>();
        if (dto.getCustomSections() != null) {
            int i = 0;
            for (CustomSection it : dto.getCustomSections()) {
                ResumeCustomSection e = new ResumeCustomSection();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setTitle(it.getTitle());
                e.setContent(it.getContent());
                e.setSortOrder(i++);
                customSectionMapper.insert(e);
                String fid = (it.getId() != null && !it.getId().isBlank()) ? it.getId() : String.valueOf(e.getId());
                customIdMap.put(fid, String.valueOf(e.getId()));
            }
        }

        if (dto.getSections() != null) {
            int i = 0;
            for (Section s : dto.getSections()) {
                ResumeSection e = new ResumeSection();
                e.setResumeId(resumeId);
                e.setUserId(userId);
                e.setSectionType(s.getType());
                if ("custom".equals(s.getType())) {
                    e.setCustomId(customIdMap.getOrDefault(s.getCustomId(), s.getCustomId()));
                } else {
                    e.setCustomId(s.getCustomId());
                }
                e.setVisible(s.isVisible());
                e.setSortOrder(i++);
                sectionMapper.insert(e);
            }
        }
    }

    private ResumeExperience toExperience(Long userId, Long resumeId, String type, ExperienceItem it, int order) {
        ResumeExperience e = new ResumeExperience();
        e.setResumeId(resumeId);
        e.setUserId(userId);
        e.setItemType(type);
        e.setCompany(it.getCompany());
        e.setPosition(it.getPosition());
        e.setStartDate(it.getStartDate());
        e.setEndDate(it.getEndDate());
        e.setCurrent(it.isCurrent());
        e.setDescription(it.getDescription());
        e.setSortOrder(order);
        return e;
    }

    private void clearDetails(Long resumeId) {
        personalMapper.delete(new LambdaQueryWrapper<ResumePersonal>().eq(ResumePersonal::getResumeId, resumeId));
        educationMapper.delete(new LambdaQueryWrapper<ResumeEducation>().eq(ResumeEducation::getResumeId, resumeId));
        experienceMapper.delete(new LambdaQueryWrapper<ResumeExperience>().eq(ResumeExperience::getResumeId, resumeId));
        skillMapper.delete(new LambdaQueryWrapper<ResumeSkill>().eq(ResumeSkill::getResumeId, resumeId));
        projectMapper.delete(new LambdaQueryWrapper<ResumeProject>().eq(ResumeProject::getResumeId, resumeId));
        certificateMapper.delete(new LambdaQueryWrapper<ResumeCertificate>().eq(ResumeCertificate::getResumeId, resumeId));
        languageMapper.delete(new LambdaQueryWrapper<ResumeLanguage>().eq(ResumeLanguage::getResumeId, resumeId));
        customSectionMapper.delete(new LambdaQueryWrapper<ResumeCustomSection>().eq(ResumeCustomSection::getResumeId, resumeId));
        sectionMapper.delete(new LambdaQueryWrapper<ResumeSection>().eq(ResumeSection::getResumeId, resumeId));
    }

    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}