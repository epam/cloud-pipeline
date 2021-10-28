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

package com.epam.release.notes.agent.service;

import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.service.github.GitHubService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReleaseNotificationServiceImpl implements ReleaseNotificationService {

    private final GitHubService gitHubService;

    @Override
    public void perform() {
        List<GitHubIssue> issues = gitHubService.fetchIssues(null, "83b4e30140f8ff880ce829ef316adbd814ee1a30");
        System.out.println(issues);
    }


}
