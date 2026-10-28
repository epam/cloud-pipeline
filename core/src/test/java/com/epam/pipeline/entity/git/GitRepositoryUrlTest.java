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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

public class GitRepositoryUrlTest {

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

    @ParameterizedTest
    @MethodSource("data2")
    public void shouldProperlyInitialize(final String caseName, final String url, final String protocol,
                                         final String userName, final String password, final String host,
                                         final String namespace, final String project) {
        final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(url);
        assertEquals(protocol, gitRepositoryUrl.getProtocol());
        assertEquals(host, gitRepositoryUrl.getHost());
        assertEquals(namespace, gitRepositoryUrl.getNamespace().orElse(null));
        assertEquals(project, gitRepositoryUrl.getProject().orElse(null));
        assertEquals(userName, gitRepositoryUrl.getUsername().orElse(null));
        assertEquals(password, gitRepositoryUrl.getPassword().orElse(null));
    }

    public static Stream<Arguments> data2() {
        return Stream.of(
                Arguments.of("simple url",
                        SIMPLE_URL,
                        PROTOCOL, null, null, HOST, null, null),
                Arguments.of("url with namespace",
                        URL_WTH_NAMESPACE,
                        PROTOCOL, null, null, HOST, NAMESPACE, null
                ),
                Arguments.of(
                        "url with namespace and project",
                        URL_WITH_NAMESPACE_AND_PROJECT,
                        PROTOCOL, null, null, HOST, NAMESPACE, PROJECT
                ),
                Arguments.of(
                        "url with username",
                        URL_WITH_USERNAME,
                        PROTOCOL, USERNAME, null, HOST, NAMESPACE, PROJECT
                ),
                Arguments.of(
                        "url with both username and password",
                        URL_WITH_USERNAME_AND_PASSWORD,
                        PROTOCOL, USERNAME, PASSWORD, HOST, NAMESPACE, PROJECT
                ),
                Arguments.of(
                        "url with username, password and port",
                        "https://username:pAssw0rd@git.company-name-42.com:42/graphic/awesome-game.git",
                        PROTOCOL, USERNAME, PASSWORD, "git.company-name-42.com:42", NAMESPACE, PROJECT
                )
        );
    }

    public static Stream<Arguments> data() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("url is empty string", ""),
                Arguments.of("url with password but without username",
                        "https://:pAssw0rd@git.company-name-42.com/graphic/awesome-game.git")
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void shouldThrowException(String caseName, String url) {
        assertThrows(Exception.class, () -> GitRepositoryUrl.from(url));
    }
}