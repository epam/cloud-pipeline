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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.credits.PlatformUsageCreditsUserBalanceMapper;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceRepository;
import com.epam.pipeline.repository.credits.PlatformUsageCreditsUserBalanceSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business-logic layer for platform usage credits user balances.
 *
 * <p>A user balance tracks the current credits held by a specific pipeline user and is updated
 * whenever the monitoring service fires a credits event (increase or deduction).
 *
 */
@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceService {

    private final PlatformUsageCreditsUserBalanceRepository repository;
    private final PlatformUsageCreditsUserBalanceMapper mapper;

    /**
     * Returns a paged, optionally filtered list of user balances.
     * This method supports three optional predicates, all
     * translated to SQL so that paging counts are always accurate:
     * <ul>
     *   <li>{@code userIds} — restrict results to a specific set of users</li>
     *   <li>{@code value} + {@code operation} ({@code "<"}, {@code ">"}, {@code "="}) — compare
     *       the stored {@code current_value} against the given threshold at the database level</li>
     * </ul>
     * @param filter filter criteria; {@code page} and {@code pageSize} are always applied
     * @return paged result containing matching balance DTOs and the total count
     */
    public PagedResult<List<PlatformUsageCreditsUserBalance>> filter(
            final PlatformUsageCreditsUserBalanceFilterVO filter) {
        final Page<PlatformUsageCreditsUserBalanceEntity> page =
                repository.findAll(PlatformUsageCreditsUserBalanceSpecification.build(filter),
                        new PageRequest(filter.getPage(), filter.getPageSize()));
        final List<PlatformUsageCreditsUserBalance> elements = page.getContent().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
        return new PagedResult<>(elements, (int) page.getTotalElements());
    }

    /**
     * Resets the credits balance to {@code value}.
     * This method either updates one user's balance (when {@code userId} is
     * supplied) or bulk-updates every row in the table (when {@code userId} is {@code null}).
     * If the target user has no existing balance row it is created on the fly.
     *
     * <p>When {@code userId} is non-null, only that user's row is updated (created if absent).
     * When {@code userId} is {@code null}, every existing balance row is updated in a single
     * bulk statement.
     *
     * @param value  the new credits balance to set
     * @param userId the target user, or {@code null} to reset all users
     */
    @Transactional
    public void reset(final int value, @Nullable final Long userId) {
        final LocalDateTime now = DateUtils.nowUTC();
        if (userId != null) {
            final PlatformUsageCreditsUserBalanceEntity entity = repository.findByUserId(userId)
                    .orElseGet(() -> PlatformUsageCreditsUserBalanceEntity.builder().userId(userId).build());
            entity.setCurrentValue(value);
            entity.setModifiedDate(now);
            repository.save(entity);
        } else {
            repository.resetAll(value, now);
        }
    }
}
