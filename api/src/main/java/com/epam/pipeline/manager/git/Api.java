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
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface Api {
    String PROJECT_PARAMETER = "project";

    /**
     * Get a specific project.
     * This endpoint can be accessed without authentication if the project is publicly accessible.
     *
     * @param idOrName The ID or URL-encoded path of the project
     */
    @GET("projects/{project}")
    Call<GitProject> getProject(@Path(PROJECT_PARAMETER) String idOrName);

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
    @GET("projects/{project}/repository/tree")
    Call<List<GitRepositoryEntry>> getRepositoryTree(@Path(PROJECT_PARAMETER) String idOrName,
                                                     @Query("path") String path,
                                                     @Query("ref") String reference,
                                                     @Query("recursive") Boolean recursive);

    /**
     * Allows you to receive information about file in repository like name, size, content.
     * Note that file content is Base64 encoded.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param filePath  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     */
    @GET("projects/{project}/repository/files/{file_path}")
    Call<GitFile> getFiles(@Path(PROJECT_PARAMETER) String idOrName,
                           @Path("file_path") String filePath,
                           @Query("ref") String reference);

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
    @GET("projects/{project}/repository/commits")
    Call<List<GitCommitEntry>> getCommits(@Path(PROJECT_PARAMETER) String idOrName,
                                          @Query("ref_name") String reference,
                                          @Query("since") String since,
                                          @Query("until") String until,
                                          @Query("path") String path,
                                          @Query("all") String all,
                                          @Query("with_stats") String withStats);

    /**
     * Get a specific commit identified by the commit hash or name of a branch or tag.
     *
     * @param idOrName The ID or URL-encoded path of the project
     * @param sha      The commit hash or name of a repository branch or tag
     * @param stats    (optional) - Include commit stats. Default is true
     */
    @GET("projects/{project}/repository/commits/{sha}")
    Call<GitCommitEntry> getCommit(@Path(PROJECT_PARAMETER) String idOrName,
                                   @Path("sha") String sha,
                                   @Query("stats") Boolean stats);

    /**
     * Create a commit by posting a JSON payload.
     *
     * @param idOrName The ID or URL-encoded path of the project
     */
    @POST("projects/{project}/repository/commits")
    Call<GitCommitEntry> postCommit(@Path(PROJECT_PARAMETER) String idOrName,
                                @Body GitPushCommitEntry commitEntry);

    /**
     * Get a list of repository tags from a project, sorted by name in reverse alphabetical order.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName      The ID or URL-encoded path of the project
     * @param orderCriteria (optional) - Return tags ordered by name or updated fields. Default is updated
     * @param sortCriteria  (optional) - Return tags sorted in asc or desc order. Default is desc
     */
    @GET("projects/{project}/repository/tags")
    Call<List<GitTagEntry>> getRevisions(@Path(PROJECT_PARAMETER) String idOrName,
                                         @Query("order_by") String orderCriteria,
                                         @Query("sort") String sortCriteria);

    /**
     * Get a specific repository tag determined by its name.
     * This endpoint can be accessed without authentication if the repository is publicly accessible.
     *
     * @param idOrName The ID or URL-encoded path of the project
     * @param tagName  The name of the tag
     */
    @GET("projects/{project}/repository/tags/{tag}")
    Call<GitTagEntry> getRevision(@Path(PROJECT_PARAMETER) String idOrName,
                                  @Query("tag_name") String tagName);
}
