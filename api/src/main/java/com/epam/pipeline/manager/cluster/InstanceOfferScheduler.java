/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * A class, that schedules price list updates and checks
 */
@Service
@RequiredArgsConstructor
public class InstanceOfferScheduler extends AbstractSchedulingManager {

    private final InstanceOfferSchedulerCore core;

    @PostConstruct
    public void init() {
        scheduleFixedDelay(core::checkAndUpdatePriceListIfNecessary,
                SystemPreferences.CLUSTER_INSTANCE_OFFER_UPDATE_RATE,
                "Instance Offers Expiration Status Check");
    }

    public void checkAndUpdatePriceListIfNecessary() {
        core.checkAndUpdatePriceListIfNecessary();
    }

    public void updatePriceList() {
        core.updatePriceList();
    }

    public void updatePriceList(final AbstractCloudRegion region) {
        core.updatePriceList(region);
    }
}
