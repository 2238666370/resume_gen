package com.resumegen.ai;

/**
 * 技能定义：每个 Skill = 编码 + 内置默认 prompt 模板。
 * 模板中的 {{var}} 占位符由调用方填充；skill_prompt 知识库可热覆盖内置默认。
 */
public final class Skills {

    private Skills() {
    }

    public static final String SYSTEM_JSON_RULE =
            "你是资深技术面试官兼简历顾问。只输出合法 JSON，不要输出 Markdown 代码块、注释或任何解释文字。";

    /** 预规划阶段系统提示：先拆解任务、给出执行计划，不输出最终答案。 */
    public static final String SYSTEM_PLANNER =
            "你是一名任务规划专家。先分析任务，再用要点列出简洁、可执行的执行计划（如：先检索什么知识、再按哪些维度组织答案）。只输出计划，不要输出最终答案。";

    /** ReAct 阶段系统提示：思考-行动-观察，通过 KB_SEARCH 检索知识库后输出最终答案。 */
    public static final String SYSTEM_REACT = """
            采用「思考-行动-观察」的方式完成任务。可用工具仅一个：
            KB_SEARCH(action_input) —— 检索知识库，返回相关参考内容（出题维度/写作范式等）。

            每一轮只输出一个 JSON 对象，二选一：
            1) 需要检索知识库：{"thought":"你的思考","action":"KB_SEARCH","action_input":"检索关键词"}
            2) 可以作答：{"thought":"你的思考","final_answer": <最终结果 JSON>}

            final_answer 必须严格符合任务要求的 JSON 结构。除此之外不要输出任何文字。""";

    public static final String INTERVIEW_GEN_QUESTIONS = "interview.gen_questions";
    public static final String RESUME_REWRITE = "resume.rewrite";
    public static final String RESUME_EXPAND_STAR = "resume.expand_star";
    public static final String RESUME_SUGGEST = "resume.suggest";
    public static final String RESUME_IMPROVE = "resume.improve";
    public static final String RESUME_SCORE = "resume.score";

    /** 出题（结合 RAG 命中的高频题/考察维度），输出完整题库。 */
    public static final String DEFAULT_INTERVIEW_PROMPT = """
            请基于以下简历内容，生成面试官可能追问的问题清单。
            有目标岗位时聚焦该岗位考察点；有 JD 时优先围绕 JD 技能与职责出题。
            参考知识（可能为空）：{{context}}

            目标岗位：{{targetRole}}
            岗位 JD：{{jd}}
            简历内容：{{resume}}

            请按 JSON 数组输出，每个元素结构为 {"category":"分类","question":"问题","answer":"参考答案要点","tips":"考察重点"}。
            分类限定在：项目深挖、技术原理、行为面、场景题 四类之一。题量 6~10 题。""";

    /** 单段润色。 */
    public static final String DEFAULT_REWRITE_PROMPT = """
            请润色以下简历描述，使表达更正式、专业、量化，但不改变事实、不虚构公司/项目/技能。
            参考写作范式（可能为空）：{{context}}
            所属栏目：{{section}}

            原文：{{text}}

            请输出 JSON 对象：{"original":"原文","revised":"润色后文本"}。""";

    /** 要点 → STAR 结构化描述。 */
    public static final String DEFAULT_EXPAND_PROMPT = """
            请将以下简历要点扩写为 STAR（情境-任务-行动-结果）结构化描述，突出量化成果与个人贡献。
            不虚构经历，仅基于给定要点合理组织语言。
            参考写作范式（可能为空）：{{context}}
            所属栏目：{{section}}

            原文要点：{{text}}

            请输出 JSON 对象：{"original":"原文","expanded":"扩写后文本"}。""";

    /** 全文诊断建议（不改稿）。 */
    public static final String DEFAULT_SUGGEST_PROMPT = """
            请分析以下简历全文，输出改进建议列表。不要改写正文，只诊断问题并给出建议。
            参考写作范式（可能为空）：{{context}}

            简历全文：{{resume}}

            请输出 JSON 数组，每个元素结构为 {"section":"栏目","issue":"问题","advice":"改进建议","priority":"高|中|低"}。""";

    /** 全文改稿：输出完整改进后的简历 JSON（结构不变，仅优化措辞）。 */
    public static final String DEFAULT_IMPROVE_PROMPT = """
            请基于以下简历全文，输出一份改进后的完整简历 JSON。
            参考写作范式（可能为空）：{{context}}

            简历全文：{{resume}}

            要求：
            1) 结构与字段必须与输入完全一致（相同顶层字段、相同数组长度与顺序），仅优化各文本字段的措辞与表达；
            2) 不要新增或删除任何记录；不要改动 id、templateId、accentColor、各类日期字段与 sections；
            3) 语言精炼、专业、突出量化成果，但不虚构事实；不得省略任何字段（即使无需修改也要原样保留）。
            请输出完整简历 JSON 对象。""";

    /** 简历评分 + JD 匹配度（ATS 风格，可解释）。 */
    public static final String DEFAULT_SCORE_PROMPT = """
            请对以下简历进行 ATS 风格评分。若提供岗位 JD，则同时计算与 JD 的匹配度。
            参考写作范式（可能为空）：{{context}}

            目标岗位：{{targetRole}}
            岗位 JD：{{jd}}
            简历内容：{{resume}}

            请输出 JSON 对象，结构如下（totalScore 与各维度 score 均为 0~100 的整数，matchedPercent 为 0~100 的整数，无 JD 时 matchedPercent 填 0）：
            {
              "totalScore": 82,
              "matchedPercent": 68,
              "dimensions": [
                {"key":"completeness","name":"完整性","score":85,"comment":"缺少项目量化结果"},
                {"key":"quantification","name":"量化度","score":70,"comment":"成果多为定性描述"},
                {"key":"verbs","name":"动词质量","score":80,"comment":"动词较丰富"},
                {"key":"structure","name":"结构清晰度","score":90,"comment":"模块划分合理"},
                {"key":"format","name":"格式规范","score":88,"comment":"格式统一"}
              ],
              "skillHits": ["java","spring boot","mysql"],
              "missingSkills": ["redis","kafka"],
              "suggestions": ["在项目经历中补充 STAR 量化结果", "工作经历增加在职时长说明"]
            }
            要求：
            1) 维度固定在：completeness(完整性)/quantification(量化度)/verbs(动词质量)/structure(结构清晰度)/format(格式规范) 五项；
            2) 每个低分维度与每条缺失技能都给出 1~2 条可执行建议；
            3) 仅基于简历内容客观评判，不虚构事实；建议应可操作、可落地。""";
}