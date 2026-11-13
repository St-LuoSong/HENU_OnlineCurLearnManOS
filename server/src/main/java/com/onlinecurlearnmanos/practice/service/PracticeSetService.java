package com.onlinecurlearnmanos.practice.service;

import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.practice.dto.GeneratedPracticeQuestion;
import com.onlinecurlearnmanos.practice.dto.PracticeAnswerItemRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSetAnswersRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSetCreateRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSetDetailResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSetItemResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSetSummaryResponse;
import com.onlinecurlearnmanos.practice.entity.PracticeSet;
import com.onlinecurlearnmanos.practice.entity.PracticeSetItem;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetItemMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeSetService {

    private static final Logger log = LoggerFactory.getLogger(PracticeSetService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> OBJECTIVE_TYPES = List.of("single_choice", "fill_blank");

    private final PracticeSetMapper setMapper;
    private final PracticeSetItemMapper itemMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LlmService llmService;
    private final PointService pointService;
    private final ObjectMapper objectMapper;
    private final LearningRecordService learningRecordService;

    @Value("${app.practice.repeat-cost:20}")
    private int repeatCost;

    public PracticeSetDetailResponse create(Long userId, PracticeSetCreateRequest request) {
        Subject subject = requireSubject(request.subjectId());
        KnowledgePoint point = validatePoint(request.subjectId(), request.knowledgePointId());
        AiMode mode = AiMode.parse(request.mode());

        LocalDateTime now = LocalDateTime.now();
        String title = StringUtils.hasText(request.title())
                ? request.title().trim()
                : subject.getName() + " · " + now.toLocalDate() + " 练习";
        String idempotencyKey = "practice-set:" + userId + ":" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String systemPrompt = buildQuestionPrompt(subject, point);

        PointTransaction practiceFee = reservePracticeFee(userId, request.subjectId(), mode, idempotencyKey, requestId);
        try {
            PracticeSetDetailResponse result = llmService.execute(
                    userId,
                    new LlmChatRequest(
                            "请生成一套新的练习。只返回 JSON 数组，不要输出解释文字。",
                            systemPrompt,
                            null,
                            0.5,
                            idempotencyKey,
                            requestId
                    ),
                    mode,
                    "AI_PRACTICE_SET",
                    "生成一套练习题",
                    response -> persistGeneratedSet(
                            userId,
                            request.subjectId(),
                            request.knowledgePointId(),
                            title,
                            mode,
                            response
                    )
            );
            if (practiceFee != null) {
                try {
                    pointService.confirm(practiceFee.getId());
                } catch (RuntimeException exception) {
                    log.error(
                            "Practice set created but repeat fee confirmation failed, transactionId={}",
                            practiceFee.getId(),
                            exception
                    );
                }
            }
            return result;
        } catch (RuntimeException exception) {
            if (practiceFee != null) {
                pointService.refund(practiceFee.getId(), "练习套题生成失败退款");
            }
            throw exception;
        }
    }

    public List<PracticeSetSummaryResponse> list(Long userId, Long subjectId) {
        LambdaQueryWrapper<PracticeSet> query = new LambdaQueryWrapper<PracticeSet>()
                .eq(PracticeSet::getUserId, userId)
                .orderByDesc(PracticeSet::getCreatedAt);
        if (subjectId != null) {
            query.eq(PracticeSet::getSubjectId, subjectId);
        }
        return setMapper.selectList(query).stream().map(this::toSummary).toList();
    }

    public PracticeSetDetailResponse get(Long userId, Long setId) {
        PracticeSet set = getRequired(userId, setId);
        return toDetail(set, itemsOf(set.getId()));
    }

    @Transactional
    public PracticeSetDetailResponse saveAnswers(
            Long userId,
            Long setId,
            PracticeSetAnswersRequest request
    ) {
        PracticeSet set = getRequired(userId, setId);
        if (!"IN_PROGRESS".equals(set.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "这套练习已经交卷，不能继续修改");
        }
        List<PracticeSetItem> items = itemsOf(setId);
        Map<Long, PracticeSetItem> byId = items.stream()
                .collect(Collectors.toMap(PracticeSetItem::getId, item -> item));
        for (PracticeAnswerItemRequest answer : request.answers()) {
            PracticeSetItem item = byId.get(answer.itemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "答案中包含不属于本套练习的题目");
            }
            item.setStudentAnswer(answer.answer().trim());
            item.setAnsweredAt(LocalDateTime.now());
            itemMapper.updateById(item);
        }
        int answered = (int) items.stream()
                .filter(item -> StringUtils.hasText(item.getStudentAnswer()))
                .count();
        set.setAnsweredCount(answered);
        setMapper.updateById(set);
        return toDetail(set, items);
    }

    @Transactional
    public PracticeSetDetailResponse submit(Long userId, Long setId) {
        PracticeSet set = getRequired(userId, setId);
        if ("SUBMITTED".equals(set.getStatus())) {
            return toDetail(set, itemsOf(setId));
        }
        List<PracticeSetItem> items = itemsOf(setId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "这套练习没有题目");
        }
        gradeItems(items, false);
        finishSet(set, items);
        recordLearningResult(set);
        return toDetail(set, items);
    }

    private PracticeSetDetailResponse persistGeneratedSet(
            Long userId,
            Long subjectId,
            Long knowledgePointId,
            String title,
            AiMode mode,
            LlmChatResponse response
    ) {
        List<GeneratedPracticeQuestion> questions = parseQuestions(response.content());
        // 只有新一轮题目成功解析后才归档旧套题，避免生成失败时提前关闭用户正在做的练习。
        autoCloseOpenSets(userId, subjectId);
        PracticeSet set = new PracticeSet();
        set.setUserId(userId);
        set.setSubjectId(subjectId);
        set.setKnowledgePointId(knowledgePointId);
        set.setTitle(title);
        set.setBillingMode(mode.name());
        set.setStatus("IN_PROGRESS");
        set.setQuestionCount(questions.size());
        set.setAnsweredCount(0);
        set.setCorrectCount(0);
        set.setStartedAt(LocalDateTime.now());
        setMapper.insert(set);

        List<PracticeSetItem> items = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            GeneratedPracticeQuestion question = questions.get(index);
            PracticeSetItem item = new PracticeSetItem();
            item.setSetId(set.getId());
            item.setSeq(index + 1);
            item.setQuestionType(normalizeType(question.type()));
            item.setStem(question.stem());
            item.setOptionsJson(writeOptions(question.options()));
            item.setCorrectAnswer(question.correctAnswer());
            item.setExplanation(question.explanation());
            item.setMaxScore(question.maxScore() == null ? 10 : Math.max(1, question.maxScore()));
            itemMapper.insert(item);
            items.add(item);
        }
        return toDetail(set, items);
    }

    private PointTransaction reservePracticeFee(
            Long userId,
            Long subjectId,
            AiMode mode,
            String idempotencyKey,
            String requestId
    ) {
        if (mode == AiMode.EXPERT) {
            return null;
        }
        long todayCount = setMapper.selectCount(new LambdaQueryWrapper<PracticeSet>()
                .eq(PracticeSet::getUserId, userId)
                .eq(PracticeSet::getSubjectId, subjectId)
                .eq(PracticeSet::getBillingMode, AiMode.STANDARD.name())
                .ge(PracticeSet::getCreatedAt, LocalDate.now(ZONE).atStartOfDay()));
        if (todayCount == 0) {
            return null;
        }
        return pointService.reserve(
                userId,
                repeatCost,
                "AI_PRACTICE_SET",
                idempotencyKey + ":fee",
                "PRACTICE_SET",
                requestId,
                "再次生成练习套题"
        );
    }

    private void autoCloseOpenSets(Long userId, Long subjectId) {
        List<PracticeSet> openSets = setMapper.selectList(new LambdaQueryWrapper<PracticeSet>()
                .eq(PracticeSet::getUserId, userId)
                .eq(PracticeSet::getSubjectId, subjectId)
                .eq(PracticeSet::getStatus, "IN_PROGRESS"));
        for (PracticeSet openSet : openSets) {
            List<PracticeSetItem> items = itemsOf(openSet.getId());
            gradeItems(items, true);
            finishSet(openSet, items);
            recordLearningResult(openSet);
        }
    }

    private void recordLearningResult(PracticeSet set) {
        int score = set.getScore() == null ? 0 : set.getScore();
        int durationMinutes = set.getDurationSeconds() == null
                ? 0
                : Math.max(0, set.getDurationSeconds() / 60);
        learningRecordService.create(
                set.getUserId(),
                new LearningRecordSaveRequest(
                        set.getSubjectId(),
                        set.getKnowledgePointId(),
                        null,
                        "AI_PRACTICE_SET",
                        durationMinutes,
                        java.math.BigDecimal.valueOf(score),
                        score >= 60,
                        "练习套题：" + set.getTitle(),
                        set.getSubmittedAt()
                )
        );
    }

    private void gradeItems(List<PracticeSetItem> items, boolean autoClosed) {
        for (PracticeSetItem item : items) {
            String answer = item.getStudentAnswer();
            if (!StringUtils.hasText(answer)) {
                item.setIsCorrect(0);
                item.setAwardedScore(0);
                item.setAiFeedback(autoClosed ? "上一套练习已归档，本题未作答" : "本题未作答");
                item.setErrorType("UNANSWERED");
                item.setAnsweredAt(LocalDateTime.now());
                itemMapper.updateById(item);
                continue;
            }
            if (OBJECTIVE_TYPES.contains(item.getQuestionType())) {
                boolean correct = isObjectiveCorrect(item, answer);
                item.setIsCorrect(correct ? 1 : 0);
                item.setAwardedScore(correct ? item.getMaxScore() : 0);
                item.setAiFeedback(correct ? "回答正确" : "回答与参考答案不一致");
                item.setErrorType(correct ? null : ("fill_blank".equals(item.getQuestionType()) ? "RECALL" : "CONCEPT"));
                item.setAnsweredAt(LocalDateTime.now());
                itemMapper.updateById(item);
                continue;
            }
            gradeShortAnswer(item, autoClosed);
        }
    }

    private void gradeShortAnswer(PracticeSetItem item, boolean autoClosed) {
        if (autoClosed) {
            item.setIsCorrect(0);
            item.setAwardedScore(0);
            item.setAiFeedback("上一套练习已归档；简答题未提交，本次未计分");
            item.setErrorType("AUTO_CLOSED");
            item.setAnsweredAt(LocalDateTime.now());
            itemMapper.updateById(item);
            return;
        }
        String reference = normalize(item.getCorrectAnswer());
        String answer = normalize(item.getStudentAnswer());
        if (!StringUtils.hasText(reference)) {
            item.setIsCorrect(0);
            item.setAwardedScore(0);
            item.setAiFeedback("缺少参考答案，暂无法评分");
            item.setErrorType("NO_REFERENCE");
            itemMapper.updateById(item);
            return;
        }
        List<String> keyPoints = splitKeyPoints(item.getCorrectAnswer());
        if (keyPoints.isEmpty()) {
            boolean matched = answer.contains(reference) || reference.contains(answer);
            item.setIsCorrect(matched ? 1 : 0);
            item.setAwardedScore(matched ? item.getMaxScore() : 0);
            item.setAiFeedback(matched ? "回答与参考答案一致" : "回答与参考答案不一致");
            item.setErrorType(matched ? null : "EXPRESSION");
            item.setAnsweredAt(LocalDateTime.now());
            itemMapper.updateById(item);
            return;
        }
        long matched = keyPoints.stream().filter(point -> answer.contains(normalize(point))).count();
        double ratio = keyPoints.isEmpty() ? 0 : (double) matched / keyPoints.size();
        int score = (int) Math.round(item.getMaxScore() * ratio);
        boolean correct = ratio >= 0.6;
        item.setIsCorrect(correct ? 1 : 0);
        item.setAwardedScore(score);
        List<String> missing = keyPoints.stream()
                .filter(point -> !answer.contains(normalize(point)))
                .toList();
        item.setAiFeedback(
                "按参考答案要点匹配：" + matched + "/" + keyPoints.size()
                        + (missing.isEmpty() ? "，要点完整" : "，可补充：" + String.join("、", missing))
        );
        item.setErrorType(correct ? null : "EXPRESSION");
        item.setAnsweredAt(LocalDateTime.now());
        itemMapper.updateById(item);
    }

    private void finishSet(PracticeSet set, List<PracticeSetItem> items) {
        int answered = (int) items.stream().filter(item -> StringUtils.hasText(item.getStudentAnswer())).count();
        int correct = (int) items.stream().filter(item -> Integer.valueOf(1).equals(item.getIsCorrect())).count();
        int earned = items.stream().mapToInt(item -> item.getAwardedScore() == null ? 0 : item.getAwardedScore()).sum();
        int total = items.stream().mapToInt(item -> item.getMaxScore() == null ? 0 : item.getMaxScore()).sum();
        set.setAnsweredCount(answered);
        set.setCorrectCount(correct);
        set.setScore(total <= 0 ? 0 : (int) Math.round(earned * 100.0 / total));
        set.setStatus("SUBMITTED");
        LocalDateTime submittedAt = LocalDateTime.now();
        set.setSubmittedAt(submittedAt);
        if (set.getStartedAt() != null) {
            LocalDateTime effectiveEnd = set.getUpdatedAt() == null ? submittedAt : set.getUpdatedAt();
            if (effectiveEnd.isBefore(set.getStartedAt())) {
                effectiveEnd = submittedAt;
            }
            set.setDurationSeconds((int) Math.max(0, Duration.between(set.getStartedAt(), effectiveEnd).getSeconds()));
        }
        setMapper.updateById(set);
    }

    private boolean isObjectiveCorrect(PracticeSetItem item, String answer) {
        if ("single_choice".equals(item.getQuestionType())) {
            String expected = choiceKey(item.getCorrectAnswer());
            String actual = choiceKey(answer);
            if (StringUtils.hasText(expected) && StringUtils.hasText(actual)) {
                return expected.equals(actual);
            }
            if (StringUtils.hasText(expected)) {
                List<String> options = readOptions(item.getOptionsJson());
                int index = expected.charAt(0) - 'A';
                if (index >= 0 && index < options.size()) {
                    return normalize(answer).equals(normalize(options.get(index)));
                }
            }
            List<String> options = readOptions(item.getOptionsJson());
            String expectedText = normalize(item.getCorrectAnswer());
            for (int index = 0; index < options.size(); index++) {
                if (expectedText.equals(normalize(options.get(index)))) {
                    return choiceKey(answer).equals(String.valueOf((char) ('A' + index)));
                }
            }
        }
        String normalized = normalize(answer);
        return splitAcceptedAnswers(item.getCorrectAnswer()).stream()
                .map(this::normalize)
                .anyMatch(normalized::equals);
    }

    private String choiceKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        var matcher = java.util.regex.Pattern.compile("^([A-H])(?:[.、:：\\s]|$)").matcher(trimmed);
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<String> splitAcceptedAnswers(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("\\s*[|；;]\\s*"));
    }

    private List<String> splitKeyPoints(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.split("\\s*[；;，,\\n]\\s*"))
                .stream()
                .map(String::trim)
                .filter(point -> point.length() >= 2)
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("，", ",")
                .replace("；", ";")
                .replace("。", ".")
                .replaceAll("^[.。,;:]+|[.。,;:]+$", "");
    }

    private PracticeSetDetailResponse toDetail(PracticeSet set, List<PracticeSetItem> items) {
        boolean revealed = "SUBMITTED".equals(set.getStatus());
        Subject subject = subjectMapper.selectById(set.getSubjectId());
        KnowledgePoint point = set.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(set.getKnowledgePointId());
        List<PracticeSetItemResponse> responses = items.stream()
                .sorted(Comparator.comparing(PracticeSetItem::getSeq))
                .map(item -> new PracticeSetItemResponse(
                        item.getId(),
                        item.getSeq(),
                        item.getQuestionType(),
                        item.getStem(),
                        readOptions(item.getOptionsJson()),
                        item.getStudentAnswer(),
                        revealed && item.getIsCorrect() != null ? Integer.valueOf(1).equals(item.getIsCorrect()) : null,
                        revealed ? item.getAwardedScore() : null,
                        item.getMaxScore() == null ? 10 : item.getMaxScore(),
                        revealed ? item.getAiFeedback() : null,
                        revealed ? item.getErrorType() : null,
                        revealed ? item.getCorrectAnswer() : null,
                        revealed ? item.getExplanation() : null
                ))
                .toList();
        return new PracticeSetDetailResponse(
                set.getId(),
                set.getTitle(),
                set.getSubjectId(),
                subject == null ? null : subject.getName(),
                set.getKnowledgePointId(),
                point == null ? null : point.getName(),
                set.getBillingMode(),
                set.getStatus(),
                set.getQuestionCount() == null ? responses.size() : set.getQuestionCount(),
                set.getAnsweredCount() == null ? 0 : set.getAnsweredCount(),
                "SUBMITTED".equals(set.getStatus()) && set.getCorrectCount() != null ? set.getCorrectCount() : 0,
                "SUBMITTED".equals(set.getStatus()) ? set.getScore() : null,
                set.getDurationSeconds(),
                set.getStartedAt(),
                set.getSubmittedAt(),
                responses
        );
    }

    private PracticeSetSummaryResponse toSummary(PracticeSet set) {
        Subject subject = subjectMapper.selectById(set.getSubjectId());
        KnowledgePoint point = set.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(set.getKnowledgePointId());
        boolean submitted = "SUBMITTED".equals(set.getStatus());
        return new PracticeSetSummaryResponse(
                set.getId(),
                set.getTitle(),
                set.getSubjectId(),
                subject == null ? null : subject.getName(),
                set.getKnowledgePointId(),
                point == null ? null : point.getName(),
                set.getBillingMode(),
                set.getStatus(),
                set.getQuestionCount() == null ? 0 : set.getQuestionCount(),
                set.getAnsweredCount() == null ? 0 : set.getAnsweredCount(),
                submitted && set.getCorrectCount() != null ? set.getCorrectCount() : 0,
                submitted ? set.getScore() : null,
                set.getDurationSeconds(),
                set.getStartedAt(),
                set.getSubmittedAt()
        );
    }

    private List<GeneratedPracticeQuestion> parseQuestions(String content) {
        String json = extractJson(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = root.isArray() ? root : root.path("questions");
            if (!array.isArray()) {
                throw new IllegalArgumentException("缺少 questions 数组");
            }
            List<GeneratedPracticeQuestion> questions = new ArrayList<>();
            for (JsonNode node : array) {
                String type = normalizeType(node.path("type").asText(""));
                String stem = node.path("stem").asText("").trim();
                String answer = node.has("correctAnswer")
                        ? node.path("correctAnswer").asText("")
                        : node.path("answer").asText("");
                if (!StringUtils.hasText(stem) || !StringUtils.hasText(answer)) {
                    continue;
                }
                List<String> options = new ArrayList<>();
                if (node.path("options").isArray()) {
                    node.path("options").forEach(option -> options.add(option.asText("")));
                }
                if ("single_choice".equals(type) && options.size() < 2) {
                    continue;
                }
                questions.add(new GeneratedPracticeQuestion(
                        type,
                        stem,
                        options,
                        answer.trim(),
                        node.path("explanation").asText(""),
                        node.path("maxScore").asInt(10)
                ));
            }
            if (questions.size() < 3 || questions.size() > 10) {
                throw new IllegalArgumentException("有效题目数量不在 3-10 之间");
            }
            return questions;
        } catch (Exception exception) {
            log.warn("Failed to parse generated practice set: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 生成的题目格式异常，请重新生成");
        }
    }

    private String extractJson(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                value = value.substring(firstLine + 1, lastFence).trim();
            }
        }
        int objectStart = value.indexOf('{');
        int arrayStart = value.indexOf('[');
        int start = objectStart < 0 ? arrayStart : (arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart));
        int objectEnd = value.lastIndexOf('}');
        int arrayEnd = value.lastIndexOf(']');
        int end = Math.max(objectEnd, arrayEnd);
        if (start < 0 || end <= start) {
            return value;
        }
        return value.substring(start, end + 1);
    }

    private String buildQuestionPrompt(Subject subject, KnowledgePoint point) {
        String scope = point == null ? subject.getName() : subject.getName() + " / " + point.getName();
        return "你是大学课程命题老师。围绕「" + scope + "」生成一套 6 道题，"
                + "包含 3 道单选题、2 道填空题、1 道简答题，难度由浅入深。"
                + "必须只输出合法 JSON 数组，每项字段为："
                + "type(single_choice|fill_blank|short_answer)、stem、options(字符串数组，填空和简答为空数组)、"
                + "correctAnswer(单选题用 A/B/C/D)、explanation、maxScore(整数)。"
                + "不要输出 Markdown 代码块或任何额外文字。";
    }

    private String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "single_choice", "choice", "选择题", "single-choice" -> "single_choice";
            case "fill_blank", "fill", "填空", "fill-blank" -> "fill_blank";
            default -> "short_answer";
        };
    }

    private String writeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> readOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Subject requireSubject(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return subject;
    }

    private KnowledgePoint validatePoint(Long subjectId, Long knowledgePointId) {
        if (knowledgePointId == null) {
            return null;
        }
        KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
        if (point == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        if (!subjectId.equals(point.getSubjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选课程");
        }
        return point;
    }

    private PracticeSet getRequired(Long userId, Long setId) {
        PracticeSet set = setMapper.selectOne(new LambdaQueryWrapper<PracticeSet>()
                .eq(PracticeSet::getId, setId)
                .eq(PracticeSet::getUserId, userId)
                .last("LIMIT 1"));
        if (set == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "练习套题不存在");
        }
        return set;
    }

    private List<PracticeSetItem> itemsOf(Long setId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PracticeSetItem>()
                .eq(PracticeSetItem::getSetId, setId)
                .orderByAsc(PracticeSetItem::getSeq));
    }
}
