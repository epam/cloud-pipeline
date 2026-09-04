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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.*;

class GitRepositoryUrlTest {
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

    private static Stream<Arguments> provideData() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("empty", ""),
                Arguments.of("password without username",
                        "https://:pAssw0rd@git.company-name-42.com/graphic/awesome-game.git")
        );
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("provideData")
    void shouldThrowException(String caseName, String url) {
        assertThrows(Exception.class, () -> GitRepositoryUrl.from(url));
    }

    private static Stream<Arguments> provideData2() {
        return Stream.of(
                Arguments.of("simple url", SIMPLE_URL, PROTOCOL, empty(), empty(), HOST, empty(), empty()),
                Arguments.of("url with namespace", URL_WTH_NAMESPACE, PROTOCOL, empty(), empty(), HOST,
                        of(NAMESPACE), empty()),
                Arguments.of("url with namespace and project", URL_WITH_NAMESPACE_AND_PROJECT, PROTOCOL, empty(),
                        empty(), HOST, of(NAMESPACE), of(PROJECT)),
                Arguments.of("url with username", URL_WITH_USERNAME, PROTOCOL, of(USERNAME), empty(), HOST,
                        of(NAMESPACE), of(PROJECT)),
                Arguments.of("url with username and password", URL_WITH_USERNAME_AND_PASSWORD, PROTOCOL,
                        of(USERNAME), of(PASSWORD), HOST, of(NAMESPACE), of(PROJECT)),
                Arguments.of("url with username, password and port",
                        "https://username:pAssw0rd@git.company-name-42.com:42/graphic/awesome-game.git", PROTOCOL,
                        of(USERNAME), of(PASSWORD), "git.company-name-42.com:42", of(NAMESPACE), of(PROJECT))
        );
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("provideData2")
    void shouldProperlyInitialize(String caseName,
                                  String url,
                                  String protocol,
                                  Optional<String> username,
                                  Optional<String> password,
                                  String host,
                                  Optional<String> namespace,
                                  Optional<String> project) {

        GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(url);

        assertEquals(protocol, gitRepositoryUrl.getProtocol());
        assertEquals(host, gitRepositoryUrl.getHost());
        assertEquals(namespace, gitRepositoryUrl.getNamespace());
        assertEquals(project, gitRepositoryUrl.getProject());
        assertEquals(username, gitRepositoryUrl.getUsername());
        assertEquals(password, gitRepositoryUrl.getPassword());
    }

}
