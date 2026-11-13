package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.mapper.AiDailyUsageMapper;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAccessServiceTest {

    @Mock
    private AiDailyUsageMapper usageMapper;

    @InjectMocks
    private AiAccessService accessService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accessService, "standardDailyLimit", 50);
        ReflectionTestUtils.setField(accessService, "expertDailyLimit", 3);
    }

    @Test
    void consumesExpertQuotaAtomically() {
        given(usageMapper.consumeExpert(eq(7L), any(LocalDate.class), eq(3))).willReturn(1);

        AiAccessService.QuotaGrant grant = accessService.consume(7L, AiMode.EXPERT);

        assertThat(grant.mode()).isEqualTo(AiMode.EXPERT);
        assertThat(grant.userId()).isEqualTo(7L);
    }

    @Test
    void rejectsWhenExpertQuotaExhausted() {
        given(usageMapper.consumeExpert(eq(7L), any(LocalDate.class), eq(3))).willReturn(0);

        assertThatThrownBy(() -> accessService.consume(7L, AiMode.EXPERT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专家模式次数已用完");
    }

    @Test
    void releasesReleasedQuotaOnFailure() {
        AiAccessService.QuotaGrant grant =
                new AiAccessService.QuotaGrant(7L, AiMode.STANDARD, LocalDate.of(2026, 10, 27));

        accessService.release(grant);

        verify(usageMapper).releaseStandard(eq(7L), eq(LocalDate.of(2026, 10, 27)));
    }

    @Test
    void computesRemainingFromDailyUsage() {
        var usage = new com.onlinecurlearnmanos.ai.entity.AiDailyUsage();
        usage.setStandardUsed(12);
        usage.setExpertUsed(2);
        given(usageMapper.find(eq(7L), any(LocalDate.class))).willReturn(usage);

        var status = accessService.status(7L);

        assertThat(status.standardRemaining()).isEqualTo(38);
        assertThat(status.expertRemaining()).isEqualTo(1);
    }
}
