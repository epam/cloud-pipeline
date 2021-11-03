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
package com.epam.release.notes.agent.service.jira;

import com.epam.release.notes.agent.entity.jira.JiraIssueHolder;
import com.epam.release.notes.agent.entity.jira.JiraRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface JiraApi {

    /**
     * Gets a list of issues by JQL query.
     *
     * @param jiraRequest {@link JiraRequest} a request consisting of a JQL query
     *
     */
    @POST("/rest/api/2/search")
    Call<JiraIssueHolder> getIssues(@Body JiraRequest jiraRequest);
}
