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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;


@Slf4j
@ShellComponent
@AllArgsConstructor
public class ReleaseIssuesGrabber {

    private final ApplicationVersionService applicationVersionService;
    private final GitHubService gitHubService;

    @ShellMethod(key = "grab-release-issues", value = "Grab issue that was mentioned in commits")
    public void grabIssuesForRelease(@ShellOption final String from, @ShellOption final String to) {

        final Version old = Version.buildVersion(from);
        final Version current = Version.buildVersion(to);

        final VersionStatus versionStatus = applicationVersionService.getVersionStatus(old, current);
        if (versionStatus == VersionStatus.NOT_CHANGED) {
            return;
        } else if (versionStatus == VersionStatus.MAJOR_CHANGED) {
            return;
        }
        gitHubService.fetchIssues(current.getSha(), old.getSha())
                .forEach(gitHubIssue -> System.out.println(gitHubIssue.getNumber() + " " + gitHubIssue.getTitle()));
    }

}
