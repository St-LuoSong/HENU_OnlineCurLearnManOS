package com.onlinecurlearnmanos.practice.service;

import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.entity.QuestionOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 练习闭环的提示词工厂。所有提示词集中在这里，便于审阅和调整教学策略。
 */
@Service
@RequiredArgsConstructor
public class PracticePromptService {

    private static final Map<String, String> ERROR_LABELS = Map.of(
            "conceptual", "概念理解错误",
            "procedural", "解题步骤错误",
            "factual", "事实记忆错误",
            "logical", "逻辑推理错误",
            "misinterpretation", "题目理解错误"
    );

    private static final Map<String, String> ERROR_STRATEGIES = Map.of(
            "conceptual", "重点解释核心概念，使用直观的比喻和图形化描述",
            "procedural", "分解解题步骤，展示详细的操作流程",
            "factual", "提供记忆技巧，使用联想和重复练习",
            "logical", "培养逻辑思维，使用推理训练",
            "misinterpretation", "加强题目理解训练，提升审题能力"
    );

    private static final Map<String, String> LEVEL_ADJUSTMENTS = Map.of(
            "C", "从最基础的概念讲起，使用最简单的语言和大量例子",
            "B", "使用中等难度的讲解，适当引入拓展内容",
            "A", "可以深入讲解概念的本质和应用，并适当挑战思维"
    );

    private static final Map<String, String> QUESTION_TYPE_NAMES = Map.of(
            "CHOICE", "选择题",
            "JUDGMENT", "判断题",
            "QA", "问答题",
            "FILL", "填空题",
            "APPLICATION", "应用题"
    );

    public String welcomeMessage(String subjectName, String knowledgePointName) {
        StringBuilder builder = new StringBuilder();
        builder.append("欢迎进入").append(subjectName).append("练习空间。\n\n");
        if (StringUtils.hasText(knowledgePointName)) {
            builder.append("本次聚焦知识点：").append(knowledgePointName).append("。\n\n");
        }
        builder.append("你可以：\n");
        builder.append("1. 直接提问，我会先讲清概念再引导你思考；\n");
        builder.append("2. 点击“出题练习”，我会从题库抽一道题给你；\n");
        builder.append("3. 答题过程中随时点击“分层提示”，我不会直接给答案。\n\n");
        builder.append("准备好就可以开始了。");
        return builder.toString();
    }

    public String tutorSystemPrompt(
            String subjectName,
            String knowledgeContext,
            String currentGrade
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("你是一位有经验的").append(subjectName).append("课程 AI 导师。\n\n");
        builder.append("## 教学风格\n");
        builder.append("1. 采用苏格拉底式提问，通过连续的引导性问题帮助学生自主思考；\n");
        builder.append("2. 用贴近生活的实例解释抽象概念；\n");
        builder.append("3. 根据学生当前理解等级调整讲解深度：")
                .append(LEVEL_ADJUSTMENTS.getOrDefault(currentGrade, "根据学生反应灵活调整"))
                .append("；\n");
        builder.append("4. 对学生的回答给出具体、可执行的反馈，不做空泛鼓励。\n\n");
        builder.append("## 教学原则\n");
        builder.append("1. 先判断学生卡在哪一步，再针对性讲解；\n");
        builder.append("2. 由浅入深，每次回复聚焦一个核心知识点；\n");
        builder.append("3. 主动指出常见误区，并说明为什么容易出错；\n");
        builder.append("4. 不要展示思考过程，直接输出要回复学生的内容。\n");
        if (StringUtils.hasText(knowledgeContext)) {
            builder.append("\n## 课程知识库\n").append(knowledgeContext);
        }
        return builder.toString();
    }

    public String hintPrompt(
            Question question,
            List<QuestionOption> options,
            String knowledgeContext,
            String currentGrade
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("学生正在做下面这道题，希望获得提示。你不能给出最终答案，也不能直接点出正确选项。\n\n");
        builder.append("## 题目\n");
        builder.append(formatQuestion(question, options)).append('\n');
        if (StringUtils.hasText(knowledgeContext)) {
            builder.append("\n## 相关知识点\n").append(knowledgeContext).append('\n');
        }
        builder.append("\n## 学生当前理解等级\n")
                .append(currentGrade)
                .append('\n');
        builder.append("\n## 输出要求\n");
        builder.append("输出三个层次的提示，逐步引导学生自己得出结论，严格使用以下格式：\n");
        builder.append("【解题提示】\n");
        builder.append("提示1（知识激活）：...\n");
        builder.append("提示2（方法指导）：...\n");
        builder.append("提示3（检查要点）：...\n");
        return builder.toString();
    }

