package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.career.dto.CareerConsentRequest;
import com.onlinecurlearnmanos.career.dto.CareerDataPreviewResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileSaveRequest;
import com.onlinecurlearnmanos.career.entity.CareerProfile;
import com.onlinecurlearnmanos.career.mapper.CareerProfileMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CareerProfileService {

    private static final int MAX_TAGS = 12;
    private static final int MAX_TAG_LENGTH = 32;

    private final CareerProfileMapper profileMapper;
    private final CareerDataCollector dataCollector;

    public CareerProfileResponse get(Long userId) {
        return toResponse(loadOrDefault(userId));
    }

    @Transactional
    public CareerProfileResponse save(Long userId, CareerProfileSaveRequest request) {
        CareerProfile profile = getOrCreate(userId);
        profileMapper.update(null, new LambdaUpdateWrapper<CareerProfile>()
                .eq(CareerProfile::getId, profile.getId())
                .set(CareerProfile::getMajor, normalizeNullable(request.major()))
                .set(CareerProfile::getGradeLevel, normalizeNullable(request.gradeLevel()))
                .set(CareerProfile::getWeeklyHours, request.weeklyHours())
                .set(CareerProfile::getInterestTags, joinTags(request.interestTags()))
                .set(CareerProfile::getStrengthNotes, normalizeNullable(request.strengthNotes()))
                .set(CareerProfile::getCareerGoal, normalizeNullable(request.careerGoal())));
        return toResponse(getRequired(userId));
    }

    @Transactional
    public CareerProfileResponse updateConsent(Long userId, CareerConsentRequest request) {
        CareerProfile profile = getOrCreate(userId);
        LocalDateTime now = LocalDateTime.now();
        profileMapper.update(null, new LambdaUpdateWrapper<CareerProfile>()
                .eq(CareerProfile::getId, profile.getId())
                .set(CareerProfile::getConsentGranted, request.granted() ? 1 : 0)
                .set(CareerProfile::getConsentVersion, CareerConsentPolicy.CONSENT_VERSION)
                .set(CareerProfile::getIncludeLearningRecords, request.includeLearningRecords() ? 1 : 0)
                .set(CareerProfile::getIncludeGoals, request.includeGoals() ? 1 : 0)
                .set(CareerProfile::getIncludeGrades, request.includeGrades() ? 1 : 0)
                .set(CareerProfile::getConsentedAt, request.granted() ? now : profile.getConsentedAt())
                .set(CareerProfile::getRevokedAt, request.granted() ? null : now));
        return toResponse(getRequired(userId));
    }

    public CareerDataPreviewResponse preview(Long userId) {
        return dataCollector.collect(loadOrDefault(userId));
    }

    public CareerDataPreviewResponse previewFor(CareerProfile profile) {
        return dataCollector.collect(profile);
    }

    public CareerProfile requireConsentedProfile(Long userId) {
        CareerProfile profile = find(userId);
        if (profile == null || !Integer.valueOf(1).equals(profile.getConsentGranted())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "请先在数据授权中同意生涯分析的数据使用范围"
            );
        }
        return profile;
    }

    private CareerProfile loadOrDefault(Long userId) {
        CareerProfile profile = find(userId);
        return profile == null ? transientProfile(userId) : profile;
    }

    private CareerProfile find(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<CareerProfile>()
                .eq(CareerProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private CareerProfile getRequired(Long userId) {
        CareerProfile profile = find(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "生涯分析资料不存在");
        }
        return profile;
    }

    private CareerProfile getOrCreate(Long userId) {
        CareerProfile existing = find(userId);
        if (existing != null) {
            return existing;
        }
        try {
            CareerProfile created = transientProfile(userId);
            profileMapper.insert(created);
            return created;
        } catch (DataIntegrityViolationException exception) {
            CareerProfile raced = find(userId);
            if (raced == null) {
                throw exception;
            }
            return raced;
        }
    }

    private CareerProfile transientProfile(Long userId) {
        CareerProfile profile = new CareerProfile();
        profile.setUserId(userId);
        profile.setConsentGranted(0);
        profile.setConsentVersion(CareerConsentPolicy.CONSENT_VERSION);
        profile.setIncludeLearningRecords(1);
        profile.setIncludeGoals(1);
        profile.setIncludeGrades(1);
        profile.setDeleted(0);
        profile.setVersion(0);
        return profile;
    }

    private CareerProfileResponse toResponse(CareerProfile profile) {
        return new CareerProfileResponse(
                profile.getId(),
                Integer.valueOf(1).equals(profile.getConsentGranted()),
                StringUtils.hasText(profile.getConsentVersion())
                        ? profile.getConsentVersion()
                        : CareerConsentPolicy.CONSENT_VERSION,
                profile.getConsentedAt(),
                profile.getRevokedAt(),
                !Integer.valueOf(0).equals(profile.getIncludeLearningRecords()),
                !Integer.valueOf(0).equals(profile.getIncludeGoals()),
                !Integer.valueOf(0).equals(profile.getIncludeGrades()),
                profile.getMajor(),
                profile.getGradeLevel(),
                profile.getWeeklyHours(),
                splitTags(profile.getInterestTags()),
                profile.getStrengthNotes(),
                profile.getCareerGoal(),
                profile.getUpdatedAt()
        );
    }

    private List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(tag -> tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag)
                .distinct()
                .limit(MAX_TAGS)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
