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

import com.epam.release.notes.agent.entity.version.Version;
import com.epam.release.notes.agent.entity.version.VersionStatus;
import com.epam.release.notes.agent.service.github.GitHubService;
import com.epam.release.notes.agent.service.jira.JiraIssueService;
import com.epam.release.notes.agent.service.version.ApplicationVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

import static java.lang.String.format;

@Slf4j
@ShellComponent
public class ReleaseNotesNotifier {

    @Autowired
    private ApplicationVersionService applicationVersionService;

    @Autowired
    private GitHubService gitHubService;

    @Autowired
    private JiraIssueService jiraIssueService;

    @Value("${release.notes.agent.subscribers:}")
    private List<String> subscribers;

    @Value("${release.notes.agent.admin.email:}")
    private String adminEmail;

    @ShellMethod(key = "send-release-notes", value = "Grab and send release notes information to specified users")
    public void sendReleaseNotes(@ShellOption(defaultValue = ShellOption.NULL) List<String> emails) {

        final Version old = applicationVersionService.loadPreviousVersion();
        final Version current = applicationVersionService.fetchCurrentVersion();

        if (emails == null) {
            emails = subscribers;
        }

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
                    "The old major version: %s, the new major version: %s. Report will be sent to admin: %s",
                    current.toString(), old.getMajor(), current.getMajor(), adminEmail));
            applicationVersionService.storeVersion(current);
            return;
        }
        // send notifications with changes
        log.info(format(
                "Creating release notes report. Old current: %s, new current: %s. Report will be sent to: %s",
                old.getSha(), current.getSha(), emails));
        gitHubService.fetchIssues(current.getSha(), old.getSha())
                .forEach(gitHubIssue -> System.out.println(gitHubIssue.getNumber() + " " + gitHubIssue.getTitle()));
        jiraIssueService.fetchIssue(current.toString()).forEach(i -> System.out.printf(
                "Id: %s Title: %s Description: %s URL: %s Github: %s Version: %s Url: %s%n",
                i.getId(), i.getTitle(), i.getDescription(), i.getUrl(), i.getGithubId(), i.getVersion(),
                i.getUrl()));
        applicationVersionService.storeVersion(current);
    }
}
