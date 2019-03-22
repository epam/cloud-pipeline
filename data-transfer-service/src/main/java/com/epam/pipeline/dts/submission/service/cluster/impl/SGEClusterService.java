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

import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.model.cluster.ClusterConfiguration;
import com.epam.pipeline.dts.submission.model.cluster.ClusterNode;
import com.epam.pipeline.dts.submission.model.cluster.QHosts;
import com.epam.pipeline.dts.submission.service.cluster.ClusterService;
import com.epam.pipeline.dts.submission.service.cluster.HostConverter;
import com.epam.pipeline.dts.submission.service.sge.SGEService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SGEClusterService implements ClusterService {

    private final SGEService sgeService;
    private final HostConverter hostConverter;

    @Override
    public ClusterConfiguration getClusterConfiguration() {
        try {
            QHosts sgeHosts = sgeService.getHosts();
            List<ClusterNode> nodes = ListUtils.emptyIfNull(sgeHosts.getHosts())
                    .stream()
                    .map(hostConverter::convert)
                    .collect(Collectors.toList());
            return new ClusterConfiguration(nodes);
        } catch (SGECmdException e) {
            throw new IllegalArgumentException(
                    String.format("Failed to retrieve SGE cluster configuration: %s", e.getMessage()));
        }
    }
}
