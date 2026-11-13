package com.onlinecurlearnmanos.assistant.service;

import com.onlinecurlearnmanos.assistant.dto.AssistantSessionResponse;
import com.onlinecurlearnmanos.chat.dto.ChatSessionResponse;
import com.onlinecurlearnmanos.chat.service.ChatService;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionResponse;
import com.onlinecurlearnmanos.practice.service.PracticeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AssistantAggregationTest {

    @Mock
    private ChatService chatService;

    @Mock
    private PracticeService practiceService;

    @InjectMocks
    private AssistantSessionService service;

    @Test
    void mergesChatAndPracticeSessionsAndSortsByUpdatedAt() {
        LocalDateTime chatAt = LocalDateTime.of(2026, 9, 21, 10, 0);
        LocalDateTime practiceAt = LocalDateTime.of(2026, 9, 21, 11, 0);
        given(chatService.listSessions(31L)).willReturn(List.of(new ChatSessionResponse(
                1L,
                10L,
                "高等数学",
                100L,
                "导数",
                "导数问答",
                null,
                "ACTIVE",
                4,
                "导数的几何意义是什么？",
                chatAt
        )));
        given(practiceService.list(31L)).willReturn(List.of(new PracticeSessionResponse(
                2L,
                10L,
                "高等数学",
                100L,
                "导数",
                "导数练习",
                "ASSESSING",
                "B",
                1,
                200L,
                false,
                6,
                practiceAt
        )));

        List<AssistantSessionResponse> result = service.list(31L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sourceType()).isEqualTo("PRACTICE");
        assertThat(result.get(0).sessionMode()).isEqualTo("practice");
        assertThat(result.get(0).currentGrade()).isEqualTo("B");
        assertThat(result.get(0).preview()).isEqualTo("等级 B · ASSESSING");
        assertThat(result.get(1).sourceType()).isEqualTo("CHAT");
        assertThat(result.get(1).sessionMode()).isEqualTo("chat");
        assertThat(result.get(1).preview()).isEqualTo("导数的几何意义是什么？");
    }

    @Test
    void keepsPracticeTransferMetadata() {
        given(chatService.listSessions(31L)).willReturn(List.of());
        given(practiceService.list(31L)).willReturn(List.of(new PracticeSessionResponse(
                3L,
                20L,
                "线性代数",
                210L,
                "特征值",
                "特征值迁移",
                "TRANSFER_TEST",
                "A",
                0,
                301L,
                true,
                9,
                LocalDateTime.of(2026, 9, 21, 12, 0)
        )));

        AssistantSessionResponse session = service.list(31L).get(0);

        assertThat(session.transferQuestion()).isTrue();
        assertThat(session.currentQuestionId()).isEqualTo(301L);
        assertThat(session.messageCount()).isEqualTo(9);
    }
}
