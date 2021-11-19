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

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.service.EntityLoader;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CloudRegionLoader implements EntityLoader<AbstractCloudRegion> {

    private final CloudPipelineAPIClient apiClient;

    public CloudRegionLoader(final CloudPipelineAPIClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<EntityContainer<AbstractCloudRegion>> loadAllEntities() {
        return apiClient.loadAllCloudRegions().stream()
            .map(region -> EntityContainer.<AbstractCloudRegion>builder()
                .entity(region)
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public List<EntityContainer<AbstractCloudRegion>> loadAllEntitiesActiveInPeriod(final LocalDateTime from,
                                                                                    final LocalDateTime to) {
        return loadAllEntities();
    }
}
