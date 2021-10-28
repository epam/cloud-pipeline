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
import com.epam.release.notes.agent.service.version.ApplicationVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@ShellComponent
public class ReleaseNotesSender {

    @Autowired
    private ApplicationVersionService applicationVersionService;

    @Autowired
    private GitHubService gitHubService;

    @Value("${release.notes.agent.subsribers:}")
    private List<String> subscribers;

    @ShellMethod(key = "send-release-notes", value = "Grab and send release notes information to specified users")
    public void sendReleaseNotes(@ShellOption(defaultValue = ShellOption.NULL) String from,
                                 @ShellOption(defaultValue = ShellOption.NULL) String to,
                                 @ShellOption(defaultValue = ShellOption.NULL) List<String> emails) {

        final Version old = getVersion(from, () -> applicationVersionService.loadPreviousVersion());
        final Version current = getVersion(to, () -> applicationVersionService.fetchCurrentVersion());

        if (emails == null) {
            emails = subscribers;
        }

        final VersionStatus versionStatus = applicationVersionService.getVersionStatus(old, current);
        if (versionStatus == VersionStatus.NOT_CHANGED) {
            return;
        } else if (versionStatus == VersionStatus.MAJOR_CHANGED) {
            // send notification to admin
            return;
        }
        // send notifications with changes
        log.info(String.format(
                "Creating release notes report. Old current: %s, new current: %s. Report will be sent to: %s",
                old.getSha(), current.getSha(), emails));
        gitHubService.fetchIssues(current.getSha(), old.getSha())
                .forEach(gitHubIssue -> System.out.println(gitHubIssue.getNumber() + " " + gitHubIssue.getTitle()));
    }

    private Version getVersion(String version, Supplier<Version> orDefault) {
        final Version result;
        if (version == null) {
            result = orDefault.get();
        } else {
            result = Version.buildVersion(version);
        }
        return result;
    }

}
