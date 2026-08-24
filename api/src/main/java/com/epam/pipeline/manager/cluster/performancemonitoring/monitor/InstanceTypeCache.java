/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class InstanceTypeCache {

    private final InstanceOfferManager instanceOfferManager;
    private volatile Map<String, InstanceType> instanceTypes = Collections.emptyMap();

    @Autowired
    public InstanceTypeCache(final InstanceOfferManager instanceOfferManager) {
        this.instanceOfferManager = instanceOfferManager;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(cron = "0 0 0 ? * *")
    public void refresh() {
        instanceTypes = ListUtils.emptyIfNull(instanceOfferManager.getAllInstanceTypes()).stream()
                .collect(Collectors.toMap(InstanceType::getName, Function.identity(), (t1, t2) -> t1));
    }

    public Map<String, InstanceType> get() {
        return instanceTypes;
    }
}
