/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.pipeline.run.container.ContainerSecurityContext;
import com.epam.pipeline.entity.pipeline.run.container.RunContainerSpec;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodePoolLaunchConfigBuilder {

    private static final int DEFAULT_CPU_RESERVED = 1;
    private static final int DEFAULT_GPU_RESERVED = 0;
    private static final String DEFAULT_RAM_RESERVED = "1GiB";

    private final InstanceOfferDao instanceOfferDao;

    public NodePoolLaunchConfig build(final NodePoolVO pool) {
        final NodePoolLaunchConfig config = pool.getLaunchConfig() != null ? pool.getLaunchConfig() : generate(pool);
        config.setKubeAssignPolicy(withSelector(config.getKubeAssignPolicy(), pool.getId()));
        return config;
    }

    private NodePoolLaunchConfig generate(final NodePoolVO pool) {
        final NodePoolLaunchConfig config = new NodePoolLaunchConfig();
        config.setCpuRequestsEnabled(true);
        config.setRamRequestsEnabled(true);
        config.setGpuRequestsEnabled(hasGpu(pool));
        config.setCpuRequestsReserved(DEFAULT_CPU_RESERVED);
        config.setGpuRequestsReserved(DEFAULT_GPU_RESERVED);
        config.setRamRequestsReserved(DEFAULT_RAM_RESERVED);
        return config;
    }

    private RunContainerSpec withSelector(final RunContainerSpec requested, final Long poolId) {
        final RunContainerSpec spec = requested != null ? requested : defaultContainerSpec();
        if (!hasSelectorValue(spec)) {
            spec.setSelector(RunContainerSpec.PodAssignSelector.builder()
                    .label(KubernetesConstants.NODE_POOL_ID_LABEL)
                    .value(String.valueOf(poolId))
                    .build());
        }
        return spec;
    }

    private RunContainerSpec defaultContainerSpec() {
        final RunContainerSpec spec = new RunContainerSpec();
        spec.setSkipContainerRequests(false);
        spec.setSecurityContext(ContainerSecurityContext.builder()
                .privileged(true)
                .build());
        return spec;
    }

    private static boolean hasSelectorValue(final RunContainerSpec spec) {
        return Optional.ofNullable(spec.getSelector())
                .map(RunContainerSpec.PodAssignSelector::getValue)
                .filter(StringUtils::isNotBlank)
                .isPresent();
    }

    private boolean hasGpu(final NodePoolVO pool) {
        final InstanceOfferRequestVO request = new InstanceOfferRequestVO();
        request.setRegionId(pool.getRegionId());
        return ListUtils.emptyIfNull(instanceOfferDao.loadInstanceTypes(request)).stream()
                .filter(type -> pool.getInstanceType().equals(type.getName()))
                .findFirst()
                .map(InstanceType::getGpu)
                .map(gpu -> gpu > 0)
                .orElseGet(() -> {
                    log.warn("Instance type {} not found for region {}; assuming no GPU for the launch config "
                            + "of node pool {}", pool.getInstanceType(), pool.getRegionId(), pool.getId());
                    return false;
                });
    }
}
