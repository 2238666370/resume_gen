package com.resumegen.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonResumeRepositoryTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonResumeRepository repository;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties();
        props.getStorage().setJsonPath(tempDir.toString());
        repository = new JsonResumeRepository(objectMapper, props);
    }

    private ResumeDTO fullDto() {
        ResumeDTO dto = new ResumeDTO();
        dto.setTitle("测试简历");
        dto.setTemplateId("modern");
        dto.setAccentColor("#ff0000");
        dto.getPersonal().setName("张三");
        dto.getPersonal().setEmail("zhangsan@example.com");
        dto.getPersonal().setSummary("十年经验");

        ResumeDTO.EducationItem edu = new ResumeDTO.EducationItem();
        edu.setSchool("清华大学");
        edu.setDegree("本科");
        dto.getEducation().add(edu);

        ResumeDTO.ExperienceItem exp = new ResumeDTO.ExperienceItem();
        exp.setCompany("A 公司");
        exp.setPosition("后端工程师");
        dto.getExperience().add(exp);

        ResumeDTO.ExperienceItem intern = new ResumeDTO.ExperienceItem();
        intern.setCompany("B 公司");
        intern.setPosition("实习生");
        dto.getInternship().add(intern);

        ResumeDTO.SkillItem skill = new ResumeDTO.SkillItem();
        skill.setName("Java");
        skill.setLevel(5);
        dto.getSkills().add(skill);

        ResumeDTO.ProjectItem project = new ResumeDTO.ProjectItem();
        project.setName("简历生成器");
        dto.getProjects().add(project);

        ResumeDTO.CertificateItem cert = new ResumeDTO.CertificateItem();
        cert.setName("CET-6");
        dto.getCertificates().add(cert);

        ResumeDTO.LanguageItem lang = new ResumeDTO.LanguageItem();
        lang.setName("英语");
        dto.getLanguages().add(lang);

        ResumeDTO.CustomSection custom = new ResumeDTO.CustomSection();
        custom.setTitle("自定义");
        dto.getCustomSections().add(custom);

        ResumeDTO.Section section = new ResumeDTO.Section();
        section.setId("personal");
        section.setType("personal");
        section.setVisible(true);
        section.setOrder(0);
        dto.getSections().add(section);

        return dto;
    }

    @Test
    void createAndGetRoundTrip() {
        ResumeDetailVO created = repository.create(1L, fullDto());

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getVersion()).isEqualTo(0);

        ResumeDetailVO read = repository.get(1L, created.getId());
        assertThat(read.getTitle()).isEqualTo("测试简历");
        assertThat(read.getTemplateId()).isEqualTo("modern");
        assertThat(read.getAccentColor()).isEqualTo("#ff0000");
        assertThat(read.getPersonal().getName()).isEqualTo("张三");
        assertThat(read.getEducation()).hasSize(1);
        assertThat(read.getExperience()).hasSize(1);
        assertThat(read.getInternship()).hasSize(1);
        assertThat(read.getSkills()).hasSize(1);
        assertThat(read.getProjects()).hasSize(1);
        assertThat(read.getCertificates()).hasSize(1);
        assertThat(read.getLanguages()).hasSize(1);
        assertThat(read.getCustomSections()).hasSize(1);
        assertThat(read.getSections()).hasSize(1);
    }

    @Test
    void updateIncrementsVersion() {
        ResumeDetailVO created = repository.create(1L, fullDto());
        ResumeDTO updated = fullDto();
        updated.setTitle("更新后的标题");

        boolean ok = repository.update(1L, created.getId(), updated, 0);

        assertThat(ok).isTrue();
        ResumeDetailVO read = repository.get(1L, created.getId());
        assertThat(read.getTitle()).isEqualTo("更新后的标题");
        assertThat(read.getVersion()).isEqualTo(1);
    }

    @Test
    void updateWithWrongVersionReturnsFalse() {
        ResumeDetailVO created = repository.create(1L, fullDto());
        ResumeDTO updated = fullDto();
        updated.setTitle("不应生效");

        boolean ok = repository.update(1L, created.getId(), updated, 99);

        assertThat(ok).isFalse();
        ResumeDetailVO read = repository.get(1L, created.getId());
        assertThat(read.getTitle()).isEqualTo("测试简历");
        assertThat(read.getVersion()).isEqualTo(0);
    }

    @Test
    void deleteThenGetThrowsNotFound() {
        ResumeDetailVO created = repository.create(1L, fullDto());

        assertThat(repository.delete(1L, created.getId())).isTrue();

        assertThatThrownBy(() -> repository.get(1L, created.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void listAndCountAreIsolatedPerUser() {
        repository.create(1L, fullDto());
        repository.create(1L, fullDto());
        repository.create(2L, fullDto());

        assertThat(repository.count(1L, null)).isEqualTo(2);
        assertThat(repository.count(2L, null)).isEqualTo(1);

        List<ResumeListItemVO> list = repository.list(1L, 1, 10, null);
        assertThat(list).hasSize(2);
    }

    @Test
    void crossUserAccessReturnsNotFound() {
        ResumeDetailVO created = repository.create(1L, fullDto());

        assertThatThrownBy(() -> repository.get(2L, created.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);

        assertThat(repository.delete(2L, created.getId())).isFalse();
    }

    @Test
    void emptyResumeSavesAndReadsEmptyLists() {
        ResumeDetailVO created = repository.create(1L, new ResumeDTO());

        ResumeDetailVO read = repository.get(1L, created.getId());
        assertThat(read.getTitle()).isEqualTo("未命名简历");
        assertThat(read.getEducation()).isEmpty();
        assertThat(read.getExperience()).isEmpty();
    }

    @Test
    void largeTextRoundTrip() {
        ResumeDTO dto = new ResumeDTO();
        String big = "x".repeat(10000);
        dto.getPersonal().setSummary(big);

        ResumeDetailVO created = repository.create(1L, dto);

        assertThat(repository.get(1L, created.getId()).getPersonal().getSummary()).hasSize(10000);
    }
}