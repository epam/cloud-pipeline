/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service.impl.loader;

import com.epam.pipeline.billingreportagent.model.ComputeType;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PipelineRunLoader implements EntityLoader<PipelineRunWithType> {

    @Autowired
    private CloudPipelineAPIClient apiClient;

    @Override
    public List<EntityContainer<PipelineRunWithType>> loadAllEntities() {
        return loadAllEntitiesActiveInPeriod(LocalDate.ofEpochDay(0).atStartOfDay(), LocalDateTime.now());
    }

    @Override
    public List<EntityContainer<PipelineRunWithType>> loadAllEntitiesActiveInPeriod(final LocalDateTime from,
                                                                                    final LocalDateTime to) {
        final Map<String, EntityWithMetadata<PipelineUser>> usersWithMetadata = prepareUsers(apiClient);

        final List<PipelineRun> runs =
                apiClient.loadAllPipelineRunsActiveInPeriod(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(from),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(to));

        final Map<Long, List<InstanceType>> regionOffers = runs.stream()
                .map(PipelineRun::getInstance)
                .map(RunInstance::getCloudRegionId)
                .distinct()
                .collect(Collectors
                        .toMap(Function.identity(), regionId -> apiClient.loadAllInstanceTypesForRegion(regionId)));

        return runs
                .stream()
                .map(run -> EntityContainer.<PipelineRunWithType>builder()
                        .entity(new PipelineRunWithType(run, loadDisks(run), getRunType(run, regionOffers)))
                        .owner(usersWithMetadata.get(run.getOwner()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<NodeDisk> loadDisks(final PipelineRun run) {
        return ListUtils.emptyIfNull(apiClient.loadNodeDisks(run.getInstance().getNodeId()));
    }

    private ComputeType getRunType(final PipelineRun run, final Map<Long, List<InstanceType>> regionOffers) {
        return regionOffers.get(run.getInstance().getCloudRegionId())
                .stream()
                .filter(instanceOffer -> instanceOffer.getName().equals(run.getInstance().getNodeType()))
                .findAny()
                .filter(instanceOffer -> instanceOffer.getGpu() > 0)
                .map(instanceOffer -> ComputeType.GPU)
                .orElse(ComputeType.CPU);
    }

}
