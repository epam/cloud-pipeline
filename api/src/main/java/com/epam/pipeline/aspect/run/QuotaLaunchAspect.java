/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.aspect.run;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.quota.BillingQuotaExceededException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.quota.QuotaService;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Aspect
@Slf4j
@RequiredArgsConstructor
public class QuotaLaunchAspect {
    private final PreferenceManager preferenceManager;
    private final QuotaService quotaService;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    @Before("@annotation(com.epam.pipeline.aspect.run.QuotaLaunchCheck)")
    public void checkRunLaunchIsNotForbidden(JoinPoint joinPoint) {
        if (!preferenceManager.getPreference(SystemPreferences.BILLING_QUOTAS_ENABLED)) {
            return;
        }
        final PipelineUser currentUser = authManager.getCurrentUser();
        if (currentUser.isAdmin()) {
            return;
        }
        log.info("Checking billing quota limits for launching runs.");
        final Optional<AppliedQuota> activeQuota = quotaService.findActiveActionForUser(currentUser,
                QuotaActionType.DISABLE_NEW_JOBS);
        activeQuota.ifPresent(quota -> {
            log.info("Launch of new jobs is restricted due to quota applied {}", quota);
            throw new BillingQuotaExceededException(
                    messageHelper.getMessage(MessageConstants.ERROR_BILLING_QUOTA_EXCEEDED_LAUNCH));
        });
    }
}
