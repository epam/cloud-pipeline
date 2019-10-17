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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitFile;
import com.epam.pipeline.entity.git.GitHookRequest;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitProjectMember;
import com.epam.pipeline.entity.git.GitProjectMemberRequest;
import com.epam.pipeline.entity.git.GitProjectRequest;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.entity.git.GitTokenRequest;
import com.epam.pipeline.entity.git.GitlabUser;
import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.entity.git.UpdateGitFileRequest;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
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

    /**
     * @param userName The name of the GitLab user
     */
    @GET("api/v3/users")
    Call<List<GitlabUser>> searchUser(@Query("username") String userName);


    /**
     * Get a specific project.
     * This endpoint can be accessed without authentication if the project is publicly accessible.
     *
     * @param idOrName The ID or URL-encoded path of the project
     */
    @GET("api/v3/projects/{project}")
    Call<GitProject> getProject(@Path(PROJECT) String idOrName);

    /**
     * create project.
     */
    @POST("api/v3/projects")
    Call<GitProject> createProject(@Body GitProjectRequest repo);

    /**
     * Give permissions to specific user
     * */
    @POST("api/v3/projects/{project}/members")
    Call<GitProjectMember> grantProjectPermissions(@Path(PROJECT) String idOrName,
                                                   @Body GitProjectMemberRequest repo);

    /**
     * delete a specific project
     *
     * @param idOrName The ID or URL-encoded path of the project
     */
    @DELETE("api/v3/projects/{project}")
    Call<Boolean> deleteProject(@Path(PROJECT) String idOrName);

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
    @GET("api/v3/projects/{project}/repository/tree")
    Call<List<GitRepositoryEntry>> getRepositoryTree(@Path(PROJECT) String idOrName,
                                                     @Query(PATH) String path,
                                                     @Query(REF) String reference,
                                                     @Query(RECURSIVE) Boolean recursive);

    /**
     * Allows you to receive information about file in repository like name, size, content.
     * Note that file content is Base64 encoded.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     */
    @GET("api/v3/projects/{project}/repository/files/{file_path}")
    Call<GitFile> getFiles(@Path(PROJECT) String idOrName,
                           @Path(FILE_PATH) String filePath,
                           @Query(REF) String reference);

    /**
     * Allows you to receive raw content of a file.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     */
    @Streaming
    @GET("api/v3/projects/{project}/repository/files/{file_path}/raw")
    Call<ResponseBody> getFilesRawContent(@Path(PROJECT) String idOrName,
                                          @Path(value = FILE_PATH, encoded = true) String filePath,
                                          @Query(REF) String reference);

    /**
     * Allows you to receive information about file in repository like name, size, content.
     * Note that file content is Base64 encoded.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param fileRequest
     */
    @POST("api/v3/projects/{project}/repository/files/{file_path}")
    Call<GitFile> createFiles(@Path(PROJECT) String idOrName,
                              @Path(FILE_PATH) String filePath,
                              @Body UpdateGitFileRequest fileRequest);

    /**
     * Get a list of repository commits in a project.
     * <strong>NOTE</strong>: ISO 8601 format YYYY-MM-DDTHH:MM:SSZ is used for dates.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param reference (optional) - The name of a repository branch or tag or if not given the default branch
     * @param since     (optional) - Only commits after or on this date will be returned
     * @param until     (optional) - Only commits before or on this date will be returned
     * @param path      (optional) - The file path
     * @param all       (optional) - Retrieve every commit from the repository
     * @param withStats (optional) - Stats about each commit will be added to the response
     */
    @GET("api/v3/projects/{project}/repository/commits")
    Call<List<GitCommitEntry>> getCommits(@Path(PROJECT) String idOrName,
                                          @Query(REF_NAME) String reference,
                                          @Query("since") String since,
                                          @Query("until") String until,
                                          @Query(PATH) String path,
                                          @Query("all") String all,
                                          @Query("with_stats") String withStats);

    /**
     * Get a specific commit identified by the commit hash or name of a branch or tag.
     *
     * @param idOrName The ID or URL-encoded path of the project
     * @param sha      The commit hash or name of a repository branch or tag
     * @param stats    (optional) - Include commit stats. Default is true
     */
    @GET("api/v3/projects/{project}/repository/commits/{sha}")
    Call<GitCommitEntry> getCommit(@Path(PROJECT) String idOrName,
                                   @Path("sha") String sha,
                                   @Query("stats") Boolean stats);

    /**
     * Create a commit by posting a JSON payload.
     *
     * @param idOrName The ID or URL-encoded path of the project
     */
    @POST("api/v3/projects/{project}/repository/commits")
    Call<GitCommitEntry> postCommit(@Path(PROJECT) String idOrName,
                                    @Body GitPushCommitEntry commitEntry);

    /**
     * Get a list of repository tags from a project, sorted by name in reverse alphabetical order.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName      The ID or URL-encoded path of the project
     * @param orderCriteria (optional) - Return tags ordered by name or updated fields. Default is updated
     * @param sortCriteria  (optional) - Return tags sorted in asc or desc order. Default is desc
     */
    @GET("api/v3/projects/{project}/repository/tags")
    Call<List<GitTagEntry>> getRevisions(@Path(PROJECT) String idOrName,
                                         @Query("order_by") String orderCriteria,
                                         @Query("sort") String sortCriteria);

    /**
     * Get a specific repository tag determined by its name.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName The ID or URL-encoded path of the project
     * @param tagName  The name of the tag
     */
    @GET("api/v3/projects/{project}/repository/tags/{tag}")
    Call<GitTagEntry> getRevision(@Path(PROJECT) String idOrName,
                                  @Path("tag") String tagName);

    /**
     * Create a specific repository tag.
     *
     * @param idOrName The ID or URL-encoded path of the project
     * @param tagName  The name of the tag
     * @param ref
     * @param message
     * @param  releaseDescription
     */
    @POST("api/v3/projects/{project}/repository/tags")
    Call<GitTagEntry> createRevision(@Path(PROJECT) String idOrName,
                                     @Query("tag_name") String tagName,
                                     @Query(REF) String ref,
                                     @Query("message") String message,
                                     @Query("release_description") String releaseDescription);

    @GET("api/v4/version")
    Call<GitlabVersion> getVersion();

    @POST("api/v3/users/{user_id}/impersonation_tokens")
    Call<GitToken> issueToken(@Path(USER_ID) String userId,
                              @Body GitTokenRequest tokenRequest,
                              @Header(PRIVATE_TOKEN) String token);

    @POST("api/v3/projects/{project}/hooks")
    Call<GitRepositoryEntry> addProjectHook(@Path(PROJECT) String project,
                                            @Body GitHookRequest hookRequest);

    /**
     * Update a project info.
     *
     * @param project The ID or URL-encoded path of the project
     */
    @PUT("api/v3/projects/{project}")
    Call<GitProject> updateProject(@Path(PROJECT) String project,
                                   @Body GitProjectRequest projectInfo);
}
