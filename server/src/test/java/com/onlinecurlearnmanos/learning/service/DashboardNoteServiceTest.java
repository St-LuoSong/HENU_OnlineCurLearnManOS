package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteSaveRequest;
import com.onlinecurlearnmanos.learning.entity.DashboardNote;
import com.onlinecurlearnmanos.learning.mapper.DashboardNoteMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 日历便签的归属、区间与标记规范化回归：便签必须只属于创建者，越权按「不存在」处理。
 */
@ExtendWith(MockitoExtension.class)
class DashboardNoteServiceTest {

    @Mock
    private DashboardNoteMapper noteMapper;

    @InjectMocks
    private DashboardNoteServiceImpl service;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(DashboardNote.class);
    }

    @Test
    void createTrimsContentAndFallsBackToDefaultTone() {
        given(noteMapper.insert(any(DashboardNote.class))).willAnswer(invocation -> {
            DashboardNote note = invocation.getArgument(0);
            note.setId(7L);
            return 1;
        });

        DashboardNoteResponse response = service.create(
                31L,
                new DashboardNoteSaveRequest(LocalDate.of(2026, 9, 21), "  复习极限  ", "unknown")
        );

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.content()).isEqualTo("复习极限");
        assertThat(response.tone()).isEqualTo("DEFAULT");
    }

    @Test
    void createNormalizesSupportedTone() {
        given(noteMapper.insert(any(DashboardNote.class))).willReturn(1);

        DashboardNoteResponse response = service.create(
                31L,
                new DashboardNoteSaveRequest(LocalDate.of(2026, 9, 21), "期中考试", "important")
        );

        assertThat(response.tone()).isEqualTo("IMPORTANT");
    }

    @Test
    void updateRejectsAnotherUsersNoteAsNotFound() {
        given(noteMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.update(
                9L,
                31L,
                new DashboardNoteSaveRequest(LocalDate.of(2026, 9, 21), "改内容", "DEFAULT")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void deleteRejectsAnotherUsersNoteAsNotFound() {
        given(noteMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.delete(9L, 31L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void listRejectsInvertedRange() {
        assertThatThrownBy(() -> service.list(
                31L,
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 1)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void listRejectsRangeLongerThanAYear() {
        assertThatThrownBy(() -> service.list(
                31L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 12, 31)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("366");
    }

    @Test
    void listReturnsOwnedNotesForRequestedWindow() {
        DashboardNote note = new DashboardNote();
        note.setId(3L);
        note.setUserId(31L);
        note.setNoteDate(LocalDate.of(2026, 9, 21));
        note.setContent("复习极限");
        note.setTone("IMPORTANT");
        given(noteMapper.selectList(any())).willReturn(List.of(note));

        List<DashboardNoteResponse> notes = service.list(
                31L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );

        assertThat(notes).singleElement().satisfies(item -> {
            assertThat(item.noteDate()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(item.content()).isEqualTo("复习极限");
            assertThat(item.tone()).isEqualTo("IMPORTANT");
        });
    }
}

