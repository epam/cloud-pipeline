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

package com.epam.pipeline.test.creator.credits;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

public interface PlatformUsageCreditsUserBalanceCreatorUtils {

    TypeReference<Result<PagedResult<List<PlatformUsageCreditsUserBalance>>>> BALANCE_PAGED_TYPE =
            new TypeReference<Result<PagedResult<List<PlatformUsageCreditsUserBalance>>>>() {};
    TypeReference<Result<Void>> VOID_TYPE = new TypeReference<Result<Void>>() {};

    Long USER_ID = 1L;
    int BALANCE_VALUE = 1000;
    int RESET_VALUE = 2000;
    int DEFAULT_BALANCE = 2000;
    int MIN_BALANCE = 24;
    int MAX_BALANCE = 3000;
    // threshold 25% → absoluteValue = 24 + 0.25*(3000-24) = 768
    int NOTIFICATION_THRESHOLD = 25;
    int BALANCE_BELOW_THRESHOLD = 700;
    int BALANCE_ABOVE_THRESHOLD = 800;
    int CURRENT_BALANCE = 2000;
    int EVENT_VALUE = 100;
    int BALANCE_NEAR_MAX = 2900;
    int BALANCE_NEAR_MIN = 50;
    String OPERATION_GT = ">";

    static PipelineUser pipelineUser() {
        final PipelineUser user = new PipelineUser();
        user.setId(USER_ID);
        user.setRoles(Collections.emptyList());
        return user;
    }

    static PlatformUsageCreditsUserBalanceEntity balanceEntity() {
        return PlatformUsageCreditsUserBalanceEntity.builder()
                .id(1L)
                .userId(USER_ID)
                .currentValue(BALANCE_VALUE)
                .modifiedDate(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .build();
    }

    static PlatformUsageCreditsUserBalance balanceDto() {
        return new PlatformUsageCreditsUserBalance(USER_ID, BALANCE_VALUE,
            LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS), null);
    }

    static PlatformUsageCreditsUserBalanceFilterVO filterVO() {
        return PlatformUsageCreditsUserBalanceFilterVO.builder()
                .userIds(Collections.singletonList(USER_ID))
                .value(BALANCE_VALUE)
                .operation(OPERATION_GT)
                .page(1)
                .pageSize(10)
                .build();
    }

    static PagedResult<List<PlatformUsageCreditsUserBalance>> pagedResult() {
        return new PagedResult<>(Collections.singletonList(balanceDto()), 1);
    }

    static PlatformUsageCreditsUpdateEvent increaseEvent(final int value) {
        return PlatformUsageCreditsUpdateEvent.builder()
                .userId(USER_ID)
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.INCREASE)
                .value(value)
                .build();
    }

    static PlatformUsageCreditsUpdateEvent deductionEvent(final int value) {
        return PlatformUsageCreditsUpdateEvent.builder()
                .userId(USER_ID)
                .incidentType(PlatformUsageCreditsUpdateAction.ActionType.DEDUCTION)
                .value(value)
                .build();
    }
}
