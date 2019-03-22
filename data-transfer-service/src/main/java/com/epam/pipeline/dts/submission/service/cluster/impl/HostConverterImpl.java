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

package com.epam.pipeline.dts.submission.service.cluster.impl;

import com.epam.pipeline.dts.submission.model.cluster.ClusterNode;
import com.epam.pipeline.dts.submission.model.cluster.SGEHost;
import com.epam.pipeline.dts.submission.service.cluster.HostConverter;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;

@Component
public class HostConverterImpl implements HostConverter {

    @Override
    public ClusterNode convert(SGEHost host) {
        SGEHost.Queue queue = ListUtils.emptyIfNull(host.getHostQueues())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Queue mapping is invalid"));
        return ClusterNode.builder()
                .hostname(host.getName())
                .slotsTotal(queue.getSlotsNumber())
                .slotsUsed(queue.getSlotsInUseNumber())
                .build();
    }
}
