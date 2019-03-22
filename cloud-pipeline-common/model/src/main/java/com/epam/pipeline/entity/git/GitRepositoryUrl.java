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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.Assert;

public final class GitRepositoryUrl {
    private static final String INVALID_URL_FORMAT_MESSAGE = "Invalid repository URL format.";
    private static final String PROTOCOL_PATTERN = "https?";
    // Username could be either placeholder ${GIT_USER} or a real username
    private static final String USERNAME_PATTERN = "\\$\\{GIT_USER}|[-_A-Za-z0-9]++";
    // Password could be either placeholder ${GIT_TOKEN} or a real password
    private static final String PASSWORD_PATTERN = "\\$\\{GIT_TOKEN}|[-_A-Za-z0-9]++";
    private static final String HOST_PATTERN = "[-._A-Za-z0-9]++(?::[0-9]++)?";
    private static final String PATH_PART_PATTERN = "[-_A-Za-z0-9]++";
    private static final Pattern GIT_REPOSITORY_URL_PATTERN = Pattern.compile(
        "^(?<protocol>" + PROTOCOL_PATTERN + "://)"      // Any url supposed to start with protocol
            + "(?:"                                     // Open optional non-capturing group for authentication
            + "(?<username>" + USERNAME_PATTERN + ")"   // If authentication group present it should contain username
            + "(?::"
            + "(?<password>" + PASSWORD_PATTERN + "))?" // Optional password
            + "@"                                       // that group should end with @ symbol
            + ")?"                                      // Close optional non-capturing group for authentication
            + "(?<host>" + HOST_PATTERN + ")"           // Host with optional port
            + "(?:/(?<namespace>" + PATH_PART_PATTERN + "))?"     // Optional namespace
            + "(?:/(?<project>" + PATH_PART_PATTERN + ")\\.git)?$" // Optional repository that ends with .git suffix
    );

    private final String protocol;
    private final String username;
    private final String password;
    private final String host;
    private final String namespace;
    private final String project;

    private GitRepositoryUrl(final String protocol,
                             final String username,
                             final String password,
                             final String host,
                             final String namespace,
                             final String project) {
        this.protocol = protocol;
        this.username = username;
        this.password = password;
        this.host = host;
        this.namespace = namespace;
        this.project = project;
    }

    public static GitRepositoryUrl from(final String url) {
        final Matcher matcher = GIT_REPOSITORY_URL_PATTERN.matcher(url);
        Assert.isTrue(matcher.matches(), INVALID_URL_FORMAT_MESSAGE);
        return new GitRepositoryUrl(
            matcher.group("protocol"),
            matcher.group("username"),
            matcher.group("password"),
            matcher.group("host"),
            matcher.group("namespace"),
            matcher.group("project")
        );
    }

    public static String asString(final String protocol,
                                  final String username,
                                  final String password,
                                  final String host,
                                  final String namespace,
                                  final String project) {
        final StringBuffer builder = new StringBuffer();
        builder.append(protocol);
        Optional.ofNullable(username).map(builder::append);
        Optional.ofNullable(password).map(p -> builder.append(":").append(p));
        Optional.ofNullable(username).map(u -> builder.append("@"));
        builder.append(host);
        Optional.ofNullable(namespace).map(n -> builder.append("/").append(n));
        Optional.ofNullable(project).map(p -> builder.append("/").append(p).append(".git"));
        return builder.toString();
    }

    public String asString() {
        return asString(protocol, username, password, host, namespace, project);
    }

    public String getProtocol() {
        return protocol;
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public Optional<String> getPassword() {
        return Optional.ofNullable(password);
    }

    public String getHost() {
        return host;
    }

    public Optional<String> getNamespace() {
        return Optional.ofNullable(namespace);
    }

    public Optional<String> getProject() {
        return Optional.ofNullable(project);
    }

    public GitRepositoryUrl withProtocol(final String protocol) {
        final String checkedProtocol = ensureMatches(protocol, PROTOCOL_PATTERN, false);
        return new GitRepositoryUrl(checkedProtocol, username, password, host, namespace, project);
    }

    public GitRepositoryUrl withUsername(final String username) {
        final String checkedUsername = ensureMatches(username, USERNAME_PATTERN);
        return new GitRepositoryUrl(protocol, checkedUsername, password, host, namespace, project);
    }

    public GitRepositoryUrl withPassword(final String password) {
        final String checkedPassword = ensureMatches(password, PASSWORD_PATTERN);
        return new GitRepositoryUrl(protocol, username, checkedPassword, host, namespace, project);
    }

    public GitRepositoryUrl withHost(final String host) {
        final String checkedHost = ensureMatches(host, HOST_PATTERN, false);
        return new GitRepositoryUrl(protocol, username, password, checkedHost, namespace, project);
    }

    public GitRepositoryUrl withNamespace(final String namespace) {
        final String checkedNamespace = ensureMatches(namespace, PATH_PART_PATTERN);
        return new GitRepositoryUrl(protocol, username, password, host, checkedNamespace, project);
    }

    public GitRepositoryUrl withProject(final String project) {
        final String checkedProject = ensureMatches(project, PATH_PART_PATTERN);
        return new GitRepositoryUrl(protocol, username, password, host, namespace, checkedProject);
    }

    private static String ensureMatches(final String value, final String pattern) {
        return ensureMatches(value, pattern, true);
    }

    private static String ensureMatches(final String value, final String pattern, final boolean matchesIfAbsent) {
        final Boolean matches = Optional.ofNullable(value).map(v -> v.matches(pattern)).orElse(matchesIfAbsent);
        Assert.isTrue(matches, "Should match pattern " + pattern);
        return value;
    }

    @Override
    public String toString() {
        return String.format(
            "GitRepositoryUrl{protocol='%s', host='%s', username='%s', password='%s', namespace='%s', project='%s'}",
            protocol, host, username, password, namespace, project
        );
    }
}
