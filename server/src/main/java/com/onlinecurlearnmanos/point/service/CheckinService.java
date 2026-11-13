package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.point.dto.CheckinResultResponse;
import com.onlinecurlearnmanos.point.dto.CheckinStatusResponse;

public interface CheckinService {

    CheckinStatusResponse status(Long userId);

    CheckinResultResponse checkin(Long userId);
}
