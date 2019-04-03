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

import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AzureRegionCredentials;

public interface RegionCredentialsMapperHelper<D extends AbstractCloudRegionDTO,
        E extends AbstractCloudRegionCredentials> {

    E toEntity(CloudRegionMapper mapper, D dto);

    class NullCredentialsMapper implements RegionCredentialsMapperHelper<AbstractCloudRegionDTO,
            AbstractCloudRegionCredentials> {
        @Override
        public AbstractCloudRegionCredentials toEntity(final CloudRegionMapper mapper,
                                                       final AbstractCloudRegionDTO dto) {
            return null;
        }
    }

    class AzureCredentialsMapper implements RegionCredentialsMapperHelper<AzureRegionDTO, AzureRegionCredentials> {
        @Override
        public AzureRegionCredentials toEntity(final CloudRegionMapper mapper,
                                               final AzureRegionDTO dto) {
            return mapper.toAzureRegionCredentials(dto);
        }
    }
}
