package com.onlinecurlearnmanos.ai.mapper;

import com.onlinecurlearnmanos.ai.entity.AiDailyUsage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface AiDailyUsageMapper extends BaseMapper<AiDailyUsage> {

    @Insert("""
            INSERT INTO ai_daily_usage (user_id, usage_date, standard_used, expert_used)
            VALUES (#{userId}, #{usageDate}, 0, 0)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """)
    int ensureRow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("""
            UPDATE ai_daily_usage
            SET standard_used = standard_used + 1
            WHERE user_id = #{userId} AND usage_date = #{usageDate}
              AND standard_used < #{limit}
            """)
    int consumeStandard(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE ai_daily_usage
            SET expert_used = expert_used + 1
            WHERE user_id = #{userId} AND usage_date = #{usageDate}
              AND expert_used < #{limit}
            """)
    int consumeExpert(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE ai_daily_usage
            SET standard_used = GREATEST(standard_used - 1, 0)
            WHERE user_id = #{userId} AND usage_date = #{usageDate}
            """)
    int releaseStandard(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Update("""
            UPDATE ai_daily_usage
            SET expert_used = GREATEST(expert_used - 1, 0)
            WHERE user_id = #{userId} AND usage_date = #{usageDate}
            """)
    int releaseExpert(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    @Select("""
            SELECT * FROM ai_daily_usage
            WHERE user_id = #{userId} AND usage_date = #{usageDate}
            LIMIT 1
            """)
    AiDailyUsage find(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate
    );
}
