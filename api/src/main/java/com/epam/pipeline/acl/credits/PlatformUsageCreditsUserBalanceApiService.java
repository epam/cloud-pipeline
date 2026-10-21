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

package com.epam.pipeline.acl.credits;

import com.epam.pipeline.aspect.credits.CreditsFeatureCheck;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsResetRequest;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalanceFilterVO;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsEventService;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsUserBalanceCRUDService;
import com.epam.pipeline.manager.credits.PlatformUsageCreditsUserBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_USER_ADMIN;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;

@Service
@RequiredArgsConstructor
public class PlatformUsageCreditsUserBalanceApiService {

    private final PlatformUsageCreditsUserBalanceCRUDService userBalanceCRUD;
    private final PlatformUsageCreditsUserBalanceService userBalanceService;
    private final PlatformUsageCreditsEventService eventService;

    @CreditsFeatureCheck
    @PreAuthorize(ADMIN_OR_USER_ADMIN)
    public PagedResult<List<PlatformUsageCreditsUserBalance>> filter(
            final PlatformUsageCreditsUserBalanceFilterVO filter) {
        return userBalanceCRUD.filter(filter);
    }

    @CreditsFeatureCheck
    @PreAuthorize(ADMIN_OR_USER_ADMIN)
    public void reset(final PlatformUsageCreditsResetRequest resetRequest) {
        eventService.reset(resetRequest);
    }

    @CreditsFeatureCheck
    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public PlatformUsageCreditsUserBalance getBalanceWithAllocated(final Long userId) {
        return userBalanceService.getBalanceWithAllocated(userId);
    }
}
