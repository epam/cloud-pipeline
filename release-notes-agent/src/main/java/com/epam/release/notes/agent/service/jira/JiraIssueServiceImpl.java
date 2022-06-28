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

import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.jira.JiraRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class JiraIssueServiceImpl implements JiraIssueService {

    private final JiraApiClient jiraApiClient;
    private final String jiraBaseUrl;
    private final String jiraVersionCustomFieldId;
    private final String jiraGithubCustomFieldId;

    public JiraIssueServiceImpl(final JiraApiClient jiraApiClient,
                                @Value("${jira.base.url}") final String jiraBaseUrl,
                                @Value("${jira.version.custom.field.id}") final String jiraVersionCustomFieldId,
                                @Value("${jira.github.custom.field.id}") final String jiraGithubCustomFieldId) {
        this.jiraApiClient = jiraApiClient;
        this.jiraBaseUrl = jiraBaseUrl;
        this.jiraVersionCustomFieldId = jiraVersionCustomFieldId;
        this.jiraGithubCustomFieldId = jiraGithubCustomFieldId;
    }

    @Override
    public List<JiraIssue> fetchIssues(final String version) {
        final JiraRequest jiraRequest = JiraRequest.builder()
                .jql(format("cf[%s]~%s", jiraVersionCustomFieldId, version))
                .build();
        return jiraApiClient.getIssue(jiraRequest)
                .stream()
                .map(issueVO -> JiraIssueMapper.toJiraIssue(issueVO, format("customfield_%s", jiraGithubCustomFieldId),
                        jiraBaseUrl, version))
                .collect(Collectors.toList());
    }
}
