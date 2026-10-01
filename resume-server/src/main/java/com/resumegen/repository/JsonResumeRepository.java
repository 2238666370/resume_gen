package com.resumegen.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.dto.ResumeDetailVO;
import com.resumegen.dto.ResumeListItemVO;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * JSON 文件存储实现：resume.storage.type=json。
 * 整份 ResumeDTO 序列化为单个 JSON 文件，按 user_id 目录隔离。
 */
@Repository
@ConditionalOnProperty(name = "resume.storage.type", havingValue = "json")
public class JsonResumeRepository implements ResumeRepository {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final ResumeProperties props;

    public JsonResumeRepository(ObjectMapper objectMapper, ResumeProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Data
    public static class JsonDoc {
        private int version;
        private String updatedAt;
        private String title;
        private String templateId;
        private String accentColor;
        private ResumeDTO content;
    }

    private Path baseDir() {
        return Paths.get(props.getStorage().getJsonPath()).toAbsolutePath();
    }

    private Path userDir(Long userId) {
        return baseDir().resolve(String.valueOf(userId));
    }

    private Path filePath(Long userId, String resumeId) {
        return userDir(userId).resolve(resumeId + ".json");
    }

    @Override
    public ResumeDetailVO create(Long userId, ResumeDTO dto) {
        String id = UUID.randomUUID().toString();
        JsonDoc doc = new JsonDoc();
        doc.setVersion(0);
        doc.setUpdatedAt(LocalDateTime.now().format(FMT));
        doc.setTitle(nvl(dto.getTitle(), "未命名简历"));
        doc.setTemplateId(nvl(dto.getTemplateId(), "classic"));
        doc.setAccentColor(nvl(dto.getAccentColor(), "#2563eb"));
        doc.setContent(dto);
        write(userId, id, doc);
        return toDetail(id, doc);
    }

    @Override
    public ResumeDetailVO get(Long userId, String resumeId) {
        JsonDoc doc = read(userId, resumeId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toDetail(resumeId, doc);
    }

    @Override
    public boolean update(Long userId, String resumeId, ResumeDTO dto, Integer expectedVersion) {
        JsonDoc doc = read(userId, resumeId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (expectedVersion != null && !expectedVersion.equals(doc.getVersion())) {
            return false;
        }
        doc.setVersion(doc.getVersion() + 1);
        doc.setUpdatedAt(LocalDateTime.now().format(FMT));
        doc.setTitle(nvl(dto.getTitle(), "未命名简历"));
        doc.setTemplateId(nvl(dto.getTemplateId(), "classic"));
        doc.setAccentColor(nvl(dto.getAccentColor(), "#2563eb"));
        doc.setContent(dto);
        write(userId, resumeId, doc);
        return true;
    }

    @Override
    public boolean delete(Long userId, String resumeId) {
        JsonDoc doc = read(userId, resumeId);
        if (doc == null) {
            return false;
        }
        try {
            Files.deleteIfExists(filePath(userId, resumeId));
            return true;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    @Override
    public java.util.List<ResumeListItemVO> list(Long userId, long page, long size, String keyword) {
        java.util.List<ResumeListItemVO> out = new java.util.ArrayList<>();
        Path dir = userDir(userId);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        java.util.Map<String, JsonDoc> idToDoc = new java.util.LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                String id = p.getFileName().toString().replace(".json", "");
                JsonDoc doc = readDoc(p);
                if (doc != null && matches(doc, keyword)) {
                    idToDoc.put(id, doc);
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
        java.util.List<java.util.Map.Entry<String, JsonDoc>> sorted = new java.util.ArrayList<>(idToDoc.entrySet());
        sorted.sort((a, b) -> {
            String la = a.getValue().getUpdatedAt() == null ? "" : a.getValue().getUpdatedAt();
            String lb = b.getValue().getUpdatedAt() == null ? "" : b.getValue().getUpdatedAt();
            return lb.compareTo(la);
        });
        long from = (page - 1) * size;
        long to = Math.min(from + size, sorted.size());
        for (long i = from; i < to; i++) {
            java.util.Map.Entry<String, JsonDoc> entry = sorted.get((int) i);
            JsonDoc d = entry.getValue();
            ResumeListItemVO vo = new ResumeListItemVO();
            vo.setId(entry.getKey());
            vo.setTitle(d.getTitle());
            vo.setTemplateId(d.getTemplateId());
            vo.setUpdatedAt(d.getUpdatedAt());
            out.add(vo);
        }
        return out;
    }

    @Override
    public long count(Long userId, String keyword) {
        Path dir = userDir(userId);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            long n = 0;
            for (Path p : stream) {
                JsonDoc doc = readDoc(p);
                if (doc != null && matches(doc, keyword)) {
                    n++;
                }
            }
            return n;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    private boolean matches(JsonDoc doc, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String t = doc.getTitle();
        return t != null && t.contains(keyword.trim());
    }

    @Override
    public java.util.List<ResumeListItemVO> listByIds(Long userId, java.util.List<String> ids) {
        java.util.List<ResumeListItemVO> out = new java.util.ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (String id : ids) {
            JsonDoc doc = read(userId, id);
            if (doc != null) {
                ResumeListItemVO vo = new ResumeListItemVO();
                vo.setId(id);
                vo.setTitle(doc.getTitle());
                vo.setTemplateId(doc.getTemplateId());
                vo.setUpdatedAt(doc.getUpdatedAt());
                out.add(vo);
            }
        }
        return out;
    }

    private ResumeDetailVO toDetail(String resumeId, JsonDoc doc) {
        ResumeDetailVO vo = new ResumeDetailVO();
        vo.setId(resumeId);
        vo.setVersion(doc.getVersion());
        vo.setUpdatedAt(doc.getUpdatedAt());
        ResumeDTO c = doc.getContent();
        if (c != null) {
            vo.setTitle(c.getTitle() != null ? c.getTitle() : doc.getTitle());
            vo.setTemplateId(c.getTemplateId() != null ? c.getTemplateId() : doc.getTemplateId());
            vo.setAccentColor(c.getAccentColor() != null ? c.getAccentColor() : doc.getAccentColor());
            vo.setPersonal(c.getPersonal());
            vo.setEducation(c.getEducation());
            vo.setExperience(c.getExperience());
            vo.setInternship(c.getInternship());
            vo.setSkills(c.getSkills());
            vo.setProjects(c.getProjects());
            vo.setCertificates(c.getCertificates());
            vo.setLanguages(c.getLanguages());
            vo.setCustomSections(c.getCustomSections());
            vo.setSections(c.getSections());
        } else {
            vo.setTitle(doc.getTitle());
            vo.setTemplateId(doc.getTemplateId());
            vo.setAccentColor(doc.getAccentColor());
        }
        return vo;
    }

    private JsonDoc read(Long userId, String resumeId) {
        return readDoc(filePath(userId, resumeId));
    }

    private JsonDoc readDoc(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), JsonDoc.class);
        } catch (IOException e) {
            return null;
        }
    }

    private void write(Long userId, String resumeId, JsonDoc doc) {
        try {
            Files.createDirectories(userDir(userId));
            objectMapper.writeValue(filePath(userId, resumeId).toFile(), doc);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}