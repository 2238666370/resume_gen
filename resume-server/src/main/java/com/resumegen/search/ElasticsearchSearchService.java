package com.resumegen.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumegen.config.SearchProperties;
import com.resumegen.dto.ResumeDTO;
import com.resumegen.entity.CommunityPost;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 检索实现（R10-O4，{@code search.engine=elasticsearch}）。
 *
 * <p>通过 HTTP REST 直连 ES（不引入版本敏感客户端依赖），使用 IK 分词（ik_max_word 建索引 /
 * ik_smart 查询）。ES 仅作检索加速层，MySQL 为权威存储：命中返回 id 后回源 MySQL 聚合；
 * ES 调用异常时返回 null，调用方自动降级 MySQL LIKE。
 */
@Component
@ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch")
public class ElasticsearchSearchService implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchService.class);

    private final SearchProperties props;
    private final ObjectMapper om;
    private final HttpClient http;

    public ElasticsearchSearchService(SearchProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getElasticsearch().getConnectTimeoutMs()))
                .build();
    }

    @PostConstruct
    void init() {
        if (props.getElasticsearch().isAutoCreateIndex()) {
            createIndexIfAbsent(resumeIndex(), resumeMapping());
            createIndexIfAbsent(postIndex(), postMapping());
        }
    }

    @Override
    public boolean active() {
        return true;
    }

    // ---------- 检索 ----------

    @Override
    public SearchHits<Long> searchResumes(Long userId, String keyword, long page, long size) {
        try {
            ObjectNode body = om.createObjectNode();
            ObjectNode bool = body.putObject("query").putObject("bool");
            ArrayNode must = bool.putArray("must");
            must.add(multiMatch(keyword, "title^3", "skills", "companies", "positions", "projects", "schools", "summary"));
            bool.putArray("filter").add(term("userId", userId));
            applyPaging(body, page, size);
            return doSearch(resumeIndex(), body);
        } catch (Exception e) {
            log.warn("ES 简历检索失败，降级 MySQL LIKE：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public SearchHits<Long> searchPosts(String keyword, String tag, String sort, long page, long size) {
        try {
            ObjectNode body = om.createObjectNode();
            ObjectNode bool = body.putObject("query").putObject("bool");
            ArrayNode must = bool.putArray("must");
            if (keyword != null && !keyword.isBlank()) {
                must.add(multiMatch(keyword, "title^3", "summary", "tags"));
            } else {
                must.addObject().putObject("match_all");
            }
            ArrayNode filter = bool.putArray("filter");
            filter.add(term("auditStatus", CommunityPost.ST_ONLINE));
            if (tag != null && !tag.isBlank()) {
                filter.add(term("tags", tag.trim()));
            }
            applyPaging(body, page, size);
            ArrayNode sortArr = body.putArray("sort");
            sortArr.addObject().putObject("hot".equals(sort) ? "likeCount" : "createdAt").put("order", "desc");
            return doSearch(postIndex(), body);
        } catch (Exception e) {
            log.warn("ES 社区检索失败，降级 MySQL LIKE：{}", e.getMessage());
            return null;
        }
    }

    private SearchHits<Long> doSearch(String index, ObjectNode body) throws Exception {
        HttpResponse<String> resp = send("POST", "/" + index + "/_search", om.writeValueAsString(body));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("ES 返回 " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode root = om.readTree(resp.body());
        long total = root.path("hits").path("total").path("value").asLong(0);
        List<Long> ids = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            String id = hit.path("_id").asText(null);
            if (id != null) {
                ids.add(Long.parseLong(id));
            }
        }
        return new SearchHits<>(ids, total);
    }

    // ---------- 索引 ----------

    @Override
    public void indexResume(Long userId, Long resumeId, ResumeDTO dto) {
        try {
            ObjectNode doc = om.createObjectNode();
            doc.put("userId", userId);
            doc.put("title", nvl(dto.getTitle()));
            ResumeDTO.Personal p = dto.getPersonal();
            doc.put("summary", p == null ? "" : nvl(p.getSummary()));
            doc.put("skills", dto.getSkills().stream().map(ResumeDTO.SkillItem::getName)
                    .filter(java.util.Objects::nonNull).collect(Collectors.joining(" ")));
            doc.put("companies", dto.getExperience().stream().map(ResumeDTO.ExperienceItem::getCompany)
                    .filter(java.util.Objects::nonNull).collect(Collectors.joining(" ")));
            doc.put("positions", dto.getExperience().stream().map(ResumeDTO.ExperienceItem::getPosition)
                    .filter(java.util.Objects::nonNull).collect(Collectors.joining(" ")));
            doc.put("projects", dto.getProjects().stream()
                    .map(pr -> nvl(pr.getName()) + " " + nvl(pr.getDescription()))
                    .collect(Collectors.joining(" ")));
            doc.put("schools", dto.getEducation().stream()
                    .map(e -> nvl(e.getSchool()) + " " + nvl(e.getMajor()))
                    .collect(Collectors.joining(" ")));
            put(resumeIndex(), String.valueOf(resumeId), om.writeValueAsString(doc));
        } catch (Exception e) {
            log.warn("ES 索引简历失败 resumeId={}：{}", resumeId, e.getMessage());
        }
    }

    @Override
    public void deleteResume(Long resumeId) {
        delete(resumeIndex(), String.valueOf(resumeId));
    }

    @Override
    public void indexPost(CommunityPost post) {
        try {
            ObjectNode doc = om.createObjectNode();
            doc.put("title", nvl(post.getTitle()));
            doc.put("summary", nvl(post.getSummary()));
            doc.put("tags", nvl(post.getTags()));
            doc.put("auditStatus", post.getAuditStatus() == null ? 0 : post.getAuditStatus());
            doc.put("likeCount", post.getLikeCount() == null ? 0 : post.getLikeCount());
            doc.put("resumeId", post.getResumeId());
            doc.put("createdAt", post.getCreatedAt() == null
                    ? java.time.LocalDateTime.now().toString() : post.getCreatedAt().toString());
            put(postIndex(), String.valueOf(post.getId()), om.writeValueAsString(doc));
        } catch (Exception e) {
            log.warn("ES 索引社区帖失败 postId={}：{}", post.getId(), e.getMessage());
        }
    }

    @Override
    public void deletePost(Long postId) {
        delete(postIndex(), String.valueOf(postId));
    }

    // ---------- 索引管理 ----------

    private void createIndexIfAbsent(String index, String mappingJson) {
        try {
            HttpResponse<String> head = send("HEAD", "/" + index, null);
            if (head.statusCode() == 200) {
                return;
            }
            HttpResponse<String> resp = send("PUT", "/" + index, mappingJson);
            if (resp.statusCode() == 200) {
                log.info("ES 索引已创建：{}", index);
            } else {
                log.warn("ES 建索引失败 {}：{} {}", index, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("ES 建索引异常 {}（ES 不可用时检索自动降级 MySQL）：{}", index, e.getMessage());
        }
    }

    private String resumeMapping() {
        return mappingJson("userId:long,title:text,summary:text,skills:text,companies:text,"
                + "positions:text,projects:text,schools:text");
    }

    private String postMapping() {
        return mappingJson("title:text,summary:text,tags:keyword,auditStatus:integer,"
                + "likeCount:long,resumeId:long,createdAt:date");
    }

    /** 生成 mapping：text 字段用 IK 分词（ik_max_word 建索引 / ik_smart 查询）。 */
    private String mappingJson(String fields) {
        ObjectNode root = om.createObjectNode();
        root.putObject("settings").put("number_of_shards", 1).put("number_of_replicas", 0);
        ObjectNode properties = root.putObject("mappings").putObject("properties");
        for (String f : fields.split(",")) {
            String[] kv = f.split(":");
            String name = kv[0];
            String type = kv[1];
            ObjectNode node = properties.putObject(name);
            node.put("type", type);
            if ("text".equals(type)) {
                node.put("analyzer", "ik_max_word");
                node.put("search_analyzer", "ik_smart");
            }
        }
        return root.toString();
    }

    // ---------- HTTP ----------

    private void put(String index, String id, String body) throws Exception {
        HttpResponse<String> resp = send("PUT", "/" + index + "/_doc/" + id, body);
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("ES 写入 " + resp.statusCode() + ": " + resp.body());
        }
    }

    private void delete(String index, String id) {
        try {
            send("DELETE", "/" + index + "/_doc/" + id, null);
        } catch (Exception e) {
            log.warn("ES 删除索引失败 {} {}：{}", index, id, e.getMessage());
        }
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofMillis(props.getElasticsearch().getReadTimeoutMs()));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
            builder.header("Content-Type", "application/json");
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String baseUrl() {
        String u = props.getElasticsearch().getUrl();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private String resumeIndex() {
        return props.getElasticsearch().getIndexPrefix() + "resume";
    }

    private String postIndex() {
        return props.getElasticsearch().getIndexPrefix() + "post";
    }

    // ---------- JSON 片段 ----------

    private ObjectNode multiMatch(String keyword, String... fields) {
        ObjectNode mm = om.createObjectNode();
        ArrayNode arr = mm.putArray("fields");
        for (String f : fields) {
            arr.add(f);
        }
        mm.put("query", nvl(keyword));
        ObjectNode wrapper = om.createObjectNode();
        wrapper.set("multi_match", mm);
        return wrapper;
    }

    private ObjectNode term(String field, Object value) {
        ObjectNode wrapper = om.createObjectNode();
        wrapper.putObject("term").set(field, om.valueToTree(value));
        return wrapper;
    }

    private void applyPaging(ObjectNode body, long page, long size) {
        body.put("from", Math.max(0, (page - 1) * size));
        body.put("size", Math.max(1, size));
        body.put("track_total_hits", true);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
