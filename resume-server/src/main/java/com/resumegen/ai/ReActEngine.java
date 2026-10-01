package com.resumegen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumegen.common.BusinessException;
import com.resumegen.common.ErrorCode;
import com.resumegen.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 预规划 + ReAct 执行引擎（R5/R6 共用）。
 * <p>
 * 流程：先让模型针对任务产出一份执行计划（preplanning），再以「思考-行动-观察」的
 * ReAct 循环推进——需要参考时调用 KB_SEARCH 检索知识库，拿到观察结果后继续推理，
 * 最终产出 final_answer。ai.react.enabled=false 时退化为单次直出。
 */
@Service
public class ReActEngine {

    private final AiGateway gateway;
    private final AiKnowledgeService knowledge;
    private final AiProperties props;
    private final ObjectMapper om;

    public ReActEngine(AiGateway gateway, AiKnowledgeService knowledge, AiProperties props, ObjectMapper om) {
        this.gateway = gateway;
        this.knowledge = knowledge;
        this.props = props;
        this.om = om;
    }

    /** 执行预规划 + ReAct，返回最终答案 JSON 节点。 */
    public JsonNode execute(String system, String task, String kbType) {
        if (!props.getReact().isEnabled()) {
            return requireNode(gateway.generateText(system, task));
        }

        // 1. 预规划
        String plan = gateway.generateText(system + "\n" + Skills.SYSTEM_PLANNER,
                task + "\n\n请针对以上任务，用要点列出简洁的执行计划（不要输出最终答案）。");

        // 2. ReAct 循环
        StringBuilder steps = new StringBuilder();
        int max = Math.max(1, props.getReact().getMaxIterations());
        for (int i = 0; i < max; i++) {
            String raw = gateway.generateText(system + "\n" + Skills.SYSTEM_REACT, reactPrompt(task, plan, steps));
            JsonNode turn = tryParse(raw);
            if (turn == null) {
                steps.append("\n[提示] 上一轮输出无法解析为 JSON，请严格按协议输出。\n");
                continue;
            }
            if (turn.has("final_answer") && !turn.get("final_answer").isNull()) {
                return turn.get("final_answer");
            }
            if ("KB_SEARCH".equalsIgnoreCase(turn.path("action").asText(""))) {
                String q = turn.path("action_input").asText("");
                String obs = knowledge.search(kbType, q);
                steps.append("\n[观察 ").append(i + 1).append("] KB_SEARCH(").append(q).append(") => ")
                        .append(obs == null || obs.isBlank() ? "(无相关内容)" : obs).append("\n");
            } else if (!turn.has("action")) {
                // 模型未用 wrapper 直接给出最终答案
                return turn;
            } else {
                steps.append("\n[提示] 未知工具，请使用 KB_SEARCH 或直接给出 final_answer。\n");
            }
        }

        // 3. 兜底：强制输出最终答案
        return requireNode(gateway.generateText(system, task + "\n\n请忽略此前步骤，直接输出最终答案（严格 JSON）。"));
    }

    /** 执行并解析为单个对象。 */
    public <T> T executeObject(String system, String task, String kbType, Class<T> type) {
        JsonNode node = execute(system, task, kbType);
        try {
            return om.treeToValue(node, type);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 输出解析失败：" + e.getMessage());
        }
    }

    /** 执行并解析为对象列表。 */
    public <T> List<T> executeList(String system, String task, String kbType, Class<T> itemType) {
        JsonNode node = execute(system, task, kbType);
        try {
            return om.convertValue(node, om.getTypeFactory().constructCollectionType(List.class, itemType));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 输出解析失败：" + e.getMessage());
        }
    }

    private String reactPrompt(String task, String plan, StringBuilder steps) {
        return task + "\n\n=== 执行计划 ===\n" + plan + "\n\n=== 已进行步骤 ===\n" + steps
                + "\n请按协议输出下一步 JSON（action 或 final_answer）。";
    }

    private JsonNode tryParse(String raw) {
        try {
            return om.readTree(AiSupport.extractJson(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode requireNode(String raw) {
        JsonNode n = tryParse(raw);
        if (n == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR.getCode(), "AI 未返回有效 JSON");
        }
        return n;
    }
}