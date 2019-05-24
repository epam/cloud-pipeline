/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.region.AbstractCloudRegion;

import java.util.List;

public interface CloudInstancePriceService<T extends AbstractCloudRegion> extends CloudAwareService {
    String ON_DEMAND_TERM_TYPE = "OnDemand";
    String SPOT_TERM_TYPE = "Spot";
    String LINUX_OPERATING_SYSTEM = "Linux";
    String SHARED_TENANCY = "Shared";
    String HOURS_UNIT = "Hrs";
    String INSTANCE_PRODUCT_FAMILY = "Compute Instance";
    String STORAGE_PRODUCT_FAMILY = "Storage";
    String GENERAL_PURPOSE_VOLUME_TYPE = "General Purpose";
    double HOURS_IN_DAY = 24;
    double DAYS_IN_MONTH = 30;

    List<InstanceOffer> refreshPriceListForRegion(T region);
    double getSpotPrice(String instanceType, T region);
    double getPriceForDisk(List<InstanceOffer> offers, int instanceDisk, String instanceType, T region);
}
