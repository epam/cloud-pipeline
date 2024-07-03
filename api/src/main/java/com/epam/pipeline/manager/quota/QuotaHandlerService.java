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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.manager.quota.handler.QuotaHandler;
import com.epam.pipeline.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class QuotaHandlerService {

    private static final double DELTA = 0.01;
    private final Map<QuotaActionType, QuotaHandler> handlers;
    private final QuotaService quotaService;

    public QuotaHandlerService(final List<QuotaHandler> handlers,
                               final QuotaService quotaService) {
        this.handlers = CommonUtils.groupByKey(handlers, QuotaHandler::type);
        this.quotaService = quotaService;
    }

    public void applyAction(final AppliedQuota appliedQuota) {
        final List<AppliedQuota> activeActions = quotaService.findActiveQuotaForAction(
                appliedQuota.getAction().getId());
        if (CollectionUtils.isNotEmpty(activeActions)) {
            log.debug("{} action(s) are already applied for quota {}", activeActions.size(),
                    appliedQuota.getQuota());
            //Update current expense if required
            activeActions.stream()
                    .filter(previous -> quotaChanged(appliedQuota, previous))
                    .forEach(previous -> {
                        previous.setFrom(appliedQuota.getFrom());
                        previous.setTo(appliedQuota.getTo());
                        previous.setExpense(appliedQuota.getExpense());
                        quotaService.createAppliedQuota(previous);
                    });
            return;
        }
        quotaService.createAppliedQuota(appliedQuota);
        ListUtils.emptyIfNull(appliedQuota.getAction().getActions())
                .forEach(type -> handlers.getOrDefault(type, new EmptyQuotaHandler())
                        .applyActionType(appliedQuota, type));
    }

    public void clearAction(final Quota quota, final QuotaAction action) {
        final List<AppliedQuota> activeActions = quotaService.findActiveQuotaForAction(
                action.getId());
        if (CollectionUtils.isNotEmpty(activeActions)) {
            log.debug("Cancelling {} non-relevant action(s) for quota {}", activeActions.size(), quota);
            quotaService.deleteAppliedQuotas(activeActions);
        }
    }

    private boolean quotaChanged(final AppliedQuota appliedQuota, final AppliedQuota previousQuota) {
        if (!appliedQuota.getFrom().equals(previousQuota.getFrom())) {
            return true;
        }
        if (!appliedQuota.getTo().equals(previousQuota.getTo())) {
            return true;
        }
        return Precision.compareTo(previousQuota.getExpense(), appliedQuota.getExpense(), DELTA) != 0;
    }

    static class EmptyQuotaHandler implements QuotaHandler {

        @Override
        public QuotaActionType type() {
            return null;
        }

        @Override
        public void applyActionType(final AppliedQuota appliedQuota, final QuotaActionType type) {
            log.debug("No appropriate handler found for action type {}", type);
        }
    }
}
