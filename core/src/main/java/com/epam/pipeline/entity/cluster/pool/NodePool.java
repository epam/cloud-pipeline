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

/**
 * A pool of pre-provisioned compute nodes.
 *
 * <p>An ACL-secured entity with no parent: permissions are granted on the pool itself, and govern who may
 * read, edit, delete or share it. Who may <em>schedule</em> runs on it is a separate question, answered by
 * {@link #filter} and enforced when a run is assigned - an ACL grant is not a scheduling grant.
 *
 * <p>{@code id}, {@code name}, {@code owner}, {@code mask} and {@code locked} are inherited and must not be
 * redeclared here.
 */
@Data
// `usage` is excluded from equality on purpose: it is not persisted, so two instances of the same row would
// otherwise compare unequal depending on whether the code path that loaded them happened to fill it in.
@EqualsAndHashCode(callSuper = true, exclude = "usage")
public class NodePool extends AbstractSecuredEntity {

    private final AclClass aclClass = AclClass.NODE_POOL;
    // There is no parent for a node pool
    private final AbstractSecuredEntity parent = null;

    /**
     * When this pool row was created. Distinct from the inherited {@code createdDate}, which this entity
     * does not persist - do not conflate the two.
     */
    private LocalDateTime created;
    private Long regionId;
    private String instanceType;
    private int instanceDisk;
    private PriceType priceType;
    private Set<String> dockerImages;
    /**
     * @deprecated the image is part of {@link #amiConfiguration}. Still honoured: it becomes that configuration's image
     *             when the pool is created or edited, and names the image of a pool without one - see
     *             {@link #resolveInstanceImage()}.
     */
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

    /**
     * How this pool's nodes are shared between runs. {@code STANDARD} for every pool that predates the
     * column, and defaulted here rather than left null so a freshly built pool equals one loaded from the
     * database - whose column is {@code NOT NULL DEFAULT 'STANDARD'}.
     */
    private NodePoolType poolType = NodePoolType.STANDARD;

    /**
     * The window this pool is usable in. Null means unbounded, which is the existing behaviour. Set from the
     * linked capacity reservation once it becomes active.
     */
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /**
     * How runs divide one node of a sharable pool. Never populated yet - see {@link NodePoolLaunchConfig}.
     */
    private NodePoolLaunchConfig launchConfig;

    /**
     * Whether this pool is backed by a capacity reservation.
     */
    private boolean capacityReservation;

    /**
     * How this pool's nodes are launched: the one the pool's create or update request sent, or a copy of the
     * region's matching {@code amis} rule taken when the pool was created without one. A capacity reservation writes
     * its target, zone and subnet into it while it is live. Absent for a pool outside AWS, and for one no rule matched
     * and no image was given for - their nodes launch as the region's rules say.
     */
    private AMIConfiguration amiConfiguration;

    /**
     * How many of this pool's nodes are currently occupied. Populated on read from the cluster when the
     * caller asks for it, not persisted - there is no column behind it.
     */
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

    /**
     * The image this pool's nodes launch from: {@link #amiConfiguration}'s {@code ami} when set, else the deprecated
     * {@link #instanceImage}. Null leaves the choice to the region's {@code amis} rules.
     */
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
