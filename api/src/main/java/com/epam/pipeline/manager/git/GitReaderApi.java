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
import com.epam.pipeline.entity.git.*;
import retrofit2.Call;
import retrofit2.http.*;

public interface GitReaderApi {

    String FILE_PATH = "file_path";
    String REF = "ref";
    String PROJECT = "project";
    String PATH = "path";
    String PAGE = "page";
    String PAGE_SIZE = "page_size";
    String INCLUDE_DIFF = "include_diff";

    /**
     * Get a list of repository files and directories in a project.
     * This command provides essentially the same functionality as the git ls-tree command.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param path      (optional) - The path inside repository. Used to get contend of subdirectories
     * @param reference (optional) - The name of a repository branch or tag or if not given the default branch
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     */
    @GET("git/{project}/ls_tree")
    Call<Result<GitEntryListing<GitRepositoryEntry>>> getRepositoryTree(@Path(PROJECT) String idOrName,
                                                                @Query(PATH) String path,
                                                                @Query(REF) String reference,
                                                                @Query(PAGE) Long page,
                                                                @Query(PAGE_SIZE) Integer pageSize);


    /**
     * Get a list of repository files and directories in a project with additional information about last commit.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param path  Url encoded full path to new file. Ex. lib%2Fclass%2Erb
     * @param reference The name of branch, tag or commit
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     */
    @GET("git/{project}/logs_tree")
    Call<Result<GitEntryListing<GitRepositoryLogEntry>>> getRepositoryLogsTree(@Path(PROJECT) String idOrName,
                                                                              @Query(FILE_PATH) String path,
                                                                              @Query(REF) String reference,
                                                                              @Query(PAGE) Long page,
                                                                              @Query(PAGE_SIZE) Integer pageSize);

    /**
     * Get a list of repository files and directories in a project with additional information about last commit.
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param reference The name of branch, tag or commit
     */
    @POST("git/{project}/logs_tree")
    Call<Result<GitEntryListing<GitRepositoryLogEntry>>> getRepositoryLogsTree(@Path(PROJECT) String idOrName,
                                                                               @Query(REF) String reference,
                                                                               @Body GitLogsRequest paths);

    /**
     * Allows you to receive list of commit for specific filters like, paths, authors, dates
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     */
    @POST("git/{project}/commits")
    Call<Result<GitEntryIteratorListing<GitRepositoryCommit>>> listCommits(@Path(PROJECT) String idOrName,
                                                                           @Query(PAGE) Long page,
                                                                           @Query(PAGE_SIZE) Integer pageSize,
                                                                           @Body GitLogFilter filter);

    /**
     * Allows you to receive list of commit for specific filters like, paths, authors, dates and its diff
     *
     * @param idOrName  The ID or URL-encoded path of the project
     * @param reference The name of branch, tag or commit
     * @param page (optional) - The number of page to return
     * @param pageSize (optional) - The size of the page to return
     */
    @POST("git/{project}/diff")
    Call<Result<GitRepositoryCommitDiff>> listCommitDiffs(@Path(PROJECT) String idOrName,
                                                          @Query(INCLUDE_DIFF) Boolean includeDiff,
                                                          @Query(PAGE) Long page,
                                                          @Query(PAGE_SIZE) Integer pageSize,
                                                          @Body GitLogFilter filter);


}
