package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteSaveRequest;
import com.onlinecurlearnmanos.learning.dto.DashboardOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardSummaryResponse;
import com.onlinecurlearnmanos.learning.dto.CalendarEventResponse;
import com.onlinecurlearnmanos.learning.service.CalendarEventService;
import com.onlinecurlearnmanos.learning.service.DashboardNoteService;
import com.onlinecurlearnmanos.learning.service.DashboardService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CalendarEventService calendarEventService;
    private final DashboardNoteService dashboardNoteService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(dashboardService.summary(principal.getId()));
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                dashboardService.overview(principal.getId(), principal.getRoles())
        );
    }

    @GetMapping("/calendar")
    public ApiResponse<List<CalendarEventResponse>> calendar(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(calendarEventService.events(
                principal.getId(),
                principal.getRoles(),
                from,
                to
        ));
    }

    @GetMapping("/notes")
    public ApiResponse<List<DashboardNoteResponse>> notes(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(dashboardNoteService.list(principal.getId(), from, to));
    }

    @PostMapping("/notes")
    public ApiResponse<DashboardNoteResponse> createNote(
            @Valid @RequestBody DashboardNoteSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(dashboardNoteService.create(principal.getId(), request));
    }

    @PutMapping("/notes/{id}")
    public ApiResponse<DashboardNoteResponse> updateNote(
            @PathVariable Long id,
            @Valid @RequestBody DashboardNoteSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(dashboardNoteService.update(id, principal.getId(), request));
    }

    @DeleteMapping("/notes/{id}")
    public ApiResponse<Void> deleteNote(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        dashboardNoteService.delete(id, principal.getId());
        return ApiResponse.success(null);
    }
}
