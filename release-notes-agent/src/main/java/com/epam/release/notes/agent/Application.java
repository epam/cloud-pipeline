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

package com.epam.release.notes.agent;

import com.epam.release.notes.agent.service.ReleaseNotificationService;
import com.epam.release.notes.agent.service.ReleaseNotificationServiceImpl;
import com.epam.release.notes.agent.service.github.GitHubService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@SpringBootApplication
public class Application implements CommandLineRunner {

    GitHubService gitHubService;

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ReleaseNotificationService notificationService(GitHubService gitHubService) {
        return new ReleaseNotificationServiceImpl(gitHubService);
    }

    @Override
    public void run(final String... args) {
        Optional.ofNullable(notificationService(gitHubService)).ifPresent(ReleaseNotificationService::perform);
    }
}
