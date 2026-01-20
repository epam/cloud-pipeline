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

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.pipeline.RunInstance;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class NodePool implements NodePoolInfo {

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
    private PoolFilter filter;
    private boolean autoscaled;
    private Integer minSize;
    private Integer maxSize;
    private Double scaleUpThreshold;
    private Double scaleDownThreshold;
    private Integer scaleStep;
    private Map<String, PoolLabel> kubeLabels;

    public NodePool() {
    }

    public boolean isActive(final LocalDateTime timestamp) {
        if (count == 0) {
            return false;
        }
        return Optional.ofNullable(schedule)
                .map(s -> s.isActive(timestamp))
                .orElse(true);
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

    public RunningInstance toRunningInstance() {
        final RunningInstance runningInstance = new RunningInstance();
        final RunInstance instance = toRunInstance();
        instance.setPoolId(getId());
        runningInstance.setInstance(instance);
        runningInstance.setPrePulledImages(dockerImages);
        runningInstance.setPool(this);
        return runningInstance;
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
        //Only linux is supported for Node pools
        runInstance.setNodePlatform("linux");
        return runInstance;
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public LocalDateTime getCreated() {
        return this.created;
    }

    public Long getRegionId() {
        return this.regionId;
    }

    public String getInstanceType() {
        return this.instanceType;
    }

    public int getInstanceDisk() {
        return this.instanceDisk;
    }

    public PriceType getPriceType() {
        return this.priceType;
    }

    public Set<String> getDockerImages() {
        return this.dockerImages;
    }

    public String getInstanceImage() {
        return this.instanceImage;
    }

    public int getCount() {
        return this.count;
    }

    public NodeSchedule getSchedule() {
        return this.schedule;
    }

    public PoolFilter getFilter() {
        return this.filter;
    }

    public boolean isAutoscaled() {
        return this.autoscaled;
    }

    public Integer getMinSize() {
        return this.minSize;
    }

    public Integer getMaxSize() {
        return this.maxSize;
    }

    public Double getScaleUpThreshold() {
        return this.scaleUpThreshold;
    }

    public Double getScaleDownThreshold() {
        return this.scaleDownThreshold;
    }

    public Integer getScaleStep() {
        return this.scaleStep;
    }

    public Map<String, PoolLabel> getKubeLabels() {
        return this.kubeLabels;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public void setInstanceDisk(int instanceDisk) {
        this.instanceDisk = instanceDisk;
    }

    public void setPriceType(PriceType priceType) {
        this.priceType = priceType;
    }

    public void setDockerImages(Set<String> dockerImages) {
        this.dockerImages = dockerImages;
    }

    public void setInstanceImage(String instanceImage) {
        this.instanceImage = instanceImage;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setSchedule(NodeSchedule schedule) {
        this.schedule = schedule;
    }

    public void setFilter(PoolFilter filter) {
        this.filter = filter;
    }

    public void setAutoscaled(boolean autoscaled) {
        this.autoscaled = autoscaled;
    }

    public void setMinSize(Integer minSize) {
        this.minSize = minSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public void setScaleUpThreshold(Double scaleUpThreshold) {
        this.scaleUpThreshold = scaleUpThreshold;
    }

    public void setScaleDownThreshold(Double scaleDownThreshold) {
        this.scaleDownThreshold = scaleDownThreshold;
    }

    public void setScaleStep(Integer scaleStep) {
        this.scaleStep = scaleStep;
    }

    public void setKubeLabels(Map<String, PoolLabel> kubeLabels) {
        this.kubeLabels = kubeLabels;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof NodePool)) return false;
        final NodePool other = (NodePool) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$id = this.getId();
        final Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$created = this.getCreated();
        final Object other$created = other.getCreated();
        if (this$created == null ? other$created != null : !this$created.equals(other$created)) return false;
        final Object this$regionId = this.getRegionId();
        final Object other$regionId = other.getRegionId();
        if (this$regionId == null ? other$regionId != null : !this$regionId.equals(other$regionId)) return false;
        final Object this$instanceType = this.getInstanceType();
        final Object other$instanceType = other.getInstanceType();
        if (this$instanceType == null ? other$instanceType != null : !this$instanceType.equals(other$instanceType))
            return false;
        if (this.getInstanceDisk() != other.getInstanceDisk()) return false;
        final Object this$priceType = this.getPriceType();
        final Object other$priceType = other.getPriceType();
        if (this$priceType == null ? other$priceType != null : !this$priceType.equals(other$priceType)) return false;
        final Object this$dockerImages = this.getDockerImages();
        final Object other$dockerImages = other.getDockerImages();
        if (this$dockerImages == null ? other$dockerImages != null : !this$dockerImages.equals(other$dockerImages))
            return false;
        final Object this$instanceImage = this.getInstanceImage();
        final Object other$instanceImage = other.getInstanceImage();
        if (this$instanceImage == null ? other$instanceImage != null : !this$instanceImage.equals(other$instanceImage))
            return false;
        if (this.getCount() != other.getCount()) return false;
        final Object this$schedule = this.getSchedule();
        final Object other$schedule = other.getSchedule();
        if (this$schedule == null ? other$schedule != null : !this$schedule.equals(other$schedule)) return false;
        final Object this$filter = this.getFilter();
        final Object other$filter = other.getFilter();
        if (this$filter == null ? other$filter != null : !this$filter.equals(other$filter)) return false;
        if (this.isAutoscaled() != other.isAutoscaled()) return false;
        final Object this$minSize = this.getMinSize();
        final Object other$minSize = other.getMinSize();
        if (this$minSize == null ? other$minSize != null : !this$minSize.equals(other$minSize)) return false;
        final Object this$maxSize = this.getMaxSize();
        final Object other$maxSize = other.getMaxSize();
        if (this$maxSize == null ? other$maxSize != null : !this$maxSize.equals(other$maxSize)) return false;
        final Object this$scaleUpThreshold = this.getScaleUpThreshold();
        final Object other$scaleUpThreshold = other.getScaleUpThreshold();
        if (this$scaleUpThreshold == null ? other$scaleUpThreshold != null : !this$scaleUpThreshold.equals(other$scaleUpThreshold))
            return false;
        final Object this$scaleDownThreshold = this.getScaleDownThreshold();
        final Object other$scaleDownThreshold = other.getScaleDownThreshold();
        if (this$scaleDownThreshold == null ? other$scaleDownThreshold != null : !this$scaleDownThreshold.equals(other$scaleDownThreshold))
            return false;
        final Object this$scaleStep = this.getScaleStep();
        final Object other$scaleStep = other.getScaleStep();
        if (this$scaleStep == null ? other$scaleStep != null : !this$scaleStep.equals(other$scaleStep)) return false;
        final Object this$kubeLabels = this.getKubeLabels();
        final Object other$kubeLabels = other.getKubeLabels();
        if (this$kubeLabels == null ? other$kubeLabels != null : !this$kubeLabels.equals(other$kubeLabels))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NodePool;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $created = this.getCreated();
        result = result * PRIME + ($created == null ? 43 : $created.hashCode());
        final Object $regionId = this.getRegionId();
        result = result * PRIME + ($regionId == null ? 43 : $regionId.hashCode());
        final Object $instanceType = this.getInstanceType();
        result = result * PRIME + ($instanceType == null ? 43 : $instanceType.hashCode());
        result = result * PRIME + this.getInstanceDisk();
        final Object $priceType = this.getPriceType();
        result = result * PRIME + ($priceType == null ? 43 : $priceType.hashCode());
        final Object $dockerImages = this.getDockerImages();
        result = result * PRIME + ($dockerImages == null ? 43 : $dockerImages.hashCode());
        final Object $instanceImage = this.getInstanceImage();
        result = result * PRIME + ($instanceImage == null ? 43 : $instanceImage.hashCode());
        result = result * PRIME + this.getCount();
        final Object $schedule = this.getSchedule();
        result = result * PRIME + ($schedule == null ? 43 : $schedule.hashCode());
        final Object $filter = this.getFilter();
        result = result * PRIME + ($filter == null ? 43 : $filter.hashCode());
        result = result * PRIME + (this.isAutoscaled() ? 79 : 97);
        final Object $minSize = this.getMinSize();
        result = result * PRIME + ($minSize == null ? 43 : $minSize.hashCode());
        final Object $maxSize = this.getMaxSize();
        result = result * PRIME + ($maxSize == null ? 43 : $maxSize.hashCode());
        final Object $scaleUpThreshold = this.getScaleUpThreshold();
        result = result * PRIME + ($scaleUpThreshold == null ? 43 : $scaleUpThreshold.hashCode());
        final Object $scaleDownThreshold = this.getScaleDownThreshold();
        result = result * PRIME + ($scaleDownThreshold == null ? 43 : $scaleDownThreshold.hashCode());
        final Object $scaleStep = this.getScaleStep();
        result = result * PRIME + ($scaleStep == null ? 43 : $scaleStep.hashCode());
        final Object $kubeLabels = this.getKubeLabels();
        result = result * PRIME + ($kubeLabels == null ? 43 : $kubeLabels.hashCode());
        return result;
    }
}
