package com.onlinecurlearnmanos.practice.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.entity.QuestionOption;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionOptionMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.content.service.KnowledgeContextBuilder;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.learning.service.ProgressService;
import com.onlinecurlearnmanos.practice.dto.GeneratedPracticeAnswer;
import com.onlinecurlearnmanos.practice.dto.PracticeAnswerResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeMessageRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeMessageResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionCreateRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionDetailResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeTurnResponse;
import com.onlinecurlearnmanos.practice.entity.PracticeAnswer;
import com.onlinecurlearnmanos.practice.entity.PracticeMessage;
import com.onlinecurlearnmanos.practice.entity.PracticeSession;
import com.onlinecurlearnmanos.practice.mapper.PracticeAnswerMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeMessageMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private static final String LEARNING = "LEARNING";
    private static final String ASSESSING = "ASSESSING";
    private static final String TRANSFER_TEST = "TRANSFER_TEST";
    private static final String MASTERED = "MASTERED";
    private static final String REMEDIATION = "REMEDIATION";

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int HISTORY_MESSAGE_LIMIT = 10;
    private static final int HISTORY_SNIPPET_LIMIT = 240;
    private static final int QUESTION_POOL_LIMIT = 50;

    private static final List<String> PRACTICE_KEYWORDS = List.of(
            "练习", "做题", "测试", "出题", "考考我", "quiz", "test", "practice"
    );
    private static final List<String> HINT_KEYWORDS = List.of(
            "给我提示", "提示", "hint", "给点提示", "来点提示", "不会", "思路", "怎么做"
    );

    private final PracticeSessionMapper sessionMapper;
    private final PracticeMessageMapper messageMapper;
    private final PracticeAnswerMapper answerMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final KnowledgeContextBuilder knowledgeContextBuilder;
    private final PracticePromptService promptService;
    private final LlmService llmService;
    private final LearningRecordService learningRecordService;
    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    @Override
    public List<PracticeSessionResponse> list(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getUserId, userId)
                        .orderByDesc(PracticeSession::getUpdatedAt)
                        .orderByDesc(PracticeSession::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PracticeSessionDetailResponse create(Long userId, PracticeSessionCreateRequest request) {
        Subject subject = subjectMapper.selectById(request.subjectId());
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        KnowledgePoint point = null;
        if (request.knowledgePointId() != null) {
            point = knowledgePointMapper.selectById(request.knowledgePointId());
            if (point == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
            }
            if (!request.subjectId().equals(point.getSubjectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选课程");
            }
        }

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setSubjectId(subject.getId());
        session.setKnowledgePointId(point == null ? null : point.getId());
        session.setTitle(StringUtils.hasText(request.title())
                ? request.title().trim()
                : defaultTitle(subject.getName(), point == null ? null : point.getName()));
        session.setState(LEARNING);
        session.setCurrentGrade("C");
        session.setConsecutiveFailures(0);
        session.setCurrentTransfer(0);
        session.setDeleted(0);
        session.setVersion(0);
        sessionMapper.insert(session);

        appendAssistant(
                session,
                "WELCOME",
                promptService.welcomeMessage(
                        subject.getName(),
                        point == null ? null : point.getName()
                ),
                null,
                null
        );
        return detail(userId, session.getId());
    }

    @Override
    public PracticeSessionDetailResponse detail(Long userId, Long sessionId) {
        PracticeSession session = getRequired(userId, sessionId);
        return new PracticeSessionDetailResponse(toResponse(session), loadMessages(sessionId));
    }

    @Override
    public PracticeTurnResponse sendMessage(
            Long userId,
            Long sessionId,
            PracticeMessageRequest request
    ) {
        PracticeSession session = getRequired(userId, sessionId);
        String content = request.content().trim();
        String state = normalizeState(session.getState());
        boolean answering = ASSESSING.equals(state) || TRANSFER_TEST.equals(state);
        boolean hintIntent = isHintIntent(content);

        List<PracticeMessageResponse> added = new ArrayList<>();
        PracticeMessage userMessage = appendUser(
                session,
                content,
                answering && !hintIntent ? "ANSWER" : "QUESTION",
                session.getCurrentQuestionId()
        );
        added.add(toMessageResponse(userMessage));

        PracticeAnswerResponse answer = null;
        if (answering && hintIntent) {
            added.addAll(generateHint(session));
        } else if (answering) {
            PracticeEvaluation evaluation = evaluateAnswer(session, content);
            added.addAll(evaluation.messages());
            answer = evaluation.answer();
        } else if (isPracticeIntent(content)) {
            added.addAll(issueQuestion(session, false));
        } else {
            added.addAll(teach(session, content, userMessage.getId()));
        }

        persistSession(session);
        return new PracticeTurnResponse(toResponse(session), added, answer);
    }

    @Override
    public PracticeTurnResponse startQuestion(Long userId, Long sessionId) {
        PracticeSession session = getRequired(userId, sessionId);
        List<PracticeMessageResponse> added = issueQuestion(session, false);
        persistSession(session);
        return new PracticeTurnResponse(toResponse(session), added, null);
    }

    @Override
    public PracticeTurnResponse requestHint(Long userId, Long sessionId) {
        PracticeSession session = getRequired(userId, sessionId);
        List<PracticeMessageResponse> added = session.getCurrentQuestionId() == null
                ? issueQuestion(session, false)
                : generateHint(session);
        persistSession(session);
        return new PracticeTurnResponse(toResponse(session), added, null);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long sessionId) {
        getRequired(userId, sessionId);
        answerMapper.delete(new LambdaQueryWrapper<PracticeAnswer>()
                .eq(PracticeAnswer::getSessionId, sessionId));
        messageMapper.delete(new LambdaQueryWrapper<PracticeMessage>()
                .eq(PracticeMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    private PracticeEvaluation evaluateAnswer(PracticeSession session, String answerText) {
        Question question = requireCurrentQuestion(session);
        if (question == null) {
            session.setState(LEARNING);
            PracticeMessage message = appendAssistant(
                    session,
                    "TEACHING",
                    "还没有正在作答的题目，先点击“出题练习”吧。",
                    null,
                    null
            );
            return new PracticeEvaluation(List.of(toMessageResponse(message)), null);
        }

        List<QuestionOption> options = loadOptions(question.getId());
        boolean transfer = Integer.valueOf(1).equals(session.getCurrentTransfer());

        return llmService.execute(
                session.getUserId(),
                new LlmChatRequest(
                        promptService.evaluationPrompt(question, options, answerText),
                        "你是严谨但友善的评估专家，只输出结构化 JSON。",
                        null,
                        0.2,
                        null,
                        null
                ),
                "AI_PRACTICE",
                "AI 练习答案评估",
                response -> applyEvaluation(session, question, answerText, transfer, response)
        );
    }

    private PracticeEvaluation applyEvaluation(
            PracticeSession session,
            Question question,
            String answerText,
            boolean transfer,
            LlmChatResponse response
    ) {
        List<PracticeMessageResponse> messages = new ArrayList<>();
        GeneratedPracticeAnswer generated = parseAnswerOrNull(response.content());
        boolean correct;
        String grade;
        String feedback;
        String errorType = null;
        String errorLabel = null;
        String errorDescription = null;
        String improvement = null;

        if (generated != null && StringUtils.hasText(generated.grade())) {
            grade = normalizeGrade(generated.grade());
            correct = generated.correct() == null
                    ? !"C".equals(grade)
                    : generated.correct();
            feedback = textOr(generated.feedback(), "评估完成。");
            if (promptService.isKnownErrorType(generated.errorType())) {
                errorType = generated.errorType().trim().toLowerCase(Locale.ROOT);
                errorLabel = promptService.errorLabel(errorType);
            }
            errorDescription = normalizeNullable(generated.errorDescription());
            improvement = normalizeNullable(generated.improvementSuggestion());
        } else {
            correct = simpleCheck(question, answerText);
            grade = correct ? "A" : "C";
            feedback = "模型没有返回结构化评估，已改用答案比对给出结果。";
        }

        int failures = correct ? 0 : failuresOf(session) + 1;
        session.setConsecutiveFailures(failures);
        session.setCurrentGrade(grade);

        boolean transferPassed = transfer && (correct || "A".equals(grade) || "B".equals(grade));
        PracticeAnswer answerEntity = saveAnswer(
                session,
                question,
                answerText,
                correct,
                grade,
                errorType,
                errorLabel,
                feedback,
                failures,
                transfer
        );
        recordPractice(session, question, grade, transferPassed, transfer);

        String feedbackText = buildFeedbackText(grade, feedback, errorLabel, errorDescription, improvement);

        if (transfer) {
            if (transferPassed) {
                progressService.markTransferPassed(
                        session.getUserId(),
                        session.getSubjectId(),
                        question.getKnowledgePointId()
                );
                session.setState(MASTERED);
                session.setCurrentGrade("A");
                session.setCurrentQuestionId(null);
                session.setCurrentTransfer(0);
                messages.add(toMessageResponse(appendAssistant(
                        session,
                        "RESULT",
                        feedbackText + "\n\n" + promptService.masteredMessage(),
                        question.getId(),
                        response
                )));
            } else {
                session.setState(LEARNING);
                session.setCurrentGrade("B");
                session.setCurrentQuestionId(null);
                session.setCurrentTransfer(0);
                messages.add(toMessageResponse(appendAssistant(
                        session,
                        "RESULT",
                        feedbackText + "\n\n迁移测试未通过，我们先回顾基础讲解，再挑战一次。",
                        question.getId(),
                        response
                )));
            }
        } else if (correct && "A".equals(grade)) {
            messages.add(toMessageResponse(appendAssistant(
                    session,
                    "RESULT",
                    feedbackText,
                    question.getId(),
                    response
            )));
            messages.addAll(issueQuestion(session, true));
        } else if (correct) {
            session.setState(LEARNING);
            session.setCurrentQuestionId(null);
            session.setCurrentTransfer(0);
            messages.add(toMessageResponse(appendAssistant(
                    session,
                    "RESULT",
                    feedbackText + "\n\n继续练习可以再点“出题练习”。",
                    question.getId(),
                    response
            )));
        } else if (failures >= MAX_CONSECUTIVE_FAILURES) {
            session.setState(REMEDIATION);
            session.setCurrentGrade("C");
            session.setCurrentQuestionId(null);
            session.setCurrentTransfer(0);
            messages.add(toMessageResponse(appendAssistant(
                    session,
                    "RESULT",
                    feedbackText,
                    question.getId(),
                    response
            )));
            messages.add(generateRemediation(session, question, failures, errorType));
        } else {
            session.setState(ASSESSING);
            messages.add(toMessageResponse(appendAssistant(
                    session,
                    "RESULT",
                    feedbackText + "\n\n可以再试一次，或者点“分层提示”获取思路。",
                    question.getId(),
                    response
            )));
        }

        return new PracticeEvaluation(messages, toAnswerResponse(answerEntity));
    }

    private List<PracticeMessageResponse> issueQuestion(PracticeSession session, boolean transfer) {
        Question question = pickQuestion(session, transfer);
        if (question == null) {
            if (transfer) {
                session.setState(MASTERED);
                session.setCurrentGrade("A");
                session.setCurrentQuestionId(null);
                session.setCurrentTransfer(0);
                // 题库中没有迁移题时同样要把「已掌握」写回学习进度，
                // 否则练习页显示已掌握、学习概览仍停在未掌握（审计 B9）。
                progressService.markTransferPassed(
                        session.getUserId(),
                        session.getSubjectId(),
                        session.getKnowledgePointId()
                );
                return List.of(toMessageResponse(appendAssistant(
                        session,
                        "RESULT",
                        promptService.masteredMessage(),
                        null,
                        null
                )));
            }
            return List.of(toMessageResponse(appendAssistant(
                    session,
                    "TEACHING",
                    "当前课程还没有可用练习题，请等待教师补充题库，或者直接向我提问。",
                    null,
                    null
            )));
        }

        session.setCurrentQuestionId(question.getId());
        session.setCurrentTransfer(transfer ? 1 : 0);
        session.setState(transfer ? TRANSFER_TEST : ASSESSING);

        List<QuestionOption> options = loadOptions(question.getId());
        String questionText = promptService.formatQuestion(question, options);
        String content = transfer
                ? promptService.transferIntro() + questionText
                + "\n\n请认真思考后作答，需要思路可以点“分层提示”。"
                : "下面这道题用来确认你的理解程度。\n\n" + questionText
                + "\n\n提交你的答案后，我会给出评级和反馈。";

        return List.of(toMessageResponse(appendAssistant(
                session,
                transfer ? "TRANSFER" : "QUESTION",
                content,
                question.getId(),
                null
        )));
    }

    private List<PracticeMessageResponse> generateHint(PracticeSession session) {
        Question question = requireCurrentQuestion(session);
        if (question == null) {
            return List.of(toMessageResponse(appendAssistant(
                    session,
                    "TEACHING",
                    "还没有正在作答的题目，先点“出题练习”吧。",
                    null,
                    null
            )));
        }
        List<QuestionOption> options = loadOptions(question.getId());
        String knowledgeContext = knowledgeContextBuilder.build(
                session.getSubjectId(),
                question.getKnowledgePointId()
        );
        PracticeMessageResponse hint = llmService.execute(
                session.getUserId(),
                new LlmChatRequest(
                        promptService.hintPrompt(
                                question,
                                options,
                                knowledgeContext,
                                session.getCurrentGrade()
                        ),
                        "你是分层提示助手，只给提示，不给最终答案。",
                        null,
                        0.5,
                        null,
                        null
                ),
                "AI_PRACTICE",
                "AI 练习分层提示",
                response -> toMessageResponse(appendAssistant(
                        session,
                        "HINT",
                        response.content() + "\n\n可以继续作答。",
                        question.getId(),
                        response
                ))
        );
        return List.of(hint);
    }

    private List<PracticeMessageResponse> teach(
            PracticeSession session,
            String content,
            Long currentMessageId
    ) {
        Subject subject = subjectMapper.selectById(session.getSubjectId());
        String subjectName = subject == null ? "课程" : subject.getName();
        String knowledgeContext = knowledgeContextBuilder.build(
                session.getSubjectId(),
                session.getKnowledgePointId()
        );
        String systemPrompt = promptService.tutorSystemPrompt(
                subjectName,
                knowledgeContext,
                normalizeGrade(session.getCurrentGrade())
        );
        String history = recentDialogue(session.getId(), currentMessageId);
        if (StringUtils.hasText(history)) {
            systemPrompt = systemPrompt + "\n\n## 最近对话\n" + history;
        }

        PracticeMessageResponse teaching = llmService.execute(
                session.getUserId(),
                new LlmChatRequest(content, systemPrompt, null, 0.7, null, null),
                "AI_PRACTICE",
                "AI 练习辅导讲解",
                response -> {
                    if (REMEDIATION.equals(normalizeState(session.getState()))) {
                        session.setState(LEARNING);
                        session.setConsecutiveFailures(0);
                    }
                    return toMessageResponse(appendAssistant(
                            session,
                            "TEACHING",
                            response.content(),
                            null,
                            response
                    ));
                }
        );
        return List.of(teaching);
    }

    private PracticeMessageResponse generateRemediation(
            PracticeSession session,
            Question question,
            int failures,
            String errorType
    ) {
        Subject subject = subjectMapper.selectById(session.getSubjectId());
        String subjectName = subject == null ? "课程" : subject.getName();
        KnowledgePoint point = knowledgePointMapper.selectById(question.getKnowledgePointId());
        String topic = point == null ? "当前知识点" : point.getName();

        return llmService.execute(
                session.getUserId(),
                new LlmChatRequest(
                        promptService.remediationPrompt(
                                subjectName,
                                topic,
                                failures,
                                errorType,
                                recentErrorSummary(session.getId()),
                                normalizeGrade(session.getCurrentGrade())
                        ),
                        "你是补救教学助手，输出可执行的讲解与递进练习。",
                        null,
                        0.5,
                        null,
                        null
                ),
                "AI_PRACTICE",
                "AI 练习补救教学",
                response -> toMessageResponse(appendAssistant(
                        session,
                        "REMEDIATION",
                        response.content(),
                        null,
                        response
                ))
        );
    }

    private Question pickQuestion(PracticeSession session, boolean transfer) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getIsTransfer, transfer ? 1 : 0);
        if (session.getKnowledgePointId() != null) {
            wrapper.eq(Question::getKnowledgePointId, session.getKnowledgePointId());
        } else {
            wrapper.eq(Question::getSubjectId, session.getSubjectId());
        }
        wrapper.last("LIMIT " + QUESTION_POOL_LIMIT);
        List<Question> questions = questionMapper.selectList(wrapper);
        if (questions.isEmpty()) {
            return null;
        }
        return questions.get(ThreadLocalRandom.current().nextInt(questions.size()));
    }

    private Question requireCurrentQuestion(PracticeSession session) {
        if (session.getCurrentQuestionId() == null) {
            return null;
        }
        return questionMapper.selectById(session.getCurrentQuestionId());
    }

    private List<QuestionOption> loadOptions(Long questionId) {
        return optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder)
                .orderByAsc(QuestionOption::getId));
    }

    private PracticeAnswer saveAnswer(
            PracticeSession session,
            Question question,
            String answerText,
            boolean correct,
            String grade,
            String errorType,
            String errorLabel,
            String feedback,
            int failures,
            boolean transfer
    ) {
        PracticeAnswer answer = new PracticeAnswer();
        answer.setSessionId(session.getId());
        answer.setSubjectId(session.getSubjectId());
        answer.setKnowledgePointId(question.getKnowledgePointId());
        answer.setQuestionId(question.getId());
        answer.setIsTransfer(transfer ? 1 : 0);
        answer.setAnswerContent(truncate(answerText, 2000));
        answer.setIsCorrect(correct ? 1 : 0);
        answer.setGrade(grade);
        answer.setErrorType(errorType);
        answer.setErrorLabel(errorLabel);
        answer.setFeedback(truncate(feedback, 2000));
        answer.setConsecutiveFailures(failures);
        answer.setAnsweredAt(LocalDateTime.now());
        answer.setDeleted(0);
        answer.setVersion(0);
        answerMapper.insert(answer);
        return answer;
    }

    private void recordPractice(
            PracticeSession session,
            Question question,
            String grade,
            boolean transferPassed,
            boolean transfer
    ) {
        learningRecordService.create(session.getUserId(), new LearningRecordSaveRequest(
                session.getSubjectId(),
                question.getKnowledgePointId(),
                null,
                "PRACTICE",
                0,
                gradeToScore(grade),
                transferPassed,
                (transfer ? "迁移测试" : "练习评估") + "：等级 " + grade,
                null
        ));
    }

    private BigDecimal gradeToScore(String grade) {
        return switch (grade) {
            case "A" -> BigDecimal.valueOf(90);
            case "B" -> BigDecimal.valueOf(75);
            default -> BigDecimal.valueOf(50);
        };
    }

    private String buildFeedbackText(
            String grade,
            String feedback,
            String errorLabel,
            String errorDescription,
            String improvement
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("评估等级：").append(grade).append('\n');
        builder.append(feedback.trim());
        if (StringUtils.hasText(errorLabel)) {
            builder.append("\n\n错误类型：").append(errorLabel);
            if (StringUtils.hasText(errorDescription)) {
                builder.append("（").append(errorDescription.trim()).append("）");
            }
        }
        if (StringUtils.hasText(improvement)) {
            builder.append("\n改进建议：").append(improvement.trim());
        }
        return builder.toString();
    }

    private String recentDialogue(Long sessionId, Long excludeMessageId) {
        List<PracticeMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<PracticeMessage>()
                        .eq(PracticeMessage::getSessionId, sessionId)
                        .orderByDesc(PracticeMessage::getId)
                        .last("LIMIT " + (HISTORY_MESSAGE_LIMIT + 1))
        );
        List<PracticeMessage> ordered = new ArrayList<>(messages);
        ordered.removeIf(message -> message.getId().equals(excludeMessageId));
        java.util.Collections.reverse(ordered);
        return ordered.stream()
                .limit(HISTORY_MESSAGE_LIMIT)
                .map(message -> ("user".equals(message.getRole()) ? "学生：" : "助手：")
                        + truncate(message.getContent(), HISTORY_SNIPPET_LIMIT))
                .collect(Collectors.joining("\n"));
    }

    private String recentErrorSummary(Long sessionId) {
        List<PracticeAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<PracticeAnswer>()
                        .eq(PracticeAnswer::getSessionId, sessionId)
                        .eq(PracticeAnswer::getIsCorrect, 0)
                        .orderByDesc(PracticeAnswer::getId)
                        .last("LIMIT 3")
        );
        if (answers.isEmpty()) {
            return "";
        }
        return answers.stream()
                .map(answer -> "- " + truncate(firstLine(answer.getFeedback()), 160))
                .collect(Collectors.joining("\n"));
    }

    private boolean simpleCheck(Question question, String studentAnswer) {
        String correct = normalizeText(question.getCorrectAnswer());
        String student = normalizeText(studentAnswer);
        if (student.isEmpty()) {
            return false;
        }
        String type = question.getQuestionType() == null
                ? ""
                : question.getQuestionType().trim().toUpperCase(Locale.ROOT);

        if ("CHOICE".equals(type)) {
            return correct.contains(student) || student.contains(correct);
        }
        if ("JUDGMENT".equals(type)) {
            List<String> truthy = List.of("正确", "对", "true", "yes", "√");
            List<String> falsy = List.of("错误", "错", "false", "no", "×");
            boolean correctIsTrue = truthy.stream().anyMatch(correct::contains);
            return correctIsTrue
                    ? truthy.stream().anyMatch(student::contains)
                    : falsy.stream().anyMatch(student::contains);
        }
        List<String> keywords = Arrays.stream(correct.split("[\\s,，、;；]+"))
                .filter(keyword -> !keyword.isEmpty())
                .toList();
        if (keywords.isEmpty()) {
            return false;
        }
        long hits = keywords.stream().filter(student::contains).count();
        return hits * 2 >= keywords.size();
    }

    private GeneratedPracticeAnswer parseAnswerOrNull(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String cleaned = stripCodeFence(content);
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    cleaned.substring(start, end + 1),
                    GeneratedPracticeAnswer.class
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private PracticeSession getRequired(Long userId, Long sessionId) {
        PracticeSession session = sessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "练习会话不存在");
        }
        return session;
    }

    private List<PracticeMessageResponse> loadMessages(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<PracticeMessage>()
                        .eq(PracticeMessage::getSessionId, sessionId)
                        .orderByAsc(PracticeMessage::getId))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private void persistSession(PracticeSession session) {
        LocalDateTime now = LocalDateTime.now();
        sessionMapper.update(null, new LambdaUpdateWrapper<PracticeSession>()
                .eq(PracticeSession::getId, session.getId())
                .set(PracticeSession::getState, normalizeState(session.getState()))
                .set(PracticeSession::getCurrentGrade, normalizeGrade(session.getCurrentGrade()))
                .set(
                        PracticeSession::getConsecutiveFailures,
                        session.getConsecutiveFailures() == null ? 0 : session.getConsecutiveFailures()
                )
                .set(PracticeSession::getCurrentQuestionId, session.getCurrentQuestionId())
                .set(
                        PracticeSession::getCurrentTransfer,
                        Integer.valueOf(1).equals(session.getCurrentTransfer()) ? 1 : 0
                )
                .set(PracticeSession::getUpdatedAt, now));
        session.setUpdatedAt(now);
        session.setState(normalizeState(session.getState()));
        session.setCurrentGrade(normalizeGrade(session.getCurrentGrade()));
    }

    private PracticeMessage appendUser(
            PracticeSession session,
            String content,
            String messageType,
            Long questionId
    ) {
        return insertMessage(session.getId(), "user", messageType, content, questionId, null);
    }

    private PracticeMessage appendAssistant(
            PracticeSession session,
            String messageType,
            String content,
            Long questionId,
            LlmChatResponse response
    ) {
        return insertMessage(session.getId(), "assistant", messageType, content, questionId, response);
    }

    private PracticeMessage insertMessage(
            Long sessionId,
            String role,
            String messageType,
            String content,
            Long questionId,
            LlmChatResponse response
    ) {
        PracticeMessage message = new PracticeMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setQuestionId(questionId);
        if (response != null) {
            message.setProviderCode(response.providerCode());
            message.setModel(response.model());
        }
        message.setCreatedAt(LocalDateTime.now());
        message.setDeleted(0);
        message.setVersion(0);
        messageMapper.insert(message);
        return message;
    }

    private PracticeSessionResponse toResponse(PracticeSession session) {
        Subject subject = subjectMapper.selectById(session.getSubjectId());
        KnowledgePoint point = session.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(session.getKnowledgePointId());
        long messageCount = messageMapper.selectCount(new LambdaQueryWrapper<PracticeMessage>()
                .eq(PracticeMessage::getSessionId, session.getId()));
        return new PracticeSessionResponse(
                session.getId(),
                session.getSubjectId(),
                subject == null ? null : subject.getName(),
                session.getKnowledgePointId(),
                point == null ? null : point.getName(),
                session.getTitle(),
                normalizeState(session.getState()),
                normalizeGrade(session.getCurrentGrade()),
                failuresOf(session),
                session.getCurrentQuestionId(),
                Integer.valueOf(1).equals(session.getCurrentTransfer()),
                messageCount,
                session.getUpdatedAt()
        );
    }

    private PracticeMessageResponse toMessageResponse(PracticeMessage message) {
        return new PracticeMessageResponse(
                message.getId(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                message.getQuestionId(),
                message.getCreatedAt()
        );
    }

    private PracticeAnswerResponse toAnswerResponse(PracticeAnswer answer) {
        return new PracticeAnswerResponse(
                answer.getId(),
                answer.getQuestionId(),
                Integer.valueOf(1).equals(answer.getIsTransfer()),
                Integer.valueOf(1).equals(answer.getIsCorrect()),
                answer.getGrade(),
                answer.getErrorType(),
                answer.getErrorLabel(),
                answer.getFeedback(),
                answer.getAnsweredAt()
        );
    }

    private boolean isPracticeIntent(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return PRACTICE_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean isHintIntent(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return HINT_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String defaultTitle(String subjectName, String knowledgePointName) {
        return StringUtils.hasText(knowledgePointName)
                ? subjectName + " · " + knowledgePointName
                : subjectName + "练习";
    }

    private String normalizeState(String state) {
        if (!StringUtils.hasText(state)) {
            return LEARNING;
        }
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        return List.of(LEARNING, ASSESSING, TRANSFER_TEST, MASTERED, REMEDIATION).contains(normalized)
                ? normalized
                : LEARNING;
    }

    private String normalizeGrade(String grade) {
        if (!StringUtils.hasText(grade)) {
            return "C";
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return List.of("A", "B", "C").contains(normalized) ? normalized : "C";
    }

    private int failuresOf(PracticeSession session) {
        return session.getConsecutiveFailures() == null ? 0 : session.getConsecutiveFailures();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String firstLine(String value) {
        if (!StringUtils.hasText(value)) {
            return "无反馈内容";
        }
        int index = value.indexOf('\n');
        return index < 0 ? value.trim() : value.substring(0, index).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String stripCodeFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private record PracticeEvaluation(
            List<PracticeMessageResponse> messages,
            PracticeAnswerResponse answer
    ) {
    }
}
