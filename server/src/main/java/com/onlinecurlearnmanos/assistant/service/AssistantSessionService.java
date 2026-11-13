package com.onlinecurlearnmanos.assistant.service;

import com.onlinecurlearnmanos.assistant.dto.AssistantSessionResponse;
import com.onlinecurlearnmanos.chat.dto.ChatSessionResponse;
import com.onlinecurlearnmanos.chat.service.ChatService;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionResponse;
import com.onlinecurlearnmanos.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssistantSessionService {

    private final ChatService chatService;
    private final PracticeService practiceService;

    public List<AssistantSessionResponse> list(Long userId) {
        List<AssistantSessionResponse> sessions = new ArrayList<>();
        for (ChatSessionResponse chat : chatService.listSessions(userId)) {
            sessions.add(new AssistantSessionResponse(
                    chat.id(),
                    "CHAT",
                    "chat",
                    chat.subjectId(),
                    chat.subjectName(),
                    chat.knowledgePointId(),
                    chat.knowledgePointName(),
                    chat.title(),
                    chat.status(),
                    null,
                    chat.messageCount(),
                    chat.lastMessage(),
                    false,
                    null,
                    chat.updatedAt()
            ));
        }
        for (PracticeSessionResponse practice : practiceService.list(userId)) {
            sessions.add(new AssistantSessionResponse(
                    practice.id(),
                    "PRACTICE",
                    "practice",
                    practice.subjectId(),
                    practice.subjectName(),
                    practice.knowledgePointId(),
                    practice.knowledgePointName(),
                    practice.title(),
                    practice.state(),
                    practice.currentGrade(),
                    practice.messageCount(),
                    "等级 " + practice.currentGrade() + " · " + practice.state(),
                    practice.transferQuestion(),
                    practice.currentQuestionId(),
                    practice.updatedAt()
            ));
        }
        sessions.sort(Comparator.comparing(
                AssistantSessionResponse::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        return sessions;
    }
}
