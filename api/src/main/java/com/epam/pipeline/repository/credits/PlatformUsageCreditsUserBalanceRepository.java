/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.repository.credits;

import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformUsageCreditsUserBalanceRepository
        extends PagingAndSortingRepository<PlatformUsageCreditsUserBalanceEntity, Long>,
        JpaSpecificationExecutor<PlatformUsageCreditsUserBalanceEntity> {

    String BATCH_RESET = "INSERT INTO pipeline.usage_credits_user_balance (user_id, current_value, modified_date) "
            + "SELECT id, :value, NOW() FROM pipeline.user "
            + "ON CONFLICT (user_id) DO UPDATE "
            + "SET current_value = EXCLUDED.current_value, modified_date = EXCLUDED.modified_date";
    String ATOMIC_UPSERT = "WITH upsert AS ( "
            + "INSERT INTO pipeline.usage_credits_user_balance (user_id, current_value, modified_date) "
            + "VALUES (:userId, GREATEST(:min, LEAST(:max, :defaultBalance + :delta)), NOW()) "
            + "ON CONFLICT (user_id) DO UPDATE "
            + "SET current_value = GREATEST(:min, LEAST(:max, "
            + "    pipeline.usage_credits_user_balance.current_value + :delta)), "
            + "    modified_date = NOW() "
            + "RETURNING current_value "
            + ") SELECT current_value FROM upsert";

    Optional<PlatformUsageCreditsUserBalanceEntity> findByUserId(Long userId);

    /**
     * Atomically upserts the credits balance for a single user and returns the resulting value.
     *
     * <p>The arithmetic and clamping are performed entirely inside the database using a single SQL
     * statement, so concurrent calls for the same user cannot race. The DML is wrapped in a CTE and
     * exposed as a {@code SELECT} so that Spring Data JPA returns the {@code RETURNING} value
     * directly without requiring a separate follow-up read.
     *
     * @param userId         the target user
     * @param delta          signed delta: positive for INCREASE, negative for DEDUCTION
     * @param defaultBalance starting balance when no row exists yet
     * @param min            lower bound for clamping
     * @param max            upper bound for clamping
     * @return the new {@code current_value} after the update
     */
    @Query(value = ATOMIC_UPSERT, nativeQuery = true)
    Integer atomicUpdateBalance(
            @Param("userId") Long userId,
            @Param("delta") int delta,
            @Param("defaultBalance") int defaultBalance,
            @Param("min") int min,
            @Param("max") int max);

    /**
     * Upserts a balance row for every user in {@code pipeline.user}.
     * Users who already have a row get their balance updated; users with no row get one created.
     * The timestamp is set by the database so all rows share a consistent {@code NOW()} value.
     */
    @Modifying
    @Query(value = BATCH_RESET, nativeQuery = true)
    void resetAll(@Param("value") int value);
}
