package com.example.bootserver.order.internal.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Outbox 的有界轮询与条件状态迁移 SQL。 */
public interface OrderTimeoutOutboxMapper extends BaseMapper<OrderTimeoutOutboxEntity> {

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = CASE WHEN send_attempts >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
                next_attempt_time = #{now},
                last_error = CASE WHEN send_attempts >= #{maxAttempts}
                                  THEN '发送租约过期且达到重试上限'
                                  ELSE '发送租约过期，重新排队' END,
                update_time = #{now}
            WHERE status = 'SENDING' AND update_time <= #{staleBefore}
            """)
    int recoverStaleSending(@Param("staleBefore") LocalDateTime staleBefore,
                            @Param("now") LocalDateTime now,
                            @Param("maxAttempts") int maxAttempts);

    @Select("""
            SELECT id, event_id, business_key, order_id, event_version, payload, status,
                   send_attempts, next_attempt_time, sent_at, processed_at, last_error,
                   create_time, update_time
            FROM t_order_timeout_outbox
            WHERE status = 'PENDING'
              AND next_attempt_time <= #{now}
              AND send_attempts < #{maxAttempts}
            ORDER BY next_attempt_time ASC, id ASC
            LIMIT #{batchSize}
            """)
    List<OrderTimeoutOutboxEntity> selectDispatchable(@Param("now") LocalDateTime now,
                                                       @Param("maxAttempts") int maxAttempts,
                                                       @Param("batchSize") int batchSize);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = 'SENDING', send_attempts = send_attempts + 1, update_time = #{now}
            WHERE id = #{id} AND status = 'PENDING'
              AND next_attempt_time <= #{now} AND send_attempts < #{maxAttempts}
            """)
    int claimForSend(@Param("id") Long id, @Param("now") LocalDateTime now,
                     @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = 'SENT', sent_at = #{now}, last_error = NULL, update_time = #{now}
            WHERE id = #{id} AND status = 'SENDING'
            """)
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = 'DEAD', last_error = #{error}, update_time = #{now}
            WHERE id = #{id} AND status = 'SENDING' AND send_attempts >= #{maxAttempts}
            """)
    int markDeadAfterFailure(@Param("id") Long id, @Param("error") String error,
                             @Param("now") LocalDateTime now,
                             @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = 'PENDING', next_attempt_time = #{nextAttempt},
                last_error = #{error}, update_time = #{now}
            WHERE id = #{id} AND status = 'SENDING' AND send_attempts < #{maxAttempts}
            """)
    int markPendingAfterFailure(@Param("id") Long id, @Param("error") String error,
                                @Param("nextAttempt") LocalDateTime nextAttempt,
                                @Param("now") LocalDateTime now,
                                @Param("maxAttempts") int maxAttempts);

    @Select("""
            SELECT id, event_id, business_key, order_id, event_version, payload, status,
                   send_attempts, next_attempt_time, sent_at, processed_at, last_error,
                   create_time, update_time
            FROM t_order_timeout_outbox WHERE event_id = #{eventId}
            """)
    OrderTimeoutOutboxEntity selectByEventId(@Param("eventId") String eventId);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = 'CONSUMING', update_time = #{now}
            WHERE event_id = #{eventId} AND status NOT IN ('CONSUMING', 'PROCESSED', 'IGNORED')
            """)
    int claimForConsume(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_order_timeout_outbox
            SET status = #{target}, processed_at = #{now}, update_time = #{now}
            WHERE event_id = #{eventId} AND status = 'CONSUMING'
            """)
    int markConsumed(@Param("eventId") String eventId, @Param("target") String target,
                     @Param("now") LocalDateTime now);
}
