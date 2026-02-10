/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.azure;

import com.epam.pipeline.entity.git.azure.AzureDevOpsCommit;
import com.epam.pipeline.entity.git.azure.AzureDevOpsItem;
import com.epam.pipeline.entity.git.azure.AzureDevOpsObjectList;
import com.epam.pipeline.entity.git.azure.AzureDevOpsRef;
import com.epam.pipeline.entity.git.azure.AzureDevOpsRepository;
import com.epam.pipeline.entity.git.azure.AzureDevOpsTag;
import com.epam.pipeline.manager.git.ApiBuilder;
import com.epam.pipeline.manager.git.RestApiUtils;

public class AzureDevOpsClient {
    private static final String AUTHORIZATION = "Authorization";

    private final AzureDevOpsApi azureDevOpsApi;
    private final String organization;
    private final String project;
    private final String repository;

    public AzureDevOpsClient(final String baseUrl, final String credentials, final String dateFormat,
                             final String organization, final String project, final String repository) {
        this.azureDevOpsApi = buildClient(baseUrl, credentials, dateFormat);
        this.organization = organization;
        this.project = project;
        this.repository = repository;
    }

    public AzureDevOpsRepository getRepository() {
        return RestApiUtils.execute(azureDevOpsApi.getRepository(organization, project, repository));
    }

    public AzureDevOpsObjectList<AzureDevOpsRef> getRefs(final String refFilter) {
        return RestApiUtils.execute(azureDevOpsApi.getRefs(organization, project, repository, refFilter));
    }

    public AzureDevOpsTag getTag(final String objectId) {
        return RestApiUtils.execute(azureDevOpsApi.getTag(organization, project, repository, objectId));
    }

    public AzureDevOpsObjectList<AzureDevOpsCommit> getLastCommit(final String version, final String versionType) {
        return RestApiUtils.execute(azureDevOpsApi
                .getLastCommit(organization, project, repository, version, versionType));
    }

    public AzureDevOpsCommit getCommit(final String commitId) {
        return RestApiUtils.execute(azureDevOpsApi.getCommit(organization, project, repository, commitId));
    }

    public AzureDevOpsObjectList<AzureDevOpsItem> getItems(final String path, final String recursionLevel,
                                                           final String version, final String versionType) {
        return RestApiUtils.execute(azureDevOpsApi.getItems(organization, project, repository, path, recursionLevel,
                version, versionType));
    }

    public byte[] getItem(final String path, final String version, final String versionType) {
        return RestApiUtils.getFileContent(azureDevOpsApi
                .getItem(organization, project, repository, path, version, versionType));
    }

    public byte[] getItem(final String path, final String version, final String versionType, final int byteLimit) {
        return RestApiUtils.getFileContent(azureDevOpsApi
                .getItem(organization, project, repository, path, version, versionType), byteLimit);
    }

    public AzureDevOpsItem getItemInfo(final String path, final String version, final String versionType) {
        return RestApiUtils.execute(azureDevOpsApi.getItemInfo(organization, project, repository, path,
                version, versionType));
    }

    private AzureDevOpsApi buildClient(final String baseUrl, final String credentials, final String dataFormat) {
        return new ApiBuilder<>(AzureDevOpsApi.class, baseUrl, AUTHORIZATION, credentials, dataFormat).build();
    }
}