    public String evaluationPrompt(
            Question question,
            List<QuestionOption> options,
            String studentAnswer
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("请评估学生对下面这道题的作答。\n\n");
        builder.append("## 题目信息\n");
        builder.append("- 题型：").append(questionTypeName(question.getQuestionType())).append('\n');
        builder.append("- 题目：").append(question.getContent()).append('\n');
        if (options != null && !options.isEmpty()) {
            builder.append("- 选项：").append(formatOptions(options)).append('\n');
        }
        builder.append("- 参考答案：").append(question.getCorrectAnswer()).append('\n');
        if (StringUtils.hasText(question.getExplanation())) {
            builder.append("- 解析：").append(question.getExplanation()).append('\n');
        }
        builder.append("\n## 学生作答\n").append(studentAnswer).append('\n');
        builder.append("\n## 评估维度\n");
        builder.append("1. 答案正确性；2. 理解深度；3. 表达清晰度；4. 思维过程完整性。\n");
        builder.append("\n## 评级标准\n");
        builder.append("- A：完全正确且理解深刻；\n");
        builder.append("- B：基本正确，但存在小错误或理解不够深入；\n");
        builder.append("- C：理解有误，需要重新学习。\n");
        builder.append("\n## 输出格式\n");
        builder.append("只输出 JSON，不要输出 Markdown 和解释：\n");
        builder.append("""
                {
                  "is_correct": true,
                  "grade": "A",
                  "feedback": "针对学生作答的具体反馈",
                  "error_type": "conceptual",
                  "error_description": "错误类型描述，答对时为 null",
                  "improvement_suggestion": "可执行的改进建议"
                }
                """);
        builder.append("\nerror_type 只能是 conceptual、procedural、factual、logical、"
                + "misinterpretation 之一，答对时返回 null。\n");
        return builder.toString();
    }

    public String remediationPrompt(
            String subjectName,
            String topic,
            int failures,
            String errorType,
            String recentErrors,
            String currentGrade
    ) {
        String strategy = ERROR_STRATEGIES.getOrDefault(errorType, "采用多样化教学方法");
        String errorLabel = errorLabel(errorType);
        StringBuilder builder = new StringBuilder();
        builder.append("学生在学习").append(subjectName).append("的“")
                .append(topic).append("”时已经连续答错 ")
                .append(failures).append(" 次，请生成个性化补救教学。\n\n");
        builder.append("## 学生情况\n");
        builder.append("- 当前理解等级：").append(currentGrade).append('\n');
        builder.append("- 主要错误类型：").append(errorLabel).append('\n');
        builder.append("- 对应教学策略：").append(strategy).append('\n');
        if (StringUtils.hasText(recentErrors)) {
            builder.append("\n## 最近的作答反馈\n").append(recentErrors).append('\n');
        }
        builder.append("\n## 输出要求\n");
        builder.append("1. ").append(LEVEL_ADJUSTMENTS.getOrDefault(currentGrade, "根据学生水平调整难度")).append("；\n");
        builder.append("2. 用新的角度重新解释核心概念，避免堆砌术语；\n");
        builder.append("3. 给出 3 到 5 个递进式的小步骤练习；\n");
        builder.append("4. 使用以下结构：问题诊断、重新讲解、递进练习、改进建议。\n");
        builder.append("直接输出内容，不要展示思考过程。");
        return builder.toString();
    }

    public String transferIntro() {
        return """
                你对基础题掌握得不错，接下来是一道迁移测试题。
                迁移测试考查能不能把同一个知识点用到新情境里，答对即视为真正掌握。

                """;
    }

    public String masteredMessage() {
        return """
                迁移测试通过，这个知识点已经掌握。
                后续练习会继续追踪你的理解等级，想换内容可以新建一个练习会话。""";
    }

    public String formatQuestion(Question question, List<QuestionOption> options) {
        StringBuilder builder = new StringBuilder();
        int difficulty = question.getDifficulty() == null ? 1 : question.getDifficulty();
        builder.append('【').append(questionTypeName(question.getQuestionType()))
                .append("】难度：").append("★".repeat(Math.max(1, Math.min(5, difficulty))))
                .append("\n\n");
        builder.append(question.getContent()).append('\n');
        if (options != null && !options.isEmpty()) {
            builder.append('\n');
            for (QuestionOption option : options) {
                builder.append(option.getOptionKey()).append(". ")
                        .append(option.getOptionText()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    public String questionTypeName(String questionType) {
        if (!StringUtils.hasText(questionType)) {
            return "题目";
        }
        return QUESTION_TYPE_NAMES.getOrDefault(questionType.trim().toUpperCase(), "题目");
    }

    public String errorLabel(String errorType) {
        if (!StringUtils.hasText(errorType)) {
            return "综合型错误";
        }
        return ERROR_LABELS.getOrDefault(errorType.trim().toLowerCase(), "综合型错误");
    }

    public boolean isKnownErrorType(String errorType) {
        return StringUtils.hasText(errorType)
                && ERROR_LABELS.containsKey(errorType.trim().toLowerCase());
    }

    private String formatOptions(List<QuestionOption> options) {
        return options.stream()
                .map(option -> option.getOptionKey() + "." + option.getOptionText())
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }
}
