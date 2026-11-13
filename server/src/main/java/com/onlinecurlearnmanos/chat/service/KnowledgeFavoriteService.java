package com.onlinecurlearnmanos.chat.service;

import com.onlinecurlearnmanos.chat.dto.KnowledgeFavoriteRequest;
import com.onlinecurlearnmanos.chat.dto.KnowledgeFavoriteResponse;
import com.onlinecurlearnmanos.chat.entity.ChatMessage;
import com.onlinecurlearnmanos.chat.entity.ChatSession;
import com.onlinecurlearnmanos.chat.entity.KnowledgeFavorite;
import com.onlinecurlearnmanos.chat.mapper.ChatMessageMapper;
import com.onlinecurlearnmanos.chat.mapper.ChatSessionMapper;
import com.onlinecurlearnmanos.chat.mapper.KnowledgeFavoriteMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeFavoriteService {

    private final KnowledgeFavoriteMapper favoriteMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    @Transactional
    public KnowledgeFavoriteResponse add(Long userId, KnowledgeFavoriteRequest request) {
        ChatMessage message = messageMapper.selectById(request.messageId());
        if (message == null || !"assistant".equals(message.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "可收藏的回答不存在");
        }
        ChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, message.getSessionId())
                .eq(ChatSession::getUserId, userId)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对话不存在");
        }
        if (favoriteMapper.selectCount(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, userId)
                .eq(KnowledgeFavorite::getMessageId, request.messageId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "这条回答已经收藏过了");
        }
        ChatMessage question = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .eq(ChatMessage::getRole, "user")
                .lt(ChatMessage::getId, message.getId())
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));

        KnowledgeFavorite favorite = new KnowledgeFavorite();
        favorite.setUserId(userId);
        favorite.setMessageId(message.getId());
        favorite.setSessionId(session.getId());
        favorite.setSubjectId(session.getSubjectId());
        favorite.setKnowledgePointId(session.getKnowledgePointId());
        favorite.setQuestion(question == null ? null : question.getContent());
        favorite.setAnswer(message.getContent());
        favorite.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        try {
            favoriteMapper.insert(favorite);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "这条回答已经收藏过了");
        }
        return toResponse(favorite);
    }

    public PageResult<KnowledgeFavoriteResponse> list(
            Long userId,
            Long subjectId,
            String keyword,
            long page,
            long size
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<KnowledgeFavorite> query = new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, userId)
                .orderByDesc(KnowledgeFavorite::getCreatedAt);
        if (subjectId != null) {
            query.eq(KnowledgeFavorite::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(KnowledgeFavorite::getQuestion, text)
                    .or()
                    .like(KnowledgeFavorite::getAnswer, text)
                    .or()
                    .like(KnowledgeFavorite::getNote, text));
        }
        Page<KnowledgeFavorite> result = favoriteMapper.selectPage(
                new Page<>(safePage, safeSize),
                query
        );
        return new PageResult<>(
                result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    public List<Long> listMessageIds(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                        .eq(KnowledgeFavorite::getUserId, userId))
                .stream()
                .map(KnowledgeFavorite::getMessageId)
                .toList();
    }

    @Transactional
    public KnowledgeFavoriteResponse updateNote(Long userId, Long id, String note) {
        KnowledgeFavorite favorite = getRequired(userId, id);
        favorite.setNote(StringUtils.hasText(note) ? note.trim() : null);
        favoriteMapper.updateById(favorite);
        return toResponse(getRequired(userId, id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        getRequired(userId, id);
        favoriteMapper.deleteById(id);
    }

    @Transactional
    public void deleteByMessage(Long userId, Long messageId) {
        KnowledgeFavorite favorite = favoriteMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeFavorite>()
                        .eq(KnowledgeFavorite::getUserId, userId)
                        .eq(KnowledgeFavorite::getMessageId, messageId)
                        .last("LIMIT 1")
        );
        if (favorite != null) {
            favoriteMapper.deleteById(favorite.getId());
        }
    }

    private KnowledgeFavorite getRequired(Long userId, Long id) {
        KnowledgeFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getId, id)
                .eq(KnowledgeFavorite::getUserId, userId)
                .last("LIMIT 1"));
        if (favorite == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收藏不存在");
        }
        return favorite;
    }

    private KnowledgeFavoriteResponse toResponse(KnowledgeFavorite favorite) {
        Subject subject = favorite.getSubjectId() == null
                ? null
                : subjectMapper.selectById(favorite.getSubjectId());
        KnowledgePoint point = favorite.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(favorite.getKnowledgePointId());
        return new KnowledgeFavoriteResponse(
                favorite.getId(),
                favorite.getMessageId(),
                favorite.getSessionId(),
                favorite.getSubjectId(),
                subject == null ? null : subject.getName(),
                favorite.getKnowledgePointId(),
                point == null ? null : point.getName(),
                favorite.getQuestion(),
                favorite.getAnswer(),
                favorite.getNote(),
                favorite.getCreatedAt()
        );
    }
}
