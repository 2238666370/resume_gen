package com.resumegen.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.config.RagProperties;
import com.resumegen.entity.AiKbEntry;
import com.resumegen.mapper.AiKbEntryMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 知识服务：RAG 检索（向量相似检索）+ Skill prompt 模板查询。
 * rag.enabled=false 时检索短路返回空，直连 LLM。
 */
@Service
public class AiKnowledgeService {

    private final RagProperties ragProps;
    private final AiKbEntryMapper kbMapper;
    private final ObjectProvider<EmbeddingModel> embeddingProvider;

    private volatile SimpleVectorStore store;

    public AiKnowledgeService(RagProperties ragProps, AiKbEntryMapper kbMapper,
                              ObjectProvider<EmbeddingModel> embeddingProvider) {
        this.ragProps = ragProps;
        this.kbMapper = kbMapper;
        this.embeddingProvider = embeddingProvider;
    }

    /** 检索指定类型知识库的相似条目，拼接为上下文文本。 */
    public String retrieve(String kbType, String query) {
        if (!ragProps.isEnabled()) {
            return "";
        }
        EmbeddingModel embeddingModel = embeddingProvider.getIfAvailable();
        if (embeddingModel == null) {
            return "";
        }
        ensureIndexed(embeddingModel);
        if (store == null) {
            return "";
        }
        List<Document> docs = store.similaritySearch(SearchRequest.query(query)
                .withTopK(ragProps.getTopK())
                .withSimilarityThreshold(ragProps.getMinScore()));
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            if (kbType.equals(d.getMetadata().get("kbType"))) {
                sb.append(d.getContent()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** 查询 skill 的 prompt 模板（支持热更新），查不到用内置默认。 */
    public String skillPrompt(String code, String fallback) {
        AiKbEntry e = kbMapper.selectOne(new LambdaQueryWrapper<AiKbEntry>()
                .eq(AiKbEntry::getKbType, "skill_prompt")
                .eq(AiKbEntry::getCode, code)
                .eq(AiKbEntry::getStatus, 1)
                .last("LIMIT 1"));
        if (e != null && e.getContent() != null && !e.getContent().isBlank()) {
            return e.getContent();
        }
        return fallback;
    }

    /** 检索指定类型的全部启用条目（fallback：rag 关闭时仍可直读知识库做 few-shot）。 */
    public String collect(String kbType, int limit) {
        List<AiKbEntry> list = kbMapper.selectList(new LambdaQueryWrapper<AiKbEntry>()
                .eq(AiKbEntry::getKbType, kbType)
                .eq(AiKbEntry::getStatus, 1)
                .last("LIMIT " + Math.max(1, limit)));
        StringBuilder sb = new StringBuilder();
        for (AiKbEntry e : list) {
            sb.append(e.getContent()).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 检索知识库（ReAct 的 KB_SEARCH 工具）：优先向量相似检索，rag 关闭或无命中时回退直读 few-shot。
     */
    public String search(String kbType, String query) {
        String hit = retrieve(kbType, query);
        if (hit == null || hit.isBlank()) {
            hit = collect(kbType, ragProps.getTopK());
        }
        return hit == null ? "" : hit;
    }

    /** 知识库变更后调用，使缓存索引失效，下一次检索时重建。 */
    public void invalidate() {
        store = null;
    }

    private synchronized void ensureIndexed(EmbeddingModel embeddingModel) {
        if (store != null) {
            return;
        }
        SimpleVectorStore vs = SimpleVectorStore.builder(embeddingModel).build();
        List<AiKbEntry> all = kbMapper.selectList(new LambdaQueryWrapper<AiKbEntry>()
                .eq(AiKbEntry::getStatus, 1));
        List<Document> documents = new ArrayList<>();
        for (AiKbEntry e : all) {
            documents.add(new Document(e.getContent(),
                    Map.of("kbType", e.getKbType(), "id", (Object) e.getId())));
        }
        if (!documents.isEmpty()) {
            vs.add(documents);
        }
        store = vs;
    }
}