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
package com.epam.release.notes.agent.cli;

import com.epam.release.notes.agent.entity.action.Action;
import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.entity.jira.JiraIssue;
import com.epam.release.notes.agent.entity.version.Version;
import com.epam.release.notes.agent.entity.version.VersionStatus;
import com.epam.release.notes.agent.service.action.ActionServiceProvider;
import com.epam.release.notes.agent.service.github.GitHubService;
import com.epam.release.notes.agent.service.jira.JiraIssueService;
import com.epam.release.notes.agent.service.version.ApplicationVersionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@ShellComponent
public class ReleaseNotesNotifier {

    private final ApplicationVersionService applicationVersionService;
    private final GitHubService gitHubService;
    private final JiraIssueService jiraIssueService;
    private final ActionServiceProvider actionServiceProvider;

    public ReleaseNotesNotifier(final ApplicationVersionService applicationVersionService,
                                final GitHubService gitHubService,
                                final JiraIssueService jiraIssueService,
                                final ActionServiceProvider actionServiceProvider) {
        this.applicationVersionService = applicationVersionService;
        this.gitHubService = gitHubService;
        this.jiraIssueService = jiraIssueService;
        this.actionServiceProvider = actionServiceProvider;
    }

    @ShellMethod(
            key = "send-release-notes",
            value = "Grab and post/publish release notes information to specified users.")
    public void sendReleaseNotes(@ShellOption(defaultValue = ShellOption.NULL) List<String> emails) {

        final Version old = applicationVersionService.loadPreviousVersion();
        final Version current = applicationVersionService.fetchCurrentVersion();

        final VersionStatus versionStatus = applicationVersionService.getVersionStatus(old, current);
        if (versionStatus == VersionStatus.NOT_FOUND) {
            applicationVersionService.storeVersion(current);
            return;
        } else if (versionStatus == VersionStatus.NOT_CHANGED) {
            log.info(format(
                    "The current API version %s has not changed.", current.toString()));
            return;
        } else if (versionStatus == VersionStatus.MAJOR_CHANGED) {
            // send notification to admin
            log.info(format("The current major API version %s has changed. " +
                    "The old major version: %s, the new major version: %s. Report will be sent to admin.",
                    current.toString(), old.getMajor(), current.getMajor()));
            performAction(Collections.singletonList(Action.POST), old, current, Collections.emptyList(),
                    Collections.emptyList());
            applicationVersionService.storeVersion(current);
            return;
        }
        // send notifications with changes
        log.info(format(
                "Creating release notes report. Old current: %s, new current: %s. Report will be sent to: %s",
                old.getSha(), current.getSha(), emails));
        final List<GitHubIssue> gitHubIssues = gitHubService.fetchIssues(current.getSha(), old.getSha());
        gitHubIssues
                .forEach(gitHubIssue -> System.out.println(gitHubIssue.getNumber() + " " + gitHubIssue.getTitle()));
        final List<JiraIssue> jiraIssues = jiraIssueService.fetchIssue(current.toString());
        performAction(Collections.singletonList(Action.POST), old, current, gitHubIssues, jiraIssues);
        applicationVersionService.storeVersion(current);
    }

    private void performAction(final List<Action> actions, final Version old, final Version current,
                               final List<GitHubIssue> gitHubIssues, final List<JiraIssue> jiraIssues) {
        actions.stream()
                .map(action -> actionServiceProvider.getActionService(action.getName()))
                .forEach(service -> service.process(old.toString(), current.toString(),
                        filterJiraIssues(gitHubIssues, jiraIssues), gitHubIssues));
    }

    private List<JiraIssue> filterJiraIssues(final List<GitHubIssue> gitHubIssues, final List<JiraIssue> jiraIssues) {
        final Set<Long> githubIssueNumbers = gitHubIssues.stream()
                .map(GitHubIssue::getNumber)
                .collect(Collectors.toCollection(HashSet::new));
        return jiraIssues.stream()
                .filter(j -> StringUtils.isBlank(j.getGithubId()) ||
                        !githubIssueNumbers.contains(Long.parseLong(j.getGithubId())))
                .collect(Collectors.toList());
    }
}
