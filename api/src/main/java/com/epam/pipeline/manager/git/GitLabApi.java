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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.*;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

import java.util.List;

public interface GitLabApi {

    String FILE_PATH = "file_path";
    String REF = "ref";
    String PROJECT = "project";
    String PATH = "path";
    String RECURSIVE = "recursive";
    String REF_NAME = "ref_name";
    String USER_ID = "user_id";
    String PRIVATE_TOKEN = "PRIVATE-TOKEN";
    String API_VERSION = "api_version";

    /**
     * @param userName The name of the GitLab user
     */
    @GET("api/{api_version}/users")
    Call<List<GitlabUser>> searchUser(@Path(API_VERSION) String apiVersion,
                                      @Query("username") String userName);


    /**
     * Get a specific project.
     * This endpoint can be accessed without authentication if the project is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     */
    @GET("api/{api_version}/projects/{project}")
    Call<GitProject> getProject(@Path(API_VERSION) String apiVersion,
                                @Path(PROJECT) String idOrName);

    /**
     * Get a list of repository branches from a project, sorted by name alphabetically.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     */
    @GET("api/{api_version}/projects/{project}/repository/branches")
    Call<List<GitlabBranch>> getBranches(@Path(API_VERSION) String apiVersion,
                                         @Path(PROJECT) String idOrName);

    /**
     * create project.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     */
    @POST("api/{api_version}/projects")
    Call<GitProject> createProject(@Path(API_VERSION) String apiVersion,
                                   @Body GitProjectRequest repo);

    /**
     * Give permissions to specific user
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     */
    @POST("api/{api_version}/projects/{project}/members")
    Call<GitProjectMember> grantProjectPermissions(@Path(API_VERSION) String apiVersion,
                                                   @Path(PROJECT) String idOrName,
                                                   @Body GitProjectMemberRequest repo);

    /**
     * delete a specific project
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     */
    @DELETE("api/{api_version}/projects/{project}")
    Call<Boolean> deleteProject(@Path(API_VERSION) String apiVersion,
                                @Path(PROJECT) String idOrName);

    /**
     * Get a list of repository files and directories in a project.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     * This command provides essentially the same functionality as the git ls-tree command.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param path      (optional) - The path inside repository. Used to get contend of subdirectories
     * @param reference (optional) - The name of a repository branch or tag or if not given the default branch
     * @param recursive (optional) - Boolean value used to get a recursive tree (false by default)
     */
    @GET("api/v4/projects/{project}/repository/tree")
    Call<List<GitRepositoryEntry>> getRepositoryTree(@Path(PROJECT) String idOrName,
                                                     @Query(PATH) String path,
                                                     @Query(REF) String reference,
                                                     @Query(RECURSIVE) Boolean recursive);

    /**
     * Allows you to receive information about file in repository like name, size, content.
     * Note that file content is Base64 encoded.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     */
    @GET("api/{api_version}/projects/{project}/repository/files/{file_path}")
    Call<GitFile> getFiles(@Path(API_VERSION) String apiVersion,
                           @Path(PROJECT) String idOrName,
                           @Path(value = FILE_PATH, encoded = true) String filePath,
                           @Query(REF) String reference);

    /**
     * Allows you to receive raw content of a file.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     */
    @Streaming
    @GET("api/{api_version}/projects/{project}/repository/files/{file_path}/raw")
    Call<ResponseBody> getFilesRawContent(@Path(API_VERSION) String apiVersion,
                                          @Path(PROJECT) String idOrName,
                                          @Path(value = FILE_PATH, encoded = true) String filePath,
                                          @Query(REF) String reference);

    /**
     * Allows you to receive information about file in repository like name, size, content.
     * Note that file content is Base64 encoded.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param fileRequest
     */
    @POST("api/{api_version}/projects/{project}/repository/files/{file_path}")
    Call<GitFile> createFiles(@Path(API_VERSION) String apiVersion,
                              @Path(PROJECT) String idOrName,
                              @Path(FILE_PATH) String filePath,
                              @Body UpdateGitFileRequest fileRequest);

    /**
     * Get a list of repository commits in a project.
     * <strong>NOTE</strong>: ISO 8601 format YYYY-MM-DDTHH:MM:SSZ is used for dates.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName  The ID or URL-encoded path of the project
     * @param reference (optional) - The name of a repository branch or tag or if not given the default branch
     * @param since     (optional) - Only commits after or on this date will be returned
     * @param until     (optional) - Only commits before or on this date will be returned
     * @param path      (optional) - The file path
     * @param all       (optional) - Retrieve every commit from the repository
     * @param withStats (optional) - Stats about each commit will be added to the response
     */
    @GET("api/{api_version}/projects/{project}/repository/commits")
    Call<List<GitCommitEntry>> getCommits(@Path(API_VERSION) String apiVersion,
                                          @Path(PROJECT) String idOrName,
                                          @Query(REF_NAME) String reference,
                                          @Query("since") String since,
                                          @Query("until") String until,
                                          @Query(PATH) String path,
                                          @Query("all") String all,
                                          @Query("with_stats") String withStats);

    /**
     * Get a specific commit identified by the commit hash or name of a branch or tag.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param sha      The commit hash or name of a repository branch or tag
     * @param stats    (optional) - Include commit stats. Default is true
     */
    @GET("api/{api_version}/projects/{project}/repository/commits/{sha}")
    Call<GitCommitEntry> getCommit(@Path(API_VERSION) String apiVersion,
                                   @Path(PROJECT) String idOrName,
                                   @Path("sha") String sha,
                                   @Query("stats") Boolean stats);

