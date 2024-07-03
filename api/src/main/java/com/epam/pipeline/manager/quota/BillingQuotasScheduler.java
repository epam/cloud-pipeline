/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.quota;

import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BillingQuotasScheduler extends AbstractSchedulingManager {

    private final BillingQuotasMonitor monitor;

    @PostConstruct
    public void setup() {
        scheduleFixedDelaySecured(monitor::checkQuotas,
                SystemPreferences.BILLING_QUOTAS_MONITORING_PERIOD_SECONDS,
                TimeUnit.SECONDS,
                "Billing quota check");

        scheduleFixedDelaySecured(monitor::clearQuotas,
                SystemPreferences.BILLING_QUOTAS_CLEARING_PERIOD_SECONDS,
                TimeUnit.SECONDS,
                "Billing quota clear");
    }
}
