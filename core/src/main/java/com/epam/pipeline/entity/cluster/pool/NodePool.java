/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.pipeline.RunInstance;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Data
public class NodePool {

    private Long id;
    private String name;
    private LocalDateTime created;
    private Long regionId;
    private String instanceType;
    private int instanceDisk;
    private PriceType priceType;
    private Set<String> dockerImages;
    private String instanceImage;
    private int count;
    private NodeSchedule schedule;

    public boolean isActive(final LocalDateTime timestamp) {
        return Optional.ofNullable(schedule)
                .map(s -> s.isActive(timestamp))
                .orElse(false);
    }

    @Override
    public String toString() {
        return "NodePool{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", regionId=" + regionId +
                ", instanceType='" + instanceType + '\'' +
                ", instanceDisk=" + instanceDisk +
                ", priceType=" + priceType +
                ", dockerImages=" + dockerImages +
                ", instanceImage='" + instanceImage + '\'' +
                ", count=" + count +
                '}';
    }

    public RunInstance toRunInstance() {
        final RunInstance runInstance = new RunInstance();
        runInstance.setNodeType(instanceType);
        runInstance.setCloudRegionId(regionId);
        runInstance.setNodeDisk(instanceDisk);
        runInstance.setEffectiveNodeDisk(instanceDisk);
        runInstance.setSpot(PriceType.SPOT.equals(priceType));
        runInstance.setNodeImage(instanceImage);
        runInstance.setPrePulledDockerImages(dockerImages);
        return runInstance;
    }
}
