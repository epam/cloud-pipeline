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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.storage;

import com.epam.pipeline.elasticsearchagent.model.DataStorageDoc;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.AbstractCloudPipelineEntityLoader;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class DataStorageLoader extends AbstractCloudPipelineEntityLoader<DataStorageDoc> {

    public DataStorageLoader(final CloudPipelineAPIClient apiClient) {
        super(apiClient);
    }

    @Override
    protected DataStorageDoc fetchEntity(final Long id) {
        AbstractDataStorage dataStorage = getApiClient().loadDataStorage(id);
        List<? extends AbstractCloudRegion> cloudRegions = getApiClient().loadAllRegions();
        DataStorageDoc.DataStorageDocBuilder docBuilder = DataStorageDoc
                .builder()
                .storage(dataStorage);
        if (dataStorage instanceof S3bucketDataStorage) {
            docBuilder.regionName(
                    cloudRegions.stream()
                            .filter(region -> region.getProvider() == CloudProvider.AWS
                                    && region.getId().equals(((S3bucketDataStorage) dataStorage).getRegionId()))
                            .findFirst()
                            .map(AbstractCloudRegion::getRegionCode)
                            .orElse(StringUtils.EMPTY));
        }
        return docBuilder.build();
    }

    @Override
    protected String getOwner(final DataStorageDoc entity) {
        return entity.getStorage().getOwner();
    }

    @Override
    protected AclClass getAclClass(final DataStorageDoc entity) {
        return entity.getStorage().getAclClass();
    }
}
