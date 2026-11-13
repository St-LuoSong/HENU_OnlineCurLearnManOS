package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteSaveRequest;
import com.onlinecurlearnmanos.learning.entity.DashboardNote;
import com.onlinecurlearnmanos.learning.mapper.DashboardNoteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardNoteServiceImpl implements DashboardNoteService {

    /** 单次查询的最大跨度，避免前端传入无限区间把整表拖出来。 */
    private static final long MAX_RANGE_DAYS = 366L;
    private static final String DEFAULT_TONE = "DEFAULT";
    private static final Set<String> ALLOWED_TONES = Set.of("DEFAULT", "IMPORTANT", "RELAX");

    private final DashboardNoteMapper noteMapper;

    @Override
    public List<DashboardNoteResponse> list(Long userId, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? start.with(TemporalAdjusters.lastDayOfMonth()) : to;
        assertRange(start, end);
        return noteMapper.selectList(new LambdaQueryWrapper<DashboardNote>()
                        .eq(DashboardNote::getUserId, userId)
                        .between(DashboardNote::getNoteDate, start, end)
                        .orderByAsc(DashboardNote::getNoteDate)
                        .orderByAsc(DashboardNote::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DashboardNoteResponse create(Long userId, DashboardNoteSaveRequest request) {
        DashboardNote note = new DashboardNote();
        note.setUserId(userId);
        note.setNoteDate(request.noteDate());
        note.setContent(request.content().trim());
        note.setTone(normalizeTone(request.tone()));
        note.setDeleted(0);
        note.setVersion(0);
        noteMapper.insert(note);
        return toResponse(note);
    }

    @Override
    @Transactional
    public DashboardNoteResponse update(Long id, Long userId, DashboardNoteSaveRequest request) {
        DashboardNote note = getOwnedNote(id, userId);
        note.setNoteDate(request.noteDate());
        note.setContent(request.content().trim());
        note.setTone(normalizeTone(request.tone()));
        noteMapper.updateById(note);
        return toResponse(note);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        getOwnedNote(id, userId);
        noteMapper.deleteById(id);
    }

    /**
     * 越权访问统一按「不存在」处理，避免用 403 反推出他人便签 id。
     */
    private DashboardNote getOwnedNote(Long id, Long userId) {
        DashboardNote note = noteMapper.selectOne(new LambdaQueryWrapper<DashboardNote>()
                .eq(DashboardNote::getId, id)
                .eq(DashboardNote::getUserId, userId)
                .last("LIMIT 1"));
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "便签不存在");
        }
        return note;
    }

    private void assertRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "便签查询区间不能超过 366 天");
        }
    }

    private String normalizeTone(String tone) {
        if (!StringUtils.hasText(tone)) {
            return DEFAULT_TONE;
        }
        String normalized = tone.trim().toUpperCase();
        return ALLOWED_TONES.contains(normalized) ? normalized : DEFAULT_TONE;
    }

    private DashboardNoteResponse toResponse(DashboardNote note) {
        return new DashboardNoteResponse(
                note.getId(),
                note.getNoteDate(),
                note.getContent(),
                note.getTone(),
                note.getUpdatedAt()
        );
    }
}

