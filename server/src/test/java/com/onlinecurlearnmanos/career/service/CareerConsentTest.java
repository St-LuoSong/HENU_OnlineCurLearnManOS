package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.career.dto.CareerConsentRequest;
import com.onlinecurlearnmanos.career.dto.CareerProfileResponse;
import com.onlinecurlearnmanos.career.entity.CareerProfile;
import com.onlinecurlearnmanos.career.mapper.CareerProfileMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 生涯分析的数据授权契约：未授权不得生成分析；授权与撤销都要写入版本号与时间戳，
 * 便于"用户核对自己授权了什么"。
 */
@ExtendWith(MockitoExtension.class)
class CareerConsentTest {

    private static final Long USER_ID = 31L;

    @Mock
    private CareerProfileMapper profileMapper;

    @Mock
    private CareerDataCollector dataCollector;

    @InjectMocks
    private CareerProfileService careerProfileService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(CareerProfile.class);
    }

    @Test
    void requiresConsentBeforeGeneratingAnalysis() {
        given(profileMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> careerProfileService.requireConsentedProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("数据授权");
    }

    @Test
    void rejectsProfileThatRevokedConsent() {
        given(profileMapper.selectOne(any())).willReturn(profile(0));

        assertThatThrownBy(() -> careerProfileService.requireConsentedProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据授权");
    }

    @Test
    void returnsProfileWhenConsentGranted() {
        CareerProfile consented = profile(1);
        given(profileMapper.selectOne(any())).willReturn(consented);

        assertThat(careerProfileService.requireConsentedProfile(USER_ID)).isSameAs(consented);
    }

    @Test
    void newUserSeesUnconsentedProfileWithCurrentVersion() {
        given(profileMapper.selectOne(any())).willReturn(null);

        CareerProfileResponse response = careerProfileService.get(USER_ID);

        assertThat(response.consentGranted()).isFalse();
        assertThat(response.consentVersion()).isEqualTo(CareerConsentPolicy.CONSENT_VERSION);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void grantingConsentWritesVersionAndTimestamp() {
        CareerProfile existing = profile(0);
        given(profileMapper.selectOne(any())).willReturn(existing, existing);

        careerProfileService.updateConsent(
                USER_ID,
                new CareerConsentRequest(true, true, true, false)
        );

        ArgumentCaptor<LambdaUpdateWrapper<CareerProfile>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(profileMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet())
                .contains("consent_granted")
                .contains("consent_version")
                .contains("revoked_at");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(1, CareerConsentPolicy.CONSENT_VERSION);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void revokingConsentWritesRevokedAtAndKeepsVersion() {
        CareerProfile existing = profile(1);
        existing.setConsentedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        given(profileMapper.selectOne(any())).willReturn(existing, existing);

        careerProfileService.updateConsent(
                USER_ID,
                new CareerConsentRequest(false, false, false, false)
        );

        ArgumentCaptor<LambdaUpdateWrapper<CareerProfile>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(profileMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet())
                .contains("revoked_at")
                .contains("consent_version");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(0, CareerConsentPolicy.CONSENT_VERSION);
    }

    private CareerProfile profile(int consentGranted) {
        CareerProfile profile = new CareerProfile();
        profile.setId(9L);
        profile.setUserId(USER_ID);
        profile.setConsentGranted(consentGranted);
        profile.setConsentVersion(CareerConsentPolicy.CONSENT_VERSION);
        profile.setIncludeLearningRecords(1);
        profile.setIncludeGoals(1);
        profile.setIncludeGrades(1);
        return profile;
    }
}
