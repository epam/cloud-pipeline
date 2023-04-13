/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryIteratorListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogRequestFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogsPathFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderObject;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommitDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryLogEntry;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;


public interface GitReaderApi {

    String REF = "ref";
    String PROJECT = "project";
    String PATH_GLOBS = "paths";
    String PAGE = "page";
    String PAGE_SIZE = "page_size";
    String INCLUDE_DIFF = "include_diff";
    String COMMIT = "commit";

    /**
     * Get a list of repository files and directories in a project.
     * This command provides essentially the same functionality as the git ls-tree command.
     *
     * @param name  URL-encoded path of the project
     * @param reference (optional) - The name of a repository branch or tag or if not given the default branch
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     * @param paths      (optional) - The paths inside repository. Used to get contend of subdirectories
     */
    @POST("git/{project}/ls_tree")
    Call<Result<GitReaderEntryListing<GitReaderObject>>> getRepositoryTree(@Path(PROJECT) String name,
                                                                           @Query(REF) String reference,
                                                                           @Query(PAGE) Long page,
                                                                           @Query(PAGE_SIZE) Integer pageSize,
                                                                           @Body GitReaderLogsPathFilter paths);


    /**
     * Get a list of repository files and directories in a project with additional information about last commit.
     *
     * @param name  URL-encoded path of the project
     * @param reference The name of branch, tag or commit
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     * @param paths  (optional) - The paths inside repository. Used to get contend of subdirectories
     */
    @POST("git/{project}/logs_tree")
    Call<Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>>> getRepositoryLogsTree(
            @Path(PROJECT) String name,
            @Query(REF) String reference,
            @Query(PAGE) Long page,
            @Query(PAGE_SIZE) Integer pageSize,
            @Body GitReaderLogsPathFilter paths);

    /**
     * Get a list of repository files and directories in a project with additional information about last commit.
     *
     * @param name  URL-encoded path of the project
     * @param reference The name of branch, tag or commit
     */
    @POST("git/{project}/logs_tree")
    Call<Result<GitReaderEntryListing<GitReaderRepositoryLogEntry>>> getRepositoryLogsTree(
            @Path(PROJECT) String name,
            @Query(REF) String reference,
            @Body GitReaderLogsPathFilter paths);

    /**
     * Allows you to receive list of commit for specific filters like, paths, authors, dates
     *
     * @param name  URL-encoded path of the project
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     */
    @POST("git/{project}/commits")
    Call<Result<GitReaderEntryIteratorListing<GitReaderRepositoryCommit>>> listCommits(
            @Path(PROJECT) String name,
            @Query(PAGE) Long page,
            @Query(PAGE_SIZE) Integer pageSize,
            @Body GitReaderLogRequestFilter filter);

    /**
     * Allows you to receive list of commit for specific filters like, paths, authors, dates and its diff
     *
     * @param name  URL-encoded path of the project
     * @param includeDiff (optional) - Flag to include diffs from commits
     */
    @POST("git/{project}/diff")
    Call<Result<GitReaderRepositoryCommitDiff>> listCommitDiffs(@Path(PROJECT) String name,
                                                                @Query(INCLUDE_DIFF) Boolean includeDiff,
                                                                @Body GitReaderLogRequestFilter filter);

    /**
     * Allows you to get diff by specific commit
     *
     * @param name  URL-encoded path of the project
     * @param commit - The commit sha
     * @param paths (optional) - path globs to filter diff output
     */
    @POST("git/{project}/diff/{commit}")
    Call<Result<GitReaderDiffEntry>> getCommitDiff(@Path(PROJECT) String name, @Path(COMMIT) String commit,
                                                   @Body GitReaderLogsPathFilter paths);

    @Streaming
    @GET("git/{path}")
    Call<ResponseBody> downloadAttachment(@Path("path") String path);
}
