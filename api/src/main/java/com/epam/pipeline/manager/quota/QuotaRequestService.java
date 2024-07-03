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
import com.epam.pipeline.dto.quota.QuotaUsage;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.billing.BillingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;


@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaRequestService {

    public static final int MILLICENTS_IN_DOLLAR = 10_000;
    private final BillingManager billingManager;

    public QuotaUsage getQuotaUsage(final Quota quota) {
        final LocalDate from = getStartDate(quota);
        final LocalDate to = DateUtils.nowUTC().toLocalDate();
        final LocalDate endDate = getEndDate(quota);
        return QuotaUsage
                .builder()
                .from(from)
                .to(endDate)
                .expense(billingManager.getQuotaExpense(quota, from, to) / MILLICENTS_IN_DOLLAR)
                .build();
    }

    private LocalDate getEndDate(final Quota quota) {
        final LocalDate now = DateUtils.nowUTC().toLocalDate();
        switch (quota.getPeriod()) {
            case MONTH:
                return now.with(TemporalAdjusters.lastDayOfMonth());
            case QUARTER:
                return getFirstDayOfQuarter(now).plusMonths(2)
                        .with(TemporalAdjusters.lastDayOfMonth());
            case YEAR:
                return now.with(TemporalAdjusters.lastDayOfYear());
            default:
                return now;
        }
    }

    private LocalDate getStartDate(final Quota quota) {
        final LocalDate now = DateUtils.nowUTC().toLocalDate();
        switch (quota.getPeriod()) {
            case MONTH:
                return now.withDayOfMonth(1);
            case QUARTER:
                return getFirstDayOfQuarter(now);
            case YEAR:
                return now.withDayOfYear(1);
            default:
                return now;
        }
    }

    private LocalDate getFirstDayOfQuarter(final LocalDate now) {
        return now.with(now.getMonth().firstMonthOfQuarter()).with(TemporalAdjusters.firstDayOfMonth());
    }
}
