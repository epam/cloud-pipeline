/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
 */

package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;
import java.util.Map;

public interface GitHubApi {

    String BRANCH_NAME = "sha";
    String PROJECT = "project";
    String OWNER = "owner";
    String PAGE = "page";
    String PAGE_SIZE = "per_page";
    String ISSUE_NUMBER = "issue_number";

    /**
     * Gets a list of commits for the specified project branch.
     * This command provides essentially the same functionality as the git log command.
     *
     * @param project the URL-encoded name of the project
     * @param owner the URL-encoded name of the repository owner
     * @param branch (optional) SHA or branch to start listing commits from. Default: the repository’s default branch
     * @param page (optional) the number of the page to return
     * @param pageSize (optional) the size of the page to return
     */
    @GET("/repos/{owner}/{project}/commits")
    Call<List<Map<String, Object>>> listCommits(@Path(PROJECT) String project,
                                                @Path(OWNER) String owner,
                                                @Query(BRANCH_NAME) String branch,
                                                @Query(PAGE) Integer page,
                                                @Query(PAGE_SIZE) Integer pageSize);

    /**
     * Gets an issue that matches the requested issue number.
     *
     * @param project the URL-encoded name of the project
     * @param owner the URL-encoded name of the repository owner
     * @param issue the issue number
     */
    @GET("/repos/{owner}/{project}/issues/{issue_number}")
    Call<GitHubIssue> getIssue(@Path(PROJECT) String project,
                               @Path(OWNER) String owner,
                               @Path(ISSUE_NUMBER) Long issue);
}
