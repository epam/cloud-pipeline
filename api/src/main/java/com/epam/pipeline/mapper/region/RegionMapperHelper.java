/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.mapper.region;

import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.GCPRegion;

public interface RegionMapperHelper<D extends AbstractCloudRegionDTO, E extends AbstractCloudRegion> {

    E toEntity(CloudRegionMapper mapper, D dto);
    D toDTO(CloudRegionMapper mapper, E entity);

    class AWSRegionMapper implements RegionMapperHelper<AWSRegionDTO, AwsRegion> {

        @Override
        public AwsRegion toEntity(final CloudRegionMapper mapper, final AWSRegionDTO dto) {
            return mapper.toAwsRegion(dto);
        }

        @Override
        public AWSRegionDTO toDTO(final CloudRegionMapper mapper, final AwsRegion entity) {
            return mapper.toAwsRegionDTO(entity);
        }
    }

    class AzureRegionMapper implements RegionMapperHelper<AzureRegionDTO, AzureRegion> {

        @Override
        public AzureRegion toEntity(final CloudRegionMapper mapper, final AzureRegionDTO dto) {
            return mapper.toAzureRegion(dto);
        }

        @Override
        public AzureRegionDTO toDTO(final CloudRegionMapper mapper, final AzureRegion entity) {
            return mapper.toAzureRegionDTO(entity);
        }
    }

    class GCPRegionMapper implements RegionMapperHelper<GCPRegionDTO, GCPRegion> {
        @Override
        public GCPRegion toEntity(final CloudRegionMapper mapper, final GCPRegionDTO dto) {
            return mapper.toGCPRegion(dto);
        }

        @Override
        public GCPRegionDTO toDTO(final CloudRegionMapper mapper, final GCPRegion entity) {
            return mapper.toGCPRegionDTO(entity);
        }
    }
}
