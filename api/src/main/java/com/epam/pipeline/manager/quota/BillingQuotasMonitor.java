/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.dto.quota.QuotaUsage;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class BillingQuotasMonitor {

    private static final int PERCENT = 100;

    private final QuotaService quotaService;
    private final QuotaRequestService requestService;
    private final QuotaHandlerService quotaHandler;
    private final PreferenceManager preferenceManager;

    @SchedulerLock(name = "BillingQuotasMonitor_checkQuotas", lockAtMostForString = "PT30M")
    public void checkQuotas() {
        if (!preferenceManager.getPreference(SystemPreferences.BILLING_QUOTAS_ENABLED)) {
            log.debug("Billing quotas monitoring is disabled.");
            return;
        }
        log.debug("Starting billing quotas monitoring.");
        ListUtils.emptyIfNull(quotaService.getAll())
                .forEach(this::applyQuota);
        log.debug("Finished billing quotas monitoring.");
    }

    @SchedulerLock(name = "BillingQuotasMonitor_checkQuotas", lockAtMostForString = "PT10M")
    public void clearQuotas() {
        if (!preferenceManager.getPreference(SystemPreferences.BILLING_QUOTAS_ENABLED)) {
            log.debug("Billing quotas monitoring is disabled.");
            return;
        }
        log.debug("Clearing obsolete active quotas.");
        quotaService.deleteObsoleteQuotas(DateUtils.nowUTC().toLocalDate());
        log.debug("Finished cleaning obsolete active quotas.");
    }


    private void applyQuota(final Quota quota) {
        try {
            log.debug("Processing quota {}", quota);
            final QuotaUsage usage = requestService.getQuotaUsage(quota);
            final Map<Boolean, List<QuotaAction>> actionsStatus = ListUtils.emptyIfNull(quota.getActions())
                    .stream()
                    .collect(Collectors.partitioningBy(action -> exceedsLimit(quota, action, usage)));
            //process active actions
            ListUtils.emptyIfNull(actionsStatus.get(true))
                    .forEach(action -> {
                        final AppliedQuota applied = AppliedQuota.builder()
                                .quota(quota)
                                .action(action)
                                .from(usage.getFrom())
                                .to(usage.getTo())
                                .expense(usage.getExpense())
                                .build();
                        quotaHandler.applyAction(applied);
                    });
            //ensure non-active actions are not applied
            ListUtils.emptyIfNull(actionsStatus.get(false))
                    .forEach(action -> quotaHandler.clearAction(quota, action));
        } catch (Exception e) {
            log.debug("An error occurred during quota processing " + quota, e);
        }
    }

    private boolean exceedsLimit(final Quota quota,
                                 final QuotaAction action,
                                 final QuotaUsage usage) {
        final Double expense = usage.getExpense();
        if (Objects.isNull(expense)) {
            log.debug("Failed to retrieve expense for quota {}", quota);
            return false;
        }
        log.debug("Checking limit for quota {}, current expense is {}", quota, expense);
        return Double.compare(expense,
                action.getThreshold() * quota.getValue() / PERCENT) > 0;
    }
}
