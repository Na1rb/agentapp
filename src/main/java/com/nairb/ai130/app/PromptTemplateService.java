package com.nairb.ai130.app;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设角色模板（硬编码，无需数据库）。
 * <p>
 * 每个角色包含 code（标识）、name（显示名）、prompt（system prompt）。
 * 前端选择角色后，后端用对应的 prompt 替换默认 system message。
 */
@Service
public class PromptTemplateService {

    private static final List<PromptRole> ROLES = List.of(
        new PromptRole("default", "通用助手",
            """
            你是一个智能助手。
            1. 理解用户意图，包括同音错别字。
            2. 基于上下文回答问题。
            3. 如无相关信息则如实说明。
            """),
        new PromptRole("coder", "代码专家",
            """
            你是一位资深软件开发工程师。
            1. 精通多种编程语言，能编写高质量、可维护的代码。
            2. 优先考虑代码的可读性、性能和安全性。
            3. 给出代码时附带简要的解释和用法示例。
            4. 如果问题涉及框架或库，推荐最佳实践。
            """),
        new PromptRole("writer", "文案写手",
            """
            你是一位专业文案策划。
            1. 擅长撰写各类文案：广告、推文、文章、报告等。
            2. 语言生动、精准，符合目标受众的风格。
            3. 注重逻辑结构和表达效果。
            4. 可根据需求调整语气：正式、幽默、温暖等。
            """),
        new PromptRole("analyst", "逻辑分析员",
            """
            你是一位严谨的逻辑分析师。
            1. 对问题进行结构化拆解，按步骤推理。
            2. 使用前提→推理→结论的思维链。
            3. 指出假设条件、潜在偏差和备选方案。
            4. 最终给出有数据支撑的明确结论。
            """),
        new PromptRole("translator", "翻译专家",
            """
            你是一位资深翻译专家。
            1. 精通中英互译，保留原文语义和风格。
            2. 根据上下文选择最贴切的词汇和句式。
            3. 对专业术语保持一致性。
            4. 输出时先给出翻译，再附上简短的翻译说明（如有必要）。
            """)
    );

    /**
     * 获取所有角色列表（code + name）。
     */
    public List<Map<String, String>> listRoles() {
        return ROLES.stream().map(r -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", r.code());
            m.put("name", r.name());
            return m;
        }).toList();
    }

    /**
     * 根据 code 获取角色 prompt，找不到则返回默认 prompt。
     */
    public String getPrompt(String code) {
        if (code == null || code.isBlank()) return getDefault().prompt();
        return ROLES.stream()
                .filter(r -> r.code().equals(code))
                .findFirst()
                .map(PromptRole::prompt)
                .orElseGet(() -> getDefault().prompt());
    }

    /**
     * 获取角色的显示名称。
     */
    public String getName(String code) {
        if (code == null || code.isBlank()) return getDefault().name();
        return ROLES.stream()
                .filter(r -> r.code().equals(code))
                .findFirst()
                .map(PromptRole::name)
                .orElse(getDefault().name());
    }

    private PromptRole getDefault() {
        return ROLES.get(0);
    }

    public record PromptRole(String code, String name, String prompt) {}
}
