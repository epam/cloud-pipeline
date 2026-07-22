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

    Optional<PlatformUsageCreditsUserBalanceEntity> findByUserId(Long userId);

    /**
     * Upserts a balance row for every user in {@code pipeline.user}.
     * Users who already have a row get their balance updated; users with no row get one created.
     * The timestamp is set by the database so all rows share a consistent {@code NOW()} value.
     */
    @Modifying
    @Query(value = BATCH_RESET, nativeQuery = true)
    void resetAll(@Param("value") int value);
}
