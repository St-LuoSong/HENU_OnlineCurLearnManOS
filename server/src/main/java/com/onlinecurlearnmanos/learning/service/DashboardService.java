package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.DashboardSummaryResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardOverviewResponse;

import java.util.List;

public interface DashboardService {

    DashboardSummaryResponse summary(Long userId);

    DashboardOverviewResponse overview(Long userId, List<String> roles);
}
