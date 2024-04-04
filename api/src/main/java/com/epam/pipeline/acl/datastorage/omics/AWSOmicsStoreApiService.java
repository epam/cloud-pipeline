/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.datastorage.omics;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.AbstractAWSOmicsDataStorage;
import com.epam.pipeline.entity.datastorage.omics.*;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.omics.AWSOmicsStoreManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class AWSOmicsStoreApiService {

    private final DataStorageManager dataStorageManager;
    private final AWSOmicsStoreManager omicsStoreManager;
    private final MessageHelper messageHelper;

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public AWSOmicsFileImportJob importOmicsFiles(final Long id, final AWSOmicsFileImportRequest importRequest) {
        return omicsStoreManager.importOmicsFiles(fetchDataStorage(id), importRequest);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_READ)
    public AWSOmicsFileImportJobListing listImportJobs(final Long id, final String nextToken,
                                                       final Integer pageSize,
                                                       final AWSOmicsFileImportJobFilter filter) {
        return omicsStoreManager.listImportJobs(fetchDataStorage(id), nextToken, pageSize, filter);
    }

    @PreAuthorize(AclExpressions.STORAGE_ID_WRITE)
    public AWSOmicsFilesActivationJob activateOmicsFiles(final Long id, final AWSOmicsFilesActivationRequest request) {
        return omicsStoreManager.activateOmicsFiles(fetchDataStorage(id), request);
    }

    private AbstractAWSOmicsDataStorage fetchDataStorage(final Long storageId) {
        final AbstractDataStorage dataStorage = dataStorageManager.load(storageId);
        Assert.isTrue(
                DataStorageType.AWS_OMICS_REF.equals(dataStorage.getType()) ||
                        DataStorageType.AWS_OMICS_SEQ.equals(dataStorage.getType()),
                "DataStorage should be of type [" + DataStorageType.AWS_OMICS_SEQ + ", "
                        + DataStorageType.AWS_OMICS_REF + "] to import AWS Omics files."
        );
        return (AbstractAWSOmicsDataStorage) dataStorage;
    }
}
