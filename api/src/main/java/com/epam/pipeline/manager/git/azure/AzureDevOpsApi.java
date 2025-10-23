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
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AzureDevOpsApi {

    String ORGANIZATION = "organization";
    String PROJECT = "project";
    String REPOSITORY = "repository";
    String OBJECT_ID = "objectId";
    String VERSION = "searchCriteria.itemVersion.version";
    String VERSION_TYPE = "searchCriteria.itemVersion.versionType";
    String FILTER = "filter";
    String COMMIT = "commit";

    @GET("{organization}/{project}/_apis/git/repositories/{repository}?api-version=7.1")
    Call<AzureDevOpsRepository> getRepository(@Path(ORGANIZATION) String organization,
                                              @Path(PROJECT) String project,
                                              @Path(REPOSITORY) String repository);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/refs?api-version=7.1")
    Call<AzureDevOpsObjectList<AzureDevOpsRef>> getRefs(@Path(ORGANIZATION) String organization,
                                                        @Path(PROJECT) String project,
                                                        @Path(REPOSITORY) String repository,
                                                        @Query(FILTER) String filter);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/annotatedtags/{objectId}?api-version=7.1")
    Call<AzureDevOpsTag> getTag(@Path(ORGANIZATION) String organization,
                                @Path(PROJECT) String project,
                                @Path(REPOSITORY) String repository,
                                @Path(OBJECT_ID) String objectId);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/commits?api-version=7.1&$top=1")
    Call<AzureDevOpsObjectList<AzureDevOpsCommit>> getLastCommit(@Path(ORGANIZATION) String organization,
                                                                 @Path(PROJECT) String project,
                                                                 @Path(REPOSITORY) String repository,
                                                                 @Query(VERSION) String version,
                                                                 @Query(VERSION_TYPE) String versionType);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/commits/{commit}?api-version=7.1")
    Call<AzureDevOpsCommit> getCommit(@Path(ORGANIZATION) String organization,
                                      @Path(PROJECT) String project,
                                      @Path(REPOSITORY) String repository,
                                      @Path(COMMIT) String commit);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/items?api-version=7.1")
    Call<AzureDevOpsObjectList<AzureDevOpsItem>> getItems(@Path(ORGANIZATION) String organization,
                                                          @Path(PROJECT) String project,
                                                          @Path(REPOSITORY) String repository,
                                                          @Query("scopePath") String path,
                                                          @Query("recursionLevel") String recursionLevel,
                                                          @Query("versionDescriptor.version") String version,
                                                          @Query("versionDescriptor.versionType") String versionType);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/items?api-version=7.1")
    Call<ResponseBody> getItem(@Path(ORGANIZATION) String organization,
                               @Path(PROJECT) String project,
                               @Path(REPOSITORY) String repository,
                               @Query("path") String path,
                               @Query("versionDescriptor.version") String version,
                               @Query("versionDescriptor.versionType") String versionType);

    @GET("{organization}/{project}/_apis/git/repositories/{repository}/items?api-version=7.1&$format=json")
    Call<AzureDevOpsItem> getItemInfo(@Path(ORGANIZATION) String organization,
                                      @Path(PROJECT) String project,
                                      @Path(REPOSITORY) String repository,
                                      @Query("path") String path,
                                      @Query("versionDescriptor.version") String version,
                                      @Query("versionDescriptor.versionType") String versionType);
}
