/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.github;

import com.epam.pipeline.entity.git.github.GitHubAccessToken;
import com.epam.pipeline.entity.git.github.GitHubInstallation;
import com.epam.pipeline.entity.git.github.GitHubInstallationAccount;
import com.epam.pipeline.entity.git.github.GitHubInstallationRepositories;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.AuthorizationUtils;
import com.epam.pipeline.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service class for managing GitHub App authentication and installation operations.
 * <p>
 * This service provides functionality to interact with GitHub Apps API.
 * </p>
 * <p>
 * The service enforces security through installation allowlists configured via system preferences.
 * Only installations explicitly listed in {@code GITHUB_ALLOWED_INSTALLATIONS} can be accessed.
 * </p>
 *
 * <h2>GitHub App Authentication Flow:</h2>
 * <ol>
 *   <li>Generate a JWT using the App's private key and App ID</li>
 *   <li>Locate the installation for a specific repository or organization</li>
 *   <li>Verify the installation is in the allowlist</li>
 *   <li>Generate an installation access token for API operations</li>
 * </ol>
 *
 * <h2>Configuration Requirements:</h2>
 * <ul>
 *   <li>{@code github.app.id}: GitHub App identifier</li>
 *   <li>{@code github.app.private.key.pem}: RSA private key in PEM format</li>
 *   <li>{@code github.allowed.installations}: Allowlist of installation IDs or account names</li>
 *   <li>{@code github.api.root.url}: GitHub API base URL</li>
 * </ul>
 */
@Service
@Slf4j
public class GitHubAppService {
    private static final int GITHUB_APP_JWT_MAX_TTL_SECONDS = 600;
    private static final String ACCEPT_HEADER = "Accept";
    private static final String ACCEPT_GITHUB_JSON = "application/vnd.github+json";
    private static final String X_GITHUB_API_VERSION = "2026-03-10";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";

    private final PreferenceManager preferenceManager;
    private final String privateKeyPem;

    public GitHubAppService(final PreferenceManager preferenceManager,
                            @Value("${github.app.private.key.pem:}") final String privateKeyPem) {
        this.preferenceManager = preferenceManager;
        this.privateKeyPem = privateKeyPem;
    }

    /**
     * Generates a GitHub App installation access token for a specific repository.
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     *   <li>Generates a JWT for GitHub App authentication</li>
     *   <li>Locates the installation associated with the repository</li>
     *   <li>Validates that the installation is in the allowlist
     *   ({@link com.epam.pipeline.manager.preference.SystemPreferences#GITHUB_ALLOWED_INSTALLATIONS})</li>
     *   <li>Generates and returns an installation access token</li>
     * </ol>
     * <p>
     * The returned token can be used to authenticate API requests for the specified repository
     * with the permissions granted to the GitHub App installation.
     * </p>
     *
     * @param projectName    the GitHub organization or username that owns the repository.
     *                       Must not be null or empty.
     * @param repositoryName the name of the repository within the project.
     *                       Must not be null or empty.
     * @return a GitHub installation access token that can be used for API authentication.
     * The token has a limited lifetime as determined by GitHub's API.
     */
    public String getGithubAppToken(final String projectName, final String repositoryName) {
        final String jwt = generateJWT();
        final GitHubInstallation installation = findInstallationId(projectName, repositoryName, jwt);
        assertInstallationAllowed(installation);
        return generateAccessToken(jwt, installation.getId());
    }

