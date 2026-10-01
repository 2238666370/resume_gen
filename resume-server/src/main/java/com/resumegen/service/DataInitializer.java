package com.resumegen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.resumegen.entity.AiKbEntry;
import com.resumegen.entity.SysUser;
import com.resumegen.entity.Template;
import com.resumegen.mapper.AiKbEntryMapper;
import com.resumegen.mapper.SysUserMapper;
import com.resumegen.mapper.TemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化默认管理员账号与内置模板（已存在则跳过 / 补齐）。
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final TemplateMapper templateMapper;
    private final AiKbEntryMapper kbMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper, TemplateMapper templateMapper,
                           AiKbEntryMapper kbMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.templateMapper = templateMapper;
        this.kbMapper = kbMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initAdmin();
        initTemplates();
        initKbEntries();
    }

    private void initAdmin() {
        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        admin.setTokenVersion(0L);
        userMapper.insert(admin);
        log.warn("已初始化默认管理员账号 admin / admin123（请尽快修改密码）");
    }

    private void initTemplates() {
        seed("classic", "经典", "传统横版", 1, "{\"schemaVersion\":1,\"layout\":\"classic\"}");
        seed("modern", "现代", "侧边栏布局", 2, "{\"schemaVersion\":1,\"layout\":\"modern\"}");
        seed("minimal", "简约", "极简双栏", 3, "{\"schemaVersion\":1,\"layout\":\"minimal\"}");
        // 回填：既有官方模板若 schema 为空，补齐 schema（幂等）
        backfillSchema("classic", "{\"schemaVersion\":1,\"layout\":\"classic\"}");
        backfillSchema("modern", "{\"schemaVersion\":1,\"layout\":\"modern\"}");
        backfillSchema("minimal", "{\"schemaVersion\":1,\"layout\":\"minimal\"}");
    }

    private void backfillSchema(String code, String schema) {
        Template t = templateMapper.selectOne(
                new LambdaQueryWrapper<Template>().eq(Template::getCode, code));
        if (t != null && (t.getSchema() == null || t.getSchema().isBlank())) {
            t.setSchema(schema);
            t.setSchemaVersion(1);
            templateMapper.updateById(t);
        }
    }

    /** 内置模板缺失则补齐（幂等，避免覆盖用户定制字段）。 */
    private void seed(String code, String name, String category, int sortOrder, String schema) {
        Long c = templateMapper.selectCount(
                new LambdaQueryWrapper<Template>().eq(Template::getCode, code));
        if (c != null && c > 0) {
            return;
        }
        Template t = new Template();
        t.setCode(code);
        t.setName(name);
        t.setType("official");
        t.setCategory(category);
        t.setSchema(schema);
        t.setVersion(0);
        t.setSchemaVersion(1);
        t.setUseCount(0L);
        t.setViewCount(0L);
        t.setSortOrder(sortOrder);
        t.setStatus(1);
        templateMapper.insert(t);
        log.info("已内置模板 {} ({})", code, name);
    }

    /** 内置 AI 知识库种子（RAG few-shot 与写作范式，缺失则补齐）。 */
    private void initKbEntries() {
        seedKb("interview_q", "q_behavior", "高频行为面题",
                "请举一个你主导过的最有挑战性的项目，说明背景、你的角色、关键行动与可量化结果。\n"
                        + "描述一次与团队发生分歧的情况，你是如何推动达成一致的。");
        seedKb("interview_q", "q_tech", "技术原理高频题",
                "讲清一个你常用技术栈的底层原理（如 JVM 内存模型、索引结构、消息一致性），并结合项目说明取舍。");
        seedKb("writing_style", "w_star", "STAR 写作范式",
                "用 STAR（情境-任务-行动-结果）组织经历：先交代背景与目标，再写你负责的动作，最后给出可量化的成果（指标、百分比、规模）。");
        seedKb("writing_style", "w_verbs", "量化动词库",
                "优先使用强动词：主导、设计、搭建、优化、重构、落地、推动；量化用：提升 X%、降低 Y ms、支撑 N 万用户、节省 Z 人天。");
    }

    /** 内置知识条目缺失则补齐（幂等，按 kb_type+code 去重）。 */
    private void seedKb(String kbType, String code, String title, String content) {
        Long c = kbMapper.selectCount(new LambdaQueryWrapper<AiKbEntry>()
                .eq(AiKbEntry::getKbType, kbType)
                .eq(AiKbEntry::getCode, code));
        if (c != null && c > 0) {
            return;
        }
        AiKbEntry e = new AiKbEntry();
        e.setKbType(kbType);
        e.setCode(code);
        e.setTitle(title);
        e.setContent(content);
        e.setStatus(1);
        kbMapper.insert(e);
        log.info("已内置 AI 知识条目 {}:{}", kbType, code);
    }
}