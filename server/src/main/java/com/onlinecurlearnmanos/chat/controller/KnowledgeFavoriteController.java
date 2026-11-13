package com.onlinecurlearnmanos.chat.controller;

import com.onlinecurlearnmanos.chat.dto.KnowledgeFavoriteNoteRequest;
import com.onlinecurlearnmanos.chat.dto.KnowledgeFavoriteRequest;
import com.onlinecurlearnmanos.chat.dto.KnowledgeFavoriteResponse;
import com.onlinecurlearnmanos.chat.service.KnowledgeFavoriteService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-favorites")
@RequiredArgsConstructor
public class KnowledgeFavoriteController {

    private final KnowledgeFavoriteService favoriteService;

    @GetMapping
    public ApiResponse<PageResult<KnowledgeFavoriteResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "8") long size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                favoriteService.list(principal.getId(), subjectId, keyword, page, size)
        );
    }

    @GetMapping("/ids")
    public ApiResponse<List<Long>> messageIds(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(favoriteService.listMessageIds(principal.getId()));
    }

    @PostMapping
    public ApiResponse<KnowledgeFavoriteResponse> add(
            @Valid @RequestBody KnowledgeFavoriteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(favoriteService.add(principal.getId(), request));
    }

    @PutMapping("/{id}/note")
    public ApiResponse<KnowledgeFavoriteResponse> updateNote(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeFavoriteNoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                favoriteService.updateNote(principal.getId(), id, request.note())
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        favoriteService.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Void> deleteByMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        favoriteService.deleteByMessage(principal.getId(), messageId);
        return ApiResponse.success(null);
    }
}
