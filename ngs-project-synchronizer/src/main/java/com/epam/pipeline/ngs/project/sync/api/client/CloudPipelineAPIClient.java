/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
 *
 */

package com.epam.pipeline.ngs.project.sync.api.client;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.vo.preprocessing.SampleSheetRegistrationVO;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

@Service
public class CloudPipelineAPIClient {

    private final CloudPipelineAPI cloudPipelineAPI;
    private final CloudPipelineApiExecutor apiExecutor;

    public CloudPipelineAPIClient(final CloudPipelineAPI cloudPipelineAPI,
                                  final CloudPipelineApiExecutor apiExecutor) {
        this.cloudPipelineAPI = cloudPipelineAPI;
        this.apiExecutor = apiExecutor;
    }

    public FolderWithMetadata filterFolders(final Map<String, String> metadata) {
        return apiExecutor.execute(cloudPipelineAPI.filterFolders(metadata));
    }

    public FolderWithMetadata getFolderWithMetadata(final Long id) {
        return apiExecutor.execute(cloudPipelineAPI.loadProject(id, AclClass.FOLDER.name()));
    }

    public Preference getPreference(final String name) {
        return apiExecutor.execute(cloudPipelineAPI.loadPreference(name));
    }

    public AbstractDataStorage findStorageByPath(final String ngsDataPath) {
        return apiExecutor.execute(cloudPipelineAPI.findDataStorage(ngsDataPath));
    }

    public List<AbstractDataStorageItem> listDataStorageItems(final Long storageId, final String path) {
        Assert.notNull(storageId, "storageId should be specified!");
        return apiExecutor.execute(cloudPipelineAPI.loadDataStorageItems(storageId, path, false));
    }

    public MetadataEntity getMetadata(final Long folderId, final String className, final String externalId) {
        return apiExecutor.execute(cloudPipelineAPI.loadMetadataEntityByExternalId(externalId, folderId, className));
    }

    public MetadataClass getMetadataClass(final String id) {
        return apiExecutor.execute(cloudPipelineAPI.loadMetadataClassByNameOrId(id));
    }

    public MetadataClass registerMetadataClass(final String name) {
        return apiExecutor.execute(cloudPipelineAPI.registerMetadataClass(name));
    }

    public MetadataEntity createMetadata(final MetadataEntityVO entityVO) {
        return apiExecutor.execute(cloudPipelineAPI.saveMetadataEntity(entityVO));
    }

    public MetadataEntity registerSampleSheet(final SampleSheetRegistrationVO registrationVO, final boolean overwrite) {
        return apiExecutor.execute(cloudPipelineAPI.registerSampleSheetFile(registrationVO, overwrite));
    }

    public DataStorageDownloadFileUrl getStorageItemContent(final Long storageId, final String internalStoragePath) {
        return apiExecutor.execute(cloudPipelineAPI.getDataStorageItemUrlToDownload(storageId, internalStoragePath));
    }
}
