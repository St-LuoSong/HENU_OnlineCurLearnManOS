package com.onlinecurlearnmanos.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningActivityMapper {

    @Select("""
            SELECT activity_date, activity_type, SUM(amount) AS amount
            FROM (
                SELECT DATE(m.created_at) AS activity_date,
                       'AI_QUESTION' AS activity_type,
                       COUNT(*) AS amount
                FROM chat_message m
                JOIN chat_session s ON s.id = m.session_id
                WHERE s.user_id = #{userId}
                  AND s.deleted = 0
                  AND m.role = 'user'
                  AND m.created_at >= #{from}
                  AND m.created_at < #{to}
                GROUP BY DATE(m.created_at)

                UNION ALL

                SELECT DATE(i.answered_at) AS activity_date,
                       'PRACTICE' AS activity_type,
                       COUNT(*) AS amount
                FROM practice_set_item i
                JOIN practice_set p ON p.id = i.set_id
                WHERE p.user_id = #{userId}
                  AND i.student_answer IS NOT NULL
                  AND TRIM(i.student_answer) <> ''
                  AND i.answered_at >= #{from}
                  AND i.answered_at < #{to}
                GROUP BY DATE(i.answered_at)

                UNION ALL

                SELECT DATE(a.answered_at) AS activity_date,
                       'PRACTICE' AS activity_type,
                       COUNT(*) AS amount
                FROM practice_answer a
                JOIN practice_session ps ON ps.id = a.session_id
                WHERE ps.user_id = #{userId}
                  AND ps.deleted = 0
                  AND a.deleted = 0
                  AND a.answered_at >= #{from}
                  AND a.answered_at < #{to}
                GROUP BY DATE(a.answered_at)

                UNION ALL

                SELECT v.view_date AS activity_date,
                       'RESOURCE' AS activity_type,
                       COUNT(*) AS amount
                FROM resource_view_log v
                WHERE v.user_id = #{userId}
                  AND v.viewed_at >= #{from}
                  AND v.viewed_at < #{to}
                GROUP BY v.view_date

                UNION ALL

                SELECT DATE(p.created_at) AS activity_date,
                       'FORUM' AS activity_type,
                       COUNT(*) AS amount
                FROM forum_post p
                WHERE p.author_id = #{userId}
                  AND p.deleted = 0
                  AND p.status = 'PUBLISHED'
                  AND p.created_at >= #{from}
                  AND p.created_at < #{to}
                GROUP BY DATE(p.created_at)

                UNION ALL

                SELECT DATE(r.created_at) AS activity_date,
                       'FORUM' AS activity_type,
                       COUNT(*) AS amount
                FROM forum_reply r
                WHERE r.author_id = #{userId}
                  AND r.deleted = 0
                  AND r.status = 'PUBLISHED'
                  AND r.created_at >= #{from}
                  AND r.created_at < #{to}
                GROUP BY DATE(r.created_at)

                UNION ALL

                SELECT DATE(c.submitted_at) AS activity_date,
                       'CLASS' AS activity_type,
                       COUNT(*) AS amount
                FROM cloud_class_submission c
                WHERE c.student_id = #{userId}
                  AND c.submitted_at >= #{from}
                  AND c.submitted_at < #{to}
                GROUP BY DATE(c.submitted_at)
            ) activity
            GROUP BY activity_date, activity_type
            ORDER BY activity_date ASC
            """)
    List<ActivityCountRow> countActivities(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Select("""
            SELECT source_type, source_id, title, subject_name, score, max_score, recorded_at
            FROM (
                SELECT 'PRACTICE_SET' AS source_type,
                       p.id AS source_id,
                       p.title AS title,
                       s.name AS subject_name,
                       p.score AS score,
                       100.0 AS max_score,
                       p.submitted_at AS recorded_at
                FROM practice_set p
                LEFT JOIN subject s ON s.id = p.subject_id
                WHERE p.user_id = #{userId}
                  AND p.status = 'SUBMITTED'
                  AND p.score IS NOT NULL

                UNION ALL

                SELECT 'LEARNING_RECORD' AS source_type,
                       r.id AS source_id,
                       COALESCE(kp.name, s.name, r.record_type) AS title,
                       s.name AS subject_name,
                       r.score AS score,
                       100.0 AS max_score,
                       r.recorded_at AS recorded_at
                FROM learning_record r
                LEFT JOIN subject s ON s.id = r.subject_id
                LEFT JOIN knowledge_point kp ON kp.id = r.knowledge_point_id
                WHERE r.user_id = #{userId}
                  AND r.deleted = 0
                  AND r.record_type NOT IN ('AI_PRACTICE_SET', 'HOMEWORK')
                  AND r.score IS NOT NULL

                UNION ALL

                SELECT 'CLASS_TASK' AS source_type,
                       c.id AS source_id,
                       t.title AS title,
                       s.name AS subject_name,
                       c.score AS score,
                       COALESCE(t.max_score, 100.0) AS max_score,
                       COALESCE(c.graded_at, c.submitted_at) AS recorded_at
                FROM cloud_class_submission c
                JOIN cloud_class_task t ON t.id = c.task_id
                LEFT JOIN cloud_class cc ON cc.id = t.class_id
                LEFT JOIN subject s ON s.id = cc.subject_id
                WHERE c.student_id = #{userId}
                  AND c.score IS NOT NULL
            ) scores
            ORDER BY recorded_at DESC
            LIMIT #{limit}
            """)
    List<RecentScoreRow> recentScores(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );
}
