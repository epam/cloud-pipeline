/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.client.pipeline;

import com.epam.pipeline.entity.app.ApplicationInfo;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageTag;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.dts.submission.DtsRegistry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.log.LogRequest;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.rest.Result;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import com.epam.pipeline.vo.FilterNodesVO;
import com.epam.pipeline.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.vo.RunStatusVO;
import com.epam.pipeline.vo.cluster.pool.NodePoolUsage;
import com.epam.pipeline.vo.data.storage.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.vo.data.storage.DataStorageTagUpsertBatchRequest;
import com.epam.pipeline.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.vo.notification.NotificationMessageVO;
import com.epam.pipeline.vo.user.OnlineUsers;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CloudPipelineAPI {

    String ID = "id";
    String RUN_ID = "runId";
    String PARENT_ID = "parentId";
    String CLASS = "class";
    String TEMPLATE_NAME = "templateName";
    String ENTITY_CLASS = "entityClass";
    String KEY = "key";
    String VALUE = "value";
    String REGISTRY = "registry";
    String TOOL_IDENTIFIER = "image";
    String VERSION = "version";
    String PATH = "path";
    String FROM = "from";
    String TO = "to";
    String TOOL_ID = "toolId";
    String REGION_ID = "regionId";


    @POST("run/{runId}/status")
    Call<Result<PipelineRun>> updateRunStatus(@Path(RUN_ID) Long runId,
                                              @Body RunStatusVO statusUpdate);

    @POST("run/{runId}/log")
    Call<Result<RunLog>> saveLogs(@Path(RUN_ID) Long runId,
                                  @Body RunLog log);

    @POST("run/{runId}/instance")
    Call<Result<PipelineRun>> updateRunInstance(@Path(RUN_ID) Long runId,
                                                @Body RunInstance instance);

    @GET("run/{runId}")
    Call<Result<PipelineRun>> loadPipelineRun(@Path(RUN_ID) Long runId);

    @GET("run/{runId}/logs")
    Call<Result<List<RunLog>>> loadLogs(@Path(RUN_ID) Long runId);

    @POST("run/search")
    Call<Result<PagedResult<List<PipelineRun>>>> searchPipelineRuns(@Body PagingRunFilterExpressionVO filterVO);

    @POST("metadata/load")
    Call<Result<List<MetadataEntry>>> loadFolderMetadata(@Body List<EntityVO> entities);

    @Multipart
    @POST("metadata/upload")
    Call<Result<MetadataEntry>> uploadMetadata(@Query(ID) Long id,
                                               @Query(CLASS) AclClass className,
                                               @Part MultipartBody.Part file);

    @GET("metadata/search")
    Call<Result<List<EntityVO>>> searchMetadata(@Query(KEY) String key,
                                                @Query(VALUE) String value,
                                                @Query(ENTITY_CLASS) AclClass entityClass);

    @Multipart
    @POST("metadataEntity/upload")
    Call<Result<List<MetadataEntity>>> uploadMetadataEntity(@Query(PARENT_ID) Long parentId,
                                                            @Part MultipartBody.Part file);

    @GET("folder/find")
    Call<Result<Folder>> findFolder(@Query(ID) String fullyQualifiedName);

    @POST("folder/register")
    Call<Result<Folder>> registerFolder(@Body Folder folder, @Query(TEMPLATE_NAME) String templateName);

    @GET("pipeline/loadAll")
    Call<Result<List<Pipeline>>> loadAllPipelines();

    @GET("pipeline/{id}/versions")
    Call<Result<List<Revision>>> loadPipelineVersions(@Path(ID) Long pipelineId);

    @GET("pipeline/{id}/clone")
    Call<Result<String>> getGitCloneUrl(@Path(ID) Long pipelineId);

    @GET("pipeline/{id}/file")
    Call<byte[]> loadFileContent(@Path(ID) Long pipelineId, @Query(VERSION) String version, @Query(PATH) String path);

    @GET("pipeline/{id}/file/truncate")
    Call<byte[]> loadTruncatedFileContent(@Path(ID) Long pipelineId, @Query(VERSION) String version,
                                                  @Query(PATH) String path, @Query("byteLimit") Integer byteLimit);

    @GET("datastorage/loadAll")
    Call<Result<List<AbstractDataStorage>>> loadAllDataStorages();

    @GET("datastorage/availableWithMounts")
    Call<Result<List<DataStorageWithShareMount>>> loadAllDataStoragesWithMounts();

    @GET("datastorage/{id}/load")
    Call<Result<AbstractDataStorage>> loadDataStorage(@Path(ID) Long storageId);

    @GET("datastorage/findByPath")
    Call<Result<AbstractDataStorage>> findStorageByPath(@Query(ID) String path);

    @GET("datastorage/{id}/content")
    Call<Result<DataStorageItemContent>> getStorageItemContent(@Path(ID) Long storageId,
                                                               @Query(PATH) String path);
    @POST("datastorage/{id}/content")
    Call<Result<DataStorageFile>> createStorageItem(@Path(ID) Long storageId,
                                                    @Query(PATH) String path,
                                                    @Body String content);

    @PUT("datastorage/{id}/tags/batch/insert")
    Call<Result<Object>> insertDataStorageTags(@Path(ID) Long storageId,
                                               @Body DataStorageTagInsertBatchRequest request);

    @PUT("datastorage/{id}/tags/batch/upsert")
    Call<Result<Object>> upsertDataStorageTags(@Path(ID) Long id,
                                               @Body DataStorageTagUpsertBatchRequest request);

    @POST("datastorage/{id}/tags/batch/load")
    Call<Result<List<DataStorageTag>>> loadDataStorageObjectTags(@Path(ID) Long storageId,
                                                                 @Body DataStorageTagLoadBatchRequest request);

    @GET("datastorage/{id}/generateUrl")
    Call<Result<DataStorageDownloadFileUrl>> generateDownloadUrl(@Path(ID) Long storageId, @Query(PATH) String path);

    @GET("datastorage/path/usage")
    Call<Result<StorageUsage>> getStorageUsage(@Query(ID) String id, @Query(PATH) String path);

    @GET("users")
    Call<Result<List<PipelineUser>>> loadAllUsers();

    @POST("users/online")
    Call<Result<OnlineUsers>> saveOnlineUsers();

    @DELETE("users/online")
    Call<Result<Boolean>> deleteExpiredOnlineUsers(@Query("date") String date);

    @GET("user")
    Call<Result<PipelineUser>> loadUserByName(@Query("name") String name);

    @GET("user/find")
    Call<Result<List<PipelineUser>>> loadUsersByPrefix(@Query("prefix") String prefix);

    @GET("whoami")
    Call<Result<PipelineUser>> whoami();

    @POST("datastorage/tempCredentials/")
    Call<Result<TemporaryCredentials>> generateTemporaryCredentials(@Body List<DataStorageAction> actions);

    @GET("cloud/region")
    Call<Result<List<AbstractCloudRegion>>> loadAllRegions();

    @GET("aws/region")
    Call<Result<List<AwsRegion>>> loadAwsRegions();

    @GET("cloud/region/{regionId}")
    Call<Result<AbstractCloudRegion>> loadRegion(@Path("regionId") Long regionId);

    @GET("tool/load")
    Call<Result<Tool>> loadTool(@Query(REGISTRY) String registry, @Query(TOOL_IDENTIFIER) String identifier);

    @GET("toolGroup")
    Call<Result<ToolGroup>> loadToolGroup(@Query(ID) String toolGroupId);

    @GET("tool/{toolId}/attributes ")
    Call<Result<ToolDescription>> loadToolAttributes(@Path(TOOL_ID) Long toolId);

    @GET("dockerRegistry/loadTree")
    Call<Result<DockerRegistryList>> loadAllRegistries();

    @GET("dockerRegistry/{id}/load")
    Call<Result<DockerRegistry>> loadDockerRegistry(@Path(ID) Long dockerRegistryId);

    @GET("issues/{issueId}")
    Call<Result<Issue>> loadIssue(@Path("issueId") Long issueId);

    @GET("configuration/{id}")
    Call<Result<RunConfiguration>> loadRunConfiguration(@Path(ID) Long id);

    @GET("pipeline/find")
    Call<Result<Pipeline>> loadPipeline(@Query(ID) String identifier);

    @GET("metadataEntity/{id}/load")
    Call<Result<MetadataEntity>> loadMetadeataEntity(@Path(ID) Long id);

    @GET("pipeline/findByUrl")
    Call<Result<Pipeline>> loadPipelineByUrl(@Query("url") String url);

    @GET("permissions")
    Call<Result<EntityPermissionVO>> loadEntityPermissions(@Query(ID) Long id, @Query("aclClass") AclClass aclClass);

    @GET("pipeline/{id}/repository")
    Call<Result<List<GitRepositoryEntry>>> loadRepositoryContent(@Path(ID) Long id, @Query(VERSION) String version,
                                                                 @Query(PATH) String path);

    // Node methods
    @POST("cluster/node/filter")
    Call<Result<List<NodeInstance>>> findNodes(@Body FilterNodesVO filterNodesVO);

    //Notification methods
    @POST("notification/message")
    Call<Result<NotificationMessage>> createNotification(@Body NotificationMessageVO notification);

    @GET("run/activity")
    Call<Result<List<PipelineRun>>> loadRunsActivityStats(@Query(FROM) String from, @Query(TO) String to,
                                                          @Query("archive") boolean archive);

    @GET("run/pools/{id}")
    Call<Result<List<PipelineRun>>> loadRunsByPool(@Path(ID) Long poolId);

    @GET("cluster/instance/loadAll")
    Call<Result<List<InstanceType>>> loadAllInstanceTypesForRegion(@Query(REGION_ID) Long regionId);

    @GET("cluster/instance/loadAll")
    Call<Result<List<InstanceType>>> loadAllInstanceTypes();

    @GET("cluster/node/{id}/disks")
    Call<Result<List<NodeDisk>>> loadNodeDisks(@Path(ID) String nodeId);

    @GET("cluster/pool")
    Call<Result<List<NodePool>>> loadNodePools();

    @GET("dts/{id}")
    Call<Result<DtsRegistry>> loadDts(@Path(ID) String dtsNameOrId);

    @HTTP(method = "DELETE", path = "dts/{id}/preferences", hasBody = true)
    Call<Result<DtsRegistry>> deleteDtsPreferences(@Path(ID) String dtsNameOrId,
                                                   @Body DtsRegistryPreferencesRemovalVO removalVO);

    @PUT("dts/{id}/heartbeat")
    Call<Result<DtsRegistry>> updateDtsHeartbeat(@Path(ID) String dtsId);

    @GET("preferences/{key}")
    Call<Result<Preference>> loadPreference(@Path(KEY) String preferenceName);

    @GET("preferences")
    Call<Result<List<Preference>>> loadAllPreference();

    @GET("filesharemount/{id}")
    Call<Result<FileShareMount>> loadShareMount(@Path(ID) Long id);

    @GET("app/info")
    Call<Result<ApplicationInfo>> fetchVersion();

    @POST("cluster/pool/usage")
    Call<Result<List<NodePoolUsage>>> saveNodePoolUsage(@Body List<NodePoolUsage> records);

    @DELETE("cluster/pool/usage")
    Call<Result<Boolean>> deleteExpiredNodePoolUsage(@Query("date") LocalDate date);

    @GET("datastorage/{id}/lifecycle/restore/effectiveHierarchy")
    Call<Result<List<StorageRestoreAction>>> loadDataStorageRestoreHierarchy(
            @Path(ID) long datastorageId, @Query(PATH) String path,
            @Query("pathType") StorageRestorePathType pathType,
            @Query("recursive") boolean recursive);

    @GET("lustre")
    Call<Result<LustreFS>> getLustre(@Query("mountName") String mountName,
                                     @Query("regionId") Long regionId);

    @POST("log/group")
    Call<Result<Map<String, Long>>> getSystemLogsGrouped(@Body LogRequest logRequest);

    @POST("runs/archive")
    Call<Result<Boolean>> archiveRuns();
}
