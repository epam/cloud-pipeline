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

package com.epam.pipeline.entity.cluster.pool;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.security.acl.AclClass;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true, exclude = "usage")
public class NodePool extends AbstractSecuredEntity {

    private final AclClass aclClass = AclClass.NODE_POOL;
    private final AbstractSecuredEntity parent = null;

    private LocalDateTime created;
    private Long regionId;
    private String instanceType;
    private int instanceDisk;
    private PriceType priceType;
    private Set<String> dockerImages;
    @Deprecated
    private String instanceImage;
    private int count;
    private NodeSchedule schedule;
    private PoolFilter filter;
    private boolean autoscaled;
    private Integer minSize;
    private Integer maxSize;
    private Double scaleUpThreshold;
    private Double scaleDownThreshold;
    private Integer scaleStep;
    private Map<String, PoolLabel> kubeLabels;

    private NodePoolType poolType = NodePoolType.STANDARD;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private NodePoolLaunchConfig launchConfig;

    private boolean capacityReservation;

    private AMIConfiguration amiConfiguration;

    private Long usage;

    public boolean isActive(final LocalDateTime timestamp) {
        if (count == 0) {
            return false;
        }
        if (startDate != null && timestamp.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && timestamp.isAfter(endDate)) {
            return false;
        }
        return Optional.ofNullable(schedule)
                .map(s -> s.isActive(timestamp))
                .orElse(true);
    }

    @Override
    public String toString() {
        return "NodePool{" +
                "id=" + getId() +
                ", name='" + getName() + '\'' +
                ", regionId=" + regionId +
                ", instanceType='" + instanceType + '\'' +
                ", instanceDisk=" + instanceDisk +
                ", priceType=" + priceType +
                ", dockerImages=" + dockerImages +
                ", instanceImage='" + instanceImage + '\'' +
                ", count=" + count +
                '}';
    }

    public RunningInstance toRunningInstance() {
        final RunningInstance runningInstance = new RunningInstance();
        final RunInstance instance = toRunInstance();
        instance.setPoolId(getId());
        runningInstance.setInstance(instance);
        runningInstance.setPrePulledImages(dockerImages);
        runningInstance.setPool(this);
        return runningInstance;
    }

    @SuppressWarnings("deprecation")
    public String resolveInstanceImage() {
        return Optional.ofNullable(amiConfiguration)
                .map(AMIConfiguration::getAmi)
                .filter(ami -> !ami.trim().isEmpty())
                .orElse(instanceImage);
    }

    public RunInstance toRunInstance() {
        final RunInstance runInstance = new RunInstance();
        runInstance.setNodeType(instanceType);
        runInstance.setCloudRegionId(regionId);
        runInstance.setNodeDisk(instanceDisk);
        runInstance.setEffectiveNodeDisk(instanceDisk);
        runInstance.setSpot(PriceType.SPOT.equals(priceType));
        runInstance.setNodeImage(resolveInstanceImage());
        runInstance.setPrePulledDockerImages(dockerImages);
        //Only linux is supported for Node pools
        runInstance.setNodePlatform("linux");
        return runInstance;
    }
}
