/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.git;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class GitRepositoryUrlTest {

    @RunWith(Parameterized.class)
    public static class DataDriven {

        private static final String URL_WITH_USERNAME_AND_PASSWORD =
                "https://username:pAssw0rd@git.company-name-42.com/graphic/awesome-game.git";
        private static final String URL_WITH_USERNAME =
                "https://username@git.company-name-42.com/graphic/awesome-game.git";
        private static final String URL_WITH_NAMESPACE_AND_PROJECT =
                "https://git.company-name-42.com/graphic/awesome-game.git";
        private static final String URL_WTH_NAMESPACE = "https://git.company-name-42.com/graphic";
        private static final String SIMPLE_URL = "https://git.company-name-42.com";
        private static final String PROTOCOL = "https://";
        private static final String HOST = "git.company-name-42.com";
        private static final String NAMESPACE = "graphic";
        private static final String PROJECT = "awesome-game";
        private static final String USERNAME = "username";
        private static final String PASSWORD = "pAssw0rd";
        private final String caseName;
        final String url;
        final String protocol;
        final String host;
        final Optional<String> namespace;
        final Optional<String> project;
        final Optional<String> userName;
        final Optional<String> password;

        public DataDriven(final String caseName,
                          final String url,
                          final String protocol,
                          final Optional<String> userName,
                          final Optional<String> password,
                          final String host,
                          final Optional<String> namespace,
                          final Optional<String> project) {
            this.caseName = caseName;
            this.url = url;
            this.protocol = protocol;
            this.host = host;
            this.namespace = namespace;
            this.project = project;
            this.userName = userName;
            this.password = password;
        }

        @Parameters(name = "{index}: when {0} passed")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                {
                    "simple url",
                    SIMPLE_URL,
                    PROTOCOL, empty(), empty(), HOST, empty(), empty()
                },
                {
                    "url with namespace",
                    URL_WTH_NAMESPACE,
                    PROTOCOL, empty(), empty(), HOST, of(NAMESPACE), empty()
                },
                {
                    "url with namespace and project",
                    URL_WITH_NAMESPACE_AND_PROJECT,
                    PROTOCOL, empty(), empty(), HOST, of(NAMESPACE), of(PROJECT)
                },
                {
                    "url with username",
                    URL_WITH_USERNAME,
                    PROTOCOL, of(USERNAME), empty(), HOST, of(NAMESPACE), of(PROJECT)
                },
                {
                    "url with both username and password",
                    URL_WITH_USERNAME_AND_PASSWORD,
                    PROTOCOL, of(USERNAME), of(PASSWORD), HOST, of(NAMESPACE), of(PROJECT)
                },
                {
                    "url with username, password and port",
                    "https://username:pAssw0rd@git.company-name-42.com:42/graphic/awesome-game.git",
                    PROTOCOL, of(USERNAME), of(PASSWORD), "git.company-name-42.com:42", of(NAMESPACE), of(PROJECT)
                },
            });
        }

        @Test
        public void shouldProperlyInitialize() {
            final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(url);
            assertEquals(protocol, gitRepositoryUrl.getProtocol());
            assertEquals(host, gitRepositoryUrl.getHost());
            assertEquals(namespace, gitRepositoryUrl.getNamespace());
            assertEquals(project, gitRepositoryUrl.getProject());
            assertEquals(userName, gitRepositoryUrl.getUsername());
            assertEquals(password, gitRepositoryUrl.getPassword());
        }
    }

    @RunWith(Parameterized.class)
    public static class Negative {

        private final String caseName;
        private final String url;

        public Negative(final String caseName, final String url) {
            this.caseName = caseName;
            this.url = url;
        }

        @Parameters(name = "{index}: when {0} passed")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                {
                    "null",
                    null
                },
                {
                    "url is empty string",
                    ""
                },
                {
                    "url with password but without username",
                    "https://:pAssw0rd@git.company-name-42.com/graphic/awesome-game.git"
                }
            });
        }

        @Test(expected = Exception.class)
        public void shouldThrowException() {
            GitRepositoryUrl.from(url);
        }
    }
}