    /**
     * Retrieves all GitHub App installations that are permitted by the allowlist configuration.
     * <p>
     * This method fetches all installations where the GitHub App is installed and filters them
     * against the allowlist defined in {@code GITHUB_ALLOWED_INSTALLATIONS}. Installations can
     * be allowed by either their installation ID or account name (organization/user login).
     * </p>
     * <p>
     * The allowlist matching is case-insensitive for account names.
     * </p>
     *
     * @return a list of {@link GitHubInstallation} objects representing allowed installations.
     * Returns an empty list if no installations match the allowlist.
     */
    public List<GitHubInstallation> findAllowedInstallations() {
        final List<String> allowed = getAllowedInstallations();

        final String jwt = generateJWT();
        final List<GitHubInstallation> installations = findInstallations(jwt);
        return installations.stream()
                .filter(Objects::nonNull)
                .filter(installation -> isInstallationAllowed(installation, allowed))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all repositories accessible through a specific GitHub App installation.
     *
     * <p>
     * Only installations explicitly listed in
     * {@link com.epam.pipeline.manager.preference.SystemPreferences#GITHUB_ALLOWED_INSTALLATIONS}
     * can be accessed.
     * </p>
     *
     * @param installationId the GitHub App installation ID as a string.
     *                       Must not be null, empty, or blank.
     *                       Must contain only digits.
     *                       Must represent an existing installation.
     * @return a list of {@link GitHubRepository} objects accessible through the installation.
     * Returns an empty list if the installation has no accessible repositories.
     */
    public List<GitHubRepository> findRepositoriesByInstallation(final String installationId) {
        Assert.hasText(installationId, "Installation ID must not be null or empty.");
        Assert.state(NumberUtils.isDigits(installationId),
                String.format("Installation ID must contain only digits, but got: %s", installationId));

        final Long installationIdLong = Long.valueOf(installationId);
        final String jwt = generateJWT();
        final GitHubAppClient appClient = buildAppClient(jwt);

        final GitHubInstallation installation = findAppInstallation(appClient, installationIdLong)
                .orElseThrow(() -> new GitClientException(String.format(
                        "Installation with ID %s does not exist.", installationId)));

        assertInstallationAllowed(installation);

        final String accessToken = generateAccessToken(jwt, installationIdLong);
        final GitHubAppClient client = buildAppClient(accessToken);

        return GitHubUtils.fetchAllPages(client::getInstallationRepositories,
            responseBody -> Optional.ofNullable(responseBody)
                    .map(GitHubInstallationRepositories::getRepositories)
                    .orElse(Collections.emptyList()));
    }

    private String generateJWT() {
        final String appId = preferenceManager.getPreference(SystemPreferences.GITHUB_APP_ID);
        Assert.hasText(appId, "GitHub App ID shall be configured.");
        try {
            return JwtUtils.generateRsa256Jwt(privateKeyPem, appId, GITHUB_APP_JWT_MAX_TTL_SECONDS);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GitClientException("Failed to read GitHub App private key: " + e.getMessage());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error(e.getMessage(), e);
            throw new GitClientException("Failed to parse GitHub App private key: " + e.getMessage());
        }
    }

    private void assertInstallationAllowed(final GitHubInstallation installation) {
        final List<String> allowed = getAllowedInstallations();
        if (isInstallationAllowed(installation, allowed)) {
            return;
        }
        throw new GitClientException(String.format(
                "GitHub App installation %s is not allowed by github.allowed.installations.", installation.getId()));
    }

    private List<String> getAllowedInstallations() {
        final List<String> allowed = preferenceManager.getPreference(SystemPreferences.GITHUB_ALLOWED_INSTALLATIONS);
        if (CollectionUtils.isEmpty(allowed)) {
            throw new GitClientException("GitHub App installation access token generation is not allowed.");
        }
        return allowed;
    }

    private boolean isInstallationAllowed(final GitHubInstallation installation, final List<String> allowed) {
        // check by installation ID:
        final Long installationId = installation.getId();
        if (Objects.nonNull(installationId) && allowed.stream()
                .anyMatch(entry -> Objects.equals(entry, String.valueOf(installationId)))) {
            return true;
        }

        // check by installation account name (org or username):
        final String installationName = Optional.ofNullable(installation.getAccount())
                .map(GitHubInstallationAccount::getLogin)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        return StringUtils.isNotBlank(installationName) && allowed.stream()
                .anyMatch(installationName::equalsIgnoreCase);
    }

    private GitHubInstallation findInstallationId(final String projectName, final String repositoryName,
                                                  final String jwt) {
        final GitHubAppClient appClient = buildAppClient(jwt);
        return appClient.getRepositoryInstallation(projectName, repositoryName);
    }

    private List<GitHubInstallation> findInstallations(final String jwt) {
        final GitHubAppClient appClient = buildAppClient(jwt);
        return GitHubUtils.fetchAllPages(appClient::getAppInstallations);
    }

    private String generateAccessToken(final String jwt, final Long installationId) {
        final GitHubAppClient appClient = buildAppClient(jwt);
        final GitHubAccessToken tokenResponse = appClient.createInstallationAccessToken(installationId);
        return Optional.ofNullable(tokenResponse)
                .map(GitHubAccessToken::getToken)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new GitClientException(
                        "GitHub installation access token response did not contain a token."));
    }

    private GitHubAppClient buildAppClient(final String token) {
        final String apiUrl = preferenceManager.getPreference(SystemPreferences.GITHUB_API_ROOT_URL);
        return buildAppClient(apiUrl, token);
    }

    private GitHubAppClient buildAppClient(final String apiUrl, final String token) {
        final Map<String, String> headers = new HashMap<>();
        headers.put(ACCEPT_HEADER, ACCEPT_GITHUB_JSON);
        headers.put(API_VERSION_HEADER, X_GITHUB_API_VERSION);
        return new GitHubAppClient(apiUrl, AuthorizationUtils.buildBearerTokenAuth(token), null, headers);
    }

    private Optional<GitHubInstallation> findAppInstallation(final GitHubAppClient appClient,
                                                             final Long installationId) {
        try {
            return Optional.ofNullable(appClient.getAppInstallation(installationId));
        } catch (UnexpectedResponseStatusException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
