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

package com.epam.pipeline.aspect.cluster;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class InstancePriceListAspect {

    private final InstanceOfferScheduler instanceOfferScheduler;

    @AfterReturning(pointcut = "execution(* com.epam.pipeline.manager.region.CloudRegionManager.create(..))",
            returning = "region")
    public void updatePriceListForNewRegion(JoinPoint joinPoint, AbstractCloudRegion region) {
        instanceOfferScheduler.updatePriceList(region);
    }

    @AfterReturning(pointcut = "execution(* com.epam.pipeline.manager.region.CloudRegionManager.update(..))",
            returning = "region")
    public void updatePriceListForUpdatedRegion(JoinPoint joinPoint, AbstractCloudRegion region) {
        if (region.getProvider().equals(CloudProvider.GCP) || region.getProvider().equals(CloudProvider.LOCAL)) {
            instanceOfferScheduler.updatePriceList(region);
        }
    }
}