    /**
     * Create a commit by posting a JSON payload.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     */
    @POST("api/{api_version}/projects/{project}/repository/commits")
    Call<GitCommitEntry> postCommit(@Path(API_VERSION) String apiVersion,
                                    @Path(PROJECT) String idOrName,
                                    @Body GitPushCommitEntry commitEntry);

    /**
     * Get a list of repository tags from a project, sorted by name in reverse alphabetical order.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param apiVersion    The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName      The ID or URL-encoded path of the project
     * @param orderCriteria (optional) - Return tags ordered by name or updated fields. Default is updated
     * @param sortCriteria  (optional) - Return tags sorted in asc or desc order. Default is desc
     */
    @GET("api/{api_version}/projects/{project}/repository/tags")
    Call<List<GitTagEntry>> getRevisions(@Path(API_VERSION) String apiVersion,
                                         @Path(PROJECT) String idOrName,
                                         @Query("order_by") String orderCriteria,
                                         @Query("sort") String sortCriteria);

    /**
     * Get a specific repository tag determined by its name.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param tagName  The name of the tag
     */
    @GET("api/{api_version}/projects/{project}/repository/tags/{tag}")
    Call<GitTagEntry> getRevision(@Path(API_VERSION) String apiVersion,
                                  @Path(PROJECT) String idOrName,
                                  @Path("tag") String tagName);

    /**
     * Create a specific repository tag.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param tagName  The name of the tag
     * @param ref
     * @param message
     * @param  releaseDescription
     */
    @POST("api/{api_version}/projects/{project}/repository/tags")
    Call<GitTagEntry> createRevision(@Path(API_VERSION) String apiVersion,
                                     @Path(PROJECT) String idOrName,
                                     @Query("tag_name") String tagName,
                                     @Query(REF) String ref,
                                     @Query("message") String message,
                                     @Query("release_description") String releaseDescription);

    @GET("api/v4/version")
    Call<GitlabVersion> getVersion();

    @POST("api/{api_version}/users/{user_id}/impersonation_tokens")
    Call<GitToken> issueToken(@Path(API_VERSION) String apiVersion,
                              @Path(USER_ID) String userId,
                              @Body GitTokenRequest tokenRequest,
                              @Header(PRIVATE_TOKEN) String token);

    @POST("api/{api_version}/projects/{project}/hooks")
    Call<GitRepositoryEntry> addProjectHook(@Path(API_VERSION) String apiVersion,
                                            @Path(PROJECT) String project,
                                            @Body GitHookRequest hookRequest);

    /**
     * Update a project info.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param project The ID or URL-encoded path of the project
     */
    @PUT("api/{api_version}/projects/{project}")
    Call<GitProject> updateProject(@Path(API_VERSION) String apiVersion,
                                   @Path(PROJECT) String project,
                                   @Body GitProjectRequest projectInfo);

    /**
     * Creates a new group
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param groupInfo The new group info
     */
    @POST("api/{api_version}/groups")
    Call<GitGroup> createGroup(@Path(API_VERSION) String apiVersion,
                               @Body GitGroupRequest groupInfo);

    /**
     * Deletes a group
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param groupId The ID or URL-encoded path of the group
     */
    @DELETE("api/{api_version}/groups/{groupId}")
    Call<GitGroup> deleteGroup(@Path(API_VERSION) String apiVersion,
                               @Path("groupId") String groupId);

    /**
     * Forks a project
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param project The ID or URL-encoded path of the project
     * @param namespace The ID or path of the namespace that the project will be forked to
     */
    @POST("api/{api_version}/projects/{project}/fork")
    Call<GitProject> forkProject(@Path(API_VERSION) String apiVersion,
                                 @Path(PROJECT) String project,
                                 @Query("namespace") String namespace);

    /**
     * Get the path to repository storage for specified project. Available for administrators only.
     * NOTE: Introduced in GitLab 14.0.
     *
     * @param project The ID or URL-encoded path of the project
     */
    @GET("api/v4/projects/{project}/storage")
    Call<GitProjectStorage> getProjectStorage(@Path(PROJECT) String project);

    /**
     * Add issue to specified project.
     * This endpoint can be accessed without authentication if the project is publicly accessible.
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param issue The Issue to be created. Attachments should be specified as list of files paths.
     */
    @POST("api/{api_version}/projects/{project}/issues")
    Call<GitlabIssue> createIssue(@Path(API_VERSION) String apiVersion,
                                  @Path(PROJECT) String idOrName,
                                  @Body GitlabIssue issue);

    /**
     * Lists project issues
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param labels Labels
     */
    @GET("api/{api_version}/projects/{project}/issues")
    Call<List<GitlabIssue>> getIssues(@Path(API_VERSION) String apiVersion,
                                      @Path(PROJECT) String idOrName,
                                      @Query("labels") List<String> labels);

    /**
     * Uploads file to specified project
     *
     * @param apiVersion The Gitlab API version (values v3 or v4 supported only)
     * @param idOrName The ID or URL-encoded path of the project
     * @param file File to be uploaded
     */
    @Multipart
    @POST("api/{api_version}/projects/{project}/uploads")
    Call<GitlabUpload> upload(@Path(API_VERSION) String apiVersion,
                              @Path(PROJECT) String idOrName,
                              @Part MultipartBody.Part file);
}
