package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.career.dto.CareerAnalysisGenerateRequest;
import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerComparisonResponse;
import com.onlinecurlearnmanos.career.dto.CareerDataPreviewResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertRequest;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightResponse;
import com.onlinecurlearnmanos.career.dto.GeneratedCareerAnalysis;
import com.onlinecurlearnmanos.career.entity.CareerAnalysis;
import com.onlinecurlearnmanos.career.entity.CareerAnalysisInsight;
import com.onlinecurlearnmanos.career.entity.CareerProfile;
import com.onlinecurlearnmanos.career.mapper.CareerAnalysisInsightMapper;
import com.onlinecurlearnmanos.career.mapper.CareerAnalysisMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.GoalSaveRequest;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.dto.TaskSaveRequest;
import com.onlinecurlearnmanos.learning.service.GoalService;
import com.onlinecurlearnmanos.learning.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareerAnalysisServiceImpl implements CareerAnalysisService {

    private static final String SYSTEM_PROMPT = "你是大学生生涯发展分析助手，只输出结构化 JSON。";

    private final CareerAnalysisMapper analysisMapper;
    private final CareerAnalysisInsightMapper insightMapper;
    private final CareerAnalysisPersistenceService persistenceService;
    private final CareerProfileService profileService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final GoalService goalService;
    private final TaskService taskService;

    @Override
    public List<CareerAnalysisResponse> list(Long userId) {
        return analysisMapper.selectList(new LambdaQueryWrapper<CareerAnalysis>()
                        .eq(CareerAnalysis::getUserId, userId)
                        .orderByDesc(CareerAnalysis::getCreatedAt)
                        .orderByDesc(CareerAnalysis::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CareerAnalysisResponse get(Long userId, Long id) {
        return toResponse(getRequired(userId, id));
    }

    @Override
    public CareerAnalysisResponse generate(Long userId, CareerAnalysisGenerateRequest request) {
        CareerProfile profile = profileService.requireConsentedProfile(userId);
        CareerDataPreviewResponse data = profileService.previewFor(profile);
        String prompt = buildPrompt(
                profile,
                data,
                request == null ? null : request.focus(),
                previousChangeSummary(userId, data)
        );

        return llmService.execute(
                userId,
                new LlmChatRequest(prompt, SYSTEM_PROMPT, null, 0.4, null, null),
                "AI_CAREER",
                "AI 生涯分析生成",
                providerResponse -> {
                    try {
                        String content = stripCodeFence(providerResponse.content());
                        GeneratedCareerAnalysis generated = objectMapper.readValue(
                                content,
                                GeneratedCareerAnalysis.class
                        );
                        CareerAnalysis saved = persistenceService.save(
                                userId,
                                generated,
                                providerResponse,
                                data
                        );
                        return toResponse(saved);
                    } catch (BusinessException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR,
                                "AI 生成生涯分析失败，请重试"
                        );
                    }
                }
        );
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        getRequired(userId, id);
        insightMapper.delete(new LambdaQueryWrapper<CareerAnalysisInsight>()
                .eq(CareerAnalysisInsight::getAnalysisId, id));
        analysisMapper.deleteById(id);
    }

    private String buildPrompt(
            CareerProfile profile,
            CareerDataPreviewResponse data,
            String focus,
            String changeSummary
    ) {
        StringBuilder builder = new StringBuilder("""
                你是大学生生涯发展分析助手。请基于学生自述信息和学习数据，给出辅助性的方向建议。
                要求：
                1. 结论只能作为参考，使用“建议”“可以尝试”等表述，禁止下绝对性判断；
                2. 不得编造数据中没有的事实，数据不足时明确说明依据有限；
                3. 覆盖六个维度，每个维度 1 到 2 条，共 6 到 9 条：
                   DIRECTION(发展方向)、INTEREST(兴趣特点)、ABILITY(能力特点)、
                   SUGGESTION(发展建议)、PATH(学习路径)、GROWTH(阶段成长建议)；
                4. 另外必须给出 1 到 3 条 ACTION(下一阶段行动)，每条都要写清 reason(为什么做)
                   与 targetDate(建议完成日期，yyyy-MM-dd)；
                5. score 是 0 到 100 的整数，表示该维度当前的匹配或准备程度，无法判断时返回 null；
                6. 每条 content 控制在 120 字以内，summary 控制在 150 字以内；
                7. 只返回 JSON，不要输出 Markdown，不要输出解释。

                返回格式：
                {
                  "title": "分析标题",
                  "summary": "整体结论摘要",
                  "insights": [
                    {"category": "DIRECTION", "title": "条目标题", "content": "具体说明", "reason": null, "targetDate": null, "score": 75},
                    {"category": "ACTION", "title": "补足线性代数基础", "content": "具体做法", "reason": "数据分析方向需要矩阵基础", "targetDate": "2026-10-31", "score": null}
                  ]
                }

                学生自述信息：
                """);
        builder.append("专业：").append(textOr(profile.getMajor(), "未填写")).append('\n');
        builder.append("年级：").append(textOr(profile.getGradeLevel(), "未填写")).append('\n');
        builder.append("每周可投入学习时间：")
                .append(profile.getWeeklyHours() == null
                        ? "未填写"
                        : profile.getWeeklyHours() + " 小时")
                .append('\n');
        builder.append("兴趣标签：").append(textOr(profile.getInterestTags(), "未填写")).append('\n');
        builder.append("自我评价的优势：").append(textOr(profile.getStrengthNotes(), "未填写")).append('\n');
        builder.append("期望发展方向：").append(textOr(profile.getCareerGoal(), "未填写")).append('\n');

        builder.append("\n本次授权使用的学习数据：\n");
        builder.append("学习记录：").append(data.learningRecordCount()).append(" 条，累计 ")
                .append(data.learningMinutes()).append(" 分钟\n");
        builder.append("已掌握知识点：").append(data.masteredPointCount()).append(" 个\n");
        builder.append("涉及课程：")
                .append(data.subjects().isEmpty() ? "暂无" : String.join("、", data.subjects()))
                .append('\n');
        builder.append("学习目标：").append(data.goalCount()).append(" 个；学习任务 ")
                .append(data.taskCount()).append(" 个，其中已完成 ")
                .append(data.completedTaskCount()).append(" 个\n");
        builder.append("目标摘录：")
                .append(data.goalTitles().isEmpty() ? "暂无" : String.join("；", data.goalTitles()))
                .append('\n');
        builder.append("云班级已批改成绩：").append(data.gradedSubmissionCount()).append(" 条\n");
        builder.append("成绩平均分：")
                .append(data.averageScore() == null ? "暂无" : data.averageScore())
                .append('\n');

        if (StringUtils.hasText(changeSummary)) {
            builder.append("\n与上次分析相比的数据变化（请结合这些变化解释趋势，不要自己重新计算）：\n")
                    .append(changeSummary)
                    .append('\n');
        }
        if (StringUtils.hasText(focus)) {
            builder.append("\n本次补充关注点：").append(focus.trim()).append('\n');
        }
        return builder.toString();
    }

    @Override
    @Transactional
    public CareerInsightConvertResponse convertInsight(
            Long userId,
            Long insightId,
            CareerInsightConvertRequest request
    ) {
        CareerAnalysisInsight insight = getRequiredInsight(userId, insightId);
        String description = buildActionDescription(insight);
        if ("TASK".equals(request.target())) {
            if (insight.getConvertedTaskId() != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该建议已经转换为学习任务");
            }
            TaskResponse task = taskService.create(userId, new TaskSaveRequest(
                    null,
                    insight.getTitle(),
                    description,
                    "CAREER",
                    insight.getTargetDate() == null ? null : insight.getTargetDate().atStartOfDay(),
                    "MEDIUM"
            ));
            insightMapper.update(null, new LambdaUpdateWrapper<CareerAnalysisInsight>()
                    .eq(CareerAnalysisInsight::getId, insightId)
                    .set(CareerAnalysisInsight::getConvertedTaskId, task.id()));
            return new CareerInsightConvertResponse("TASK", task.id());
        }
        if (insight.getConvertedGoalId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该建议已经转换为学习目标");
        }
        GoalResponse goal = goalService.create(userId, new GoalSaveRequest(
                null,
                insight.getTitle(),
                description,
                insight.getTargetDate(),
                "MEDIUM"
        ));
        insightMapper.update(null, new LambdaUpdateWrapper<CareerAnalysisInsight>()
                .eq(CareerAnalysisInsight::getId, insightId)
                .set(CareerAnalysisInsight::getConvertedGoalId, goal.id()));
        return new CareerInsightConvertResponse("GOAL", goal.id());
    }

    @Override
    public CareerComparisonResponse comparison(Long userId, Long analysisId) {
        CareerAnalysis analysis = getRequired(userId, analysisId);
        CareerDataPreviewResponse before = readSnapshot(analysis.getDataSnapshot());
        CareerDataPreviewResponse after = profileService.preview(userId);
        List<String> notes = buildChangeNotes(before, after);
        return new CareerComparisonResponse(
                analysis.getId(),
                analysis.getCreatedAt(),
                before == null ? null : before.learningRecordCount(),
                after.learningRecordCount(),
                before == null ? null : before.learningMinutes(),
                after.learningMinutes(),
                before == null ? null : before.masteredPointCount(),
                after.masteredPointCount(),
                before == null ? null : before.completedTaskCount(),
                after.completedTaskCount(),
                before == null ? null : before.gradedSubmissionCount(),
                after.gradedSubmissionCount(),
                before == null ? null : before.averageScore(),
                after.averageScore(),
                notes
        );
    }

    /**
     * 量化变化由后端计算，AI 只负责解释，避免模型自己"算数"出错。
     */
    private List<String> buildChangeNotes(
            CareerDataPreviewResponse before,
            CareerDataPreviewResponse after
    ) {
        List<String> notes = new ArrayList<>();
        if (before == null) {
            notes.add("没有可用的历史数据快照，无法计算变化。");
            return notes;
        }
        notes.add("学习记录：" + before.learningRecordCount()
                + " 条 → " + after.learningRecordCount() + " 条");
        notes.add("累计学习时长：" + before.learningMinutes()
                + " 分钟 → " + after.learningMinutes() + " 分钟");
        notes.add("已掌握知识点：" + before.masteredPointCount()
                + " 个 → " + after.masteredPointCount() + " 个");
        notes.add("已完成任务：" + before.completedTaskCount()
                + " 个 → " + after.completedTaskCount() + " 个");
        notes.add("已批改成绩：" + before.gradedSubmissionCount()
                + " 条 → " + after.gradedSubmissionCount() + " 条");
        return notes;
    }

    private String previousChangeSummary(Long userId, CareerDataPreviewResponse current) {
        CareerAnalysis previous = analysisMapper.selectOne(new LambdaQueryWrapper<CareerAnalysis>()
                .eq(CareerAnalysis::getUserId, userId)
                .orderByDesc(CareerAnalysis::getCreatedAt)
                .orderByDesc(CareerAnalysis::getId)
                .last("LIMIT 1"));
        if (previous == null) {
            return "";
        }
        CareerDataPreviewResponse before = readSnapshot(previous.getDataSnapshot());
        if (before == null) {
            return "";
        }
        return String.join("；", buildChangeNotes(before, current));
    }

    private CareerDataPreviewResponse readSnapshot(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot, CareerDataPreviewResponse.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String buildActionDescription(CareerAnalysisInsight insight) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(insight.getContent())) {
            builder.append(insight.getContent().trim());
        }
        if (StringUtils.hasText(insight.getReason())) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("依据：").append(insight.getReason().trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private CareerAnalysisInsight getRequiredInsight(Long userId, Long insightId) {
        CareerAnalysisInsight insight = insightMapper.selectById(insightId);
        if (insight == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生涯分析条目不存在");
        }
        CareerAnalysis analysis = analysisMapper.selectById(insight.getAnalysisId());
        if (analysis == null || !analysis.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生涯分析条目不存在");
        }
        return insight;
    }

    private CareerAnalysis getRequired(Long userId, Long id) {
        CareerAnalysis analysis = analysisMapper.selectOne(new LambdaQueryWrapper<CareerAnalysis>()
                .eq(CareerAnalysis::getId, id)
                .eq(CareerAnalysis::getUserId, userId)
                .last("LIMIT 1"));
        if (analysis == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生涯分析记录不存在");
        }
        return analysis;
    }

    private CareerAnalysisResponse toResponse(CareerAnalysis analysis) {
        List<CareerInsightResponse> insights = insightMapper.selectList(
                        new LambdaQueryWrapper<CareerAnalysisInsight>()
                                .eq(CareerAnalysisInsight::getAnalysisId, analysis.getId())
                                .orderByAsc(CareerAnalysisInsight::getSortOrder)
                                .orderByAsc(CareerAnalysisInsight::getId))
                .stream()
                .map(insight -> new CareerInsightResponse(
                        insight.getId(),
                        insight.getCategory(),
                        insight.getTitle(),
                        insight.getContent(),
                        insight.getReason(),
                        insight.getTargetDate(),
                        insight.getConvertedGoalId(),
                        insight.getConvertedTaskId(),
                        insight.getScore(),
                        insight.getSortOrder() == null ? 0 : insight.getSortOrder()
                ))
                .toList();
        return new CareerAnalysisResponse(
                analysis.getId(),
                analysis.getTitle(),
                analysis.getSummary(),
                analysis.getStatus(),
                analysis.getProviderCode(),
                analysis.getModel(),
                splitSources(analysis.getDataSources()),
                analysis.getCreatedAt(),
                insights
        );
    }

    private List<String> splitSources(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(source -> !source.isEmpty())
                .toList();
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stripCodeFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }
}
