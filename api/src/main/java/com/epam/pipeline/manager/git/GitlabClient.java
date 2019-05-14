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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.controller.vo.PipelineSourceItemErrorVO;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitFile;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.entity.git.GitlabUser;
import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Contains logic, connected with GitLab operations
 */
@Wither
@AllArgsConstructor
@NoArgsConstructor
public class GitlabClient {
    public static final String TEMPLATE_DESCRIPTION = "description.txt";
    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabClient.class);

    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";
    private static final String PUBLIC_VISIBILITY = "public";

    // TODO: move root url to properties
    private static final String GITLAB_API_ROOT = "%s/api/v3/"; //TODO: fix to V4 in future
    private static final String GITLAB_API_V4_ROOT = "%s/api/v4/";
    private static final String GIT_GET_SOURCES_ROOT_URL = GITLAB_API_ROOT +
            "projects/%s/repository/tree";
    private static final String GIT_GET_SOURCE_FILE_URL = GITLAB_API_ROOT + "projects/%s/repository/files";
    private static final String GIT_REVISIONS = GITLAB_API_ROOT + "projects/%s/repository/tags";
    private static final String GIT_REVISION = GITLAB_API_ROOT + "projects/%s/repository/tags/%s";
    private static final String GIT_POST_PROJECT_URL = GITLAB_API_ROOT + "projects";
    private static final String GIT_PROJECT_URL = GITLAB_API_ROOT + "projects/%s";
    private static final String GIT_POST_FILE_URL = GITLAB_API_ROOT + "projects/%s/repository/files";
    private static final String GIT_COMMITS = GITLAB_API_ROOT + "projects/%s/repository/commits";
    private static final String GIT_GET_COMMIT = GITLAB_API_ROOT + "projects/%s/repository/commits/%s";
    private static final String GITLAB_API_USERS = GITLAB_API_ROOT + "users";
    private static final String GIT_ISSUE_TOKEN = GITLAB_API_USERS + "/%d/impersonation_tokens";
    private static final String GITLAB_VERSION_URL = GITLAB_API_V4_ROOT + "version";
    private static final String GITLAB_PROJECT_HOOKS = GIT_PROJECT_URL + "/hooks";

    private static final String README_DEFAULT_CONTENTS = "# Job definition\n\n"
            + "This is an initial job definition `README`\n\n"
            + "Feel free to customize it\n\n# Quick start\n\n"
            + "1. Modify job scripts using `CODE` tab above\n"
            + "2. Fine - tune job parameters and execution environment using `CONFIGURATION`"
            + " tab above or keep default values\n"
            + "3. Launch you job using `RUN` button\n";
    private static final String DEFAULT_README = "docs/README.md";
    private static final String CONFIG_JSON = "config.json";
    private static final String TEMPLATE_PLACEHOLDER = "@";
    private static final String DEFAULT_BRANCH = "master";

    private DateTimeFormatter gitDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String userName;
    private String namespace;
    private String projectName;
    private String gitHost;
    private String token;
    private String fullUrl;
    private Long adminId;
    private String adminName;
    /**
     * Indicates that user-provided token shall be used for authentication. Mainly it means
     * that host is an external Gitlab.
     */
    private boolean externalHost;

    public GitlabClient(String host, String namespace, String user, String token, String project,
            String fullUrl, Long gitAdminId, String adminName, boolean externalHost) {
        this.gitHost = host;
        this.namespace = namespace;
        this.userName = user;
        this.projectName = project;
        this.token = token;
        this.fullUrl = fullUrl;
        this.adminId = gitAdminId;
        this.adminName = adminName;
        this.externalHost = externalHost;
    }

    public static GitlabClient initializeGitlabClientFromRepositoryAndToken(String user, String repository,
            String token, Long adminId, String adminName, boolean externalHost) {
        final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(repository);
        final String host = gitRepositoryUrl.getProtocol() + gitRepositoryUrl.getHost();
        final String namespace = gitRepositoryUrl.getNamespace()
                                                 .orElseThrow(() ->
                                                         new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                                               .orElseThrow(() ->
                                                       new IllegalArgumentException("Invalid repository URL format"));

        final String userOrNamespace = externalHost ? gitRepositoryUrl.getUsername().orElse(namespace) : user;

        LOGGER.trace("Created Git client for repository {}", repository);
        return new GitlabClient(host, namespace, userOrNamespace, token, project,
                repository, adminId, adminName, externalHost);
    }

    public static GitlabClient initializeGitlabClientFromHostAndToken(String gitHost, String token,
            Long gitAdminId, String adminName) {
        return new GitlabClient(gitHost, null, null, token, null, null, gitAdminId, adminName, false);
    }

    public GitCredentials buildCloneCredentials(boolean useEnvVars, boolean issueToken, Long duration) {
        final String gitUrl = StringUtils.isNotBlank(fullUrl) ? fullUrl : gitHost;
        Assert.state(StringUtils.isNotBlank(gitUrl), "Gitlab URL is required to issue credentials.");
        final GitCredentials.GitCredentialsBuilder credentialsBuilder = GitCredentials.builder();
        if (StringUtils.isEmpty(token)) {
            return credentialsBuilder.url(gitUrl).build();
        }
        final String cloneToken;
        final String userName;
        final String email;
        if (issueToken && !externalHost) {
            final GitlabUser user = findUser(this.userName)
                    .orElseGet(() -> GitlabUser.builder()
                            .username(adminName).id(adminId).build());
            userName = user.getUsername();
            cloneToken = createImpersonationToken(projectName, user.getId(), duration);
            email = user.getEmail();
        } else {
            userName = externalHost ? this.userName.replaceAll("@.*$", "") : adminName;
            cloneToken = token;
            email = null;
        }

        GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(gitUrl);

        repositoryUrl = useEnvVars
            ? repositoryUrl.withUsername("${GIT_USER}").withPassword("${GIT_TOKEN}")
            : repositoryUrl.withUsername(userName).withPassword(cloneToken);

        LOGGER.debug("Ready url for user {} with token {}", userName, cloneToken);
        return credentialsBuilder.url(repositoryUrl.asString())
                .userName(userName)
                .token(cloneToken)
                .email(email).build();
    }

    public GitCredentials buildCloneCredentials(boolean useEnvVars, Long duration) {
        return buildCloneCredentials(useEnvVars, false, duration);
    }

    /**
     * Retrieves repository contents - a list of files in a repository
     * @param path a path in a repository to browse
     * @return a list of {@link GitRepositoryEntry}, representing files and folders
     * @throws GitClientException if something goes wrong
     */
    public List<GitRepositoryEntry> getRepositoryContents(String path,
                                                          String revision,
                                                          boolean recursive) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);

            Map<String, Object> params = new HashMap<>();
            if (StringUtils.isNotBlank(path)) {
                params.put("path", path);
            }
            if (StringUtils.isNotBlank(revision)) {
                params.put("ref_name", revision);
            }
            params.put("recursive", recursive);

            String url = addUrlParameters(String.format(GIT_GET_SOURCES_ROOT_URL, gitHost, projectId), params);
            URI uri = new URI(url);

            LOGGER.trace("Getting repository contents on path {}, URL: {}", path, uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<List<GitRepositoryEntry>> sourcesResponse = template.exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<List<GitRepositoryEntry>>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            throw new GitClientException(e);
        }
    }

    public GitProject createTemplateRepository(Template template,
                                               String name,
                                               String description,
                                               boolean indexingEnabled,
                                               String hookUrl)
            throws GitClientException {
        try {
            return createGitProject(template, description, convertPipeNameToProject(name), indexingEnabled, hookUrl);
        } catch (IOException | URISyntaxException | HttpClientErrorException e) {
            throw new GitClientException("Failed to create GIT repository: " + e.getMessage(), e);
        }
    }

    private String convertPipeNameToProject(String name) {
        return name.trim().toLowerCase().replaceAll("[^\\w\\s]", "").replaceAll("\\s+", "-");
    }

    public boolean projectExists(String name) throws GitClientException {
        try {
            ResponseEntity<GitProject> response = loadProject(name);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new UnexpectedResponseStatusException(e.getStatusCode());
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public GitProject getProject() throws GitClientException {
        return getProject(projectName);
    }

    public GitProject getProject(String name) throws GitClientException {
        try {
            ResponseEntity<GitProject> response = loadProject(name);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throw new UnexpectedResponseStatusException(e.getStatusCode());
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    private ResponseEntity<GitProject> loadProject(String name) throws URISyntaxException,
            UnsupportedEncodingException {
        String project = convertPipeNameToProject(name);
        URI uri = new URI(String.format(GIT_PROJECT_URL, gitHost, makeProjectId(adminName, project)));
        return new RestTemplate().exchange(uri, HttpMethod.GET, getAuthHeaders(),
                new ParameterizedTypeReference<GitProject>() {});
    }

    public GitProject createTemplateRepository(Template template,
                                               String description,
                                               boolean indexingEnabled,
                                               String hookUrl)
            throws GitClientException {
        Assert.notNull(this.projectName, "Project name cannot be empty");
        try {
            String repoName = this.projectName;
            return createGitProject(template, description, repoName, indexingEnabled, hookUrl);
        } catch (IOException | URISyntaxException | HttpClientErrorException e) {
            throw new GitClientException("Failed to create GIT repository: " + e.getMessage(), e);
        }
    }

    public void deleteRepository()
            throws UnexpectedResponseStatusException, URISyntaxException, UnsupportedEncodingException {

        String projectId = makeProjectId(namespace, projectName);
        String url = addUrlParameters(String.format(GIT_PROJECT_URL, gitHost, projectId), null);
        URI uri = new URI(url);
        ResponseEntity<Void> response = new RestTemplate().exchange(uri,
                HttpMethod.DELETE, getAuthHeaders(), new ParameterizedTypeReference<Void>() {});

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new UnexpectedResponseStatusException(response.getStatusCode());
        }
    }

    public GitTagEntry getRepositoryRevision(String tag) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            String url = addUrlParameters(String.format(GIT_REVISION, gitHost, projectId, tag), new HashMap<>());
            URI uri = new URI(url);

            LOGGER.trace("Getting repository revisions from URL: {}", uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<GitTagEntry> sourcesResponse = template.exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<GitTagEntry>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException | HttpClientErrorException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    private HttpEntity getAuthHeaders() {
        HttpHeaders headers = getHeaders();
        headers.add(TOKEN_HEADER, token);
        return new HttpEntity(headers);
    }

    public GitCommitEntry getRepositoryCommit(String commitId) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            String url = addUrlParameters(String.format(GIT_GET_COMMIT, gitHost, projectId, commitId),
                    new HashMap<>());
            URI uri = new URI(url);

            LOGGER.trace("Getting repository commit {} from URL: {}", commitId, uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<GitCommitEntry> sourcesResponse = template.exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<GitCommitEntry>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException | HttpClientErrorException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public List<GitTagEntry> getRepositoryRevisions(Long pageSize) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            Map<String, Object> params = new HashMap<>();
            if (pageSize != null) {
                params.put("per_page", pageSize);
            }

            String url = addUrlParameters(String.format(GIT_REVISIONS, gitHost, projectId), params);
            URI uri = new URI(url);

            LOGGER.trace("Getting repository revisions from URL: {}", uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<List<GitTagEntry>> sourcesResponse = template.exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<List<GitTagEntry>>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            throw new GitClientException(e);
        }
    }

    public GitTagEntry createRepositoryRevision(String name, String ref, String message, String releaseDescription)
            throws GitClientException {
        if (name == null) {
            throw new GitClientException("Tag name is required");
        }
        if (ref == null) {
            throw new GitClientException("Ref (commit SHA, another tag name, or branch name) is required");
        }
        try {
            String projectId = makeProjectId(namespace, projectName);
            Map<String, Object> params = new HashMap<>();
            params.put("tag_name", name);
            params.put("ref", ref);
            if (message != null) {
                params.put("message", message);
            }
            if (releaseDescription != null) {
                params.put("release_description", releaseDescription);
            }

            String url = addUrlParameters(String.format(GIT_REVISIONS, gitHost, projectId), params);
            URI uri = new URI(url);

            LOGGER.trace("Creating new tag using URL: {}", uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<GitTagEntry> sourcesResponse = template.exchange(uri, HttpMethod.POST,
                    getAuthHeaders(), new ParameterizedTypeReference<GitTagEntry>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.CREATED) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            throw new GitClientException(e);
        }
    }

    public List<GitCommitEntry> getCommits() throws GitClientException {
        return this.getCommits(null, null, null);
    }

    public List<GitCommitEntry> getCommits(String refName) throws GitClientException {
        return this.getCommits(refName, null, null);
    }

    public List<GitCommitEntry> getCommits(String refName, Date since) throws GitClientException {
        return this.getCommits(refName, since, null);
    }

    public List<GitCommitEntry> getCommits(String refName, Date since, Date until) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            Map<String, Object> params = new HashMap<>();
            if (refName != null) {
                params.put("ref_name", refName);
            }
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);
            if (since != null) {
                params.put("since", df.format(since));
            }
            if (until != null) {
                params.put("until", df.format(until));
            }
            String url = addUrlParameters(String.format(GIT_COMMITS, gitHost, projectId), params);
            URI uri = new URI(url);

            LOGGER.trace("Getting repository commits from URL: {}", uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<List<GitCommitEntry>> sourcesResponse = template.exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<List<GitCommitEntry>>() {});

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return sourcesResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException | HttpClientErrorException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    /**
     * Loads Gitlab version information
     * @return a GitlabVersion object
     * @throws GitClientException
     */
    public GitlabVersion getVersion() throws GitClientException {
        try {
            URI uri = new URI(String.format(GITLAB_VERSION_URL, gitHost));
            ResponseEntity<GitlabVersion> versionResponse = new RestTemplate().exchange(uri, HttpMethod.GET,
                                                                                getAuthHeaders(), GitlabVersion.class);
            if (versionResponse.getStatusCode() == HttpStatus.OK) {
                return versionResponse.getBody();
            } else {
                throw new UnexpectedResponseStatusException(versionResponse.getStatusCode());
            }
        } catch (URISyntaxException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public GitCommitEntry commit(GitPushCommitEntry commitEntry) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            String url = addUrlParameters(String.format(GIT_COMMITS, gitHost, projectId), null);
            URI uri = new URI(url);

            LOGGER.trace("Performing commit; URL: {}", uri);

            HttpHeaders headers = getHeaders();
            headers.add(TOKEN_HEADER, token);
            HttpEntity entity = new HttpEntity<>(commitEntry, headers);

            ResponseEntity<GitCommitEntry> response = new RestTemplate().exchange(uri,
                    HttpMethod.POST, entity, new ParameterizedTypeReference<GitCommitEntry>() {});
            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                PipelineSourceItemErrorVO error = mapper
                        .readValue(e.getResponseBodyAsByteArray(), PipelineSourceItemErrorVO.class);
                throw new GitClientException(error.getMessage());
            } catch (IOException e1) {
                throw new GitClientException(e.getMessage(), e);
            }
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public byte[] getFileContents(String path, String revision) throws GitClientException {
        return getFileContents(null, path, revision);
    }

    public byte[] getFileContents(String projectId, String path, String revision) throws GitClientException {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        try {
            if (StringUtils.isBlank(projectId)) {
                projectId = makeProjectId(namespace, projectName);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("file_path", path);
            params.put("ref", revision);

            String url = addUrlParameters(String.format(GIT_GET_SOURCE_FILE_URL, gitHost, projectId), params);
            URI uri = new URI(url);

            LOGGER.trace("Getting file contents on path {}, URL: {}", path, uri);

            RestTemplate template = new RestTemplate();
            ResponseEntity<GitFile> sourcesResponse = template.exchange(uri, HttpMethod.GET, getAuthHeaders(),
                    GitFile.class);

            if (sourcesResponse.getStatusCode() == HttpStatus.OK) {
                return Base64.getDecoder().decode(sourcesResponse.getBody().getContent());
            } else {
                throw new UnexpectedResponseStatusException(sourcesResponse.getStatusCode());
            }
        } catch (UnsupportedEncodingException | URISyntaxException | UnexpectedResponseStatusException e) {
            throw new GitClientException(e);
        }
    }

    public GitRepositoryEntry createProjectHook(String hookUrl) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, projectName);
            return addProjectHook(projectId, hookUrl);
        } catch (UnsupportedEncodingException | UnexpectedResponseStatusException | URISyntaxException e) {
            throw new GitClientException("Failed to add hook to git repository: " + e.getMessage(), e);
        }
    }

    private String addUrlParameters(String rootUrl, Map<String, Object> params)
            throws UnsupportedEncodingException {
        if (params == null || params.isEmpty()) {
            return rootUrl;
        }

        StringBuilder builder = new StringBuilder().append("?");
        int i = 0;
        for (Map.Entry<String, Object> param : params.entrySet()) {
            if (i != 0) {
                builder.append('&');
            }

            builder.append(param.getKey())
                    .append('=')
                    .append(URLEncoder.encode(param.getValue().toString(),
                            Charset.defaultCharset().displayName()));
            i++;
        }

        return rootUrl + builder.toString();
    }

    private String makeProjectId(String gitUserName, String projectName) throws UnsupportedEncodingException {
        return URLEncoder.encode(gitUserName + "/" + projectName, "UTF-8");
    }

    private String getFileContent(String path) {
        try (InputStream stream = new FileInputStream(path)) {
            List<String> lines = IOUtils.readLines(stream);
            return lines.stream()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }
    }

    private void createFile(GitProject project, String path, String content)
            throws URISyntaxException, UnexpectedResponseStatusException, UnsupportedEncodingException {
        HttpHeaders headers = getHeaders();
        headers.add(TOKEN_HEADER, token);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("file_path", path);
        parameters.put("branch_name", DEFAULT_BRANCH);
        parameters.put("commit_message", "New pipeline initial commit");
        parameters.put("content", content);

        String url = addUrlParameters(String.format(GIT_POST_FILE_URL, gitHost,
                project.getId().toString(),
                URLEncoder.encode(path, Charset.defaultCharset().displayName())), parameters);
        URI uri = new URI(url);
        HttpEntity entity = new HttpEntity(headers);

        ResponseEntity<GitRepositoryEntry> response = new RestTemplate().exchange(uri,
                HttpMethod.POST, entity, new ParameterizedTypeReference<GitRepositoryEntry>() {});
        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new UnexpectedResponseStatusException(response.getStatusCode());
        }
    }

    private String createImpersonationToken(String repositoryName, Long userId, Long duration) {
        //issue token for one day
        final String tokenName = repositoryName + "-token";
        final LocalDate endDay = LocalDate.now().plusDays(duration);
        return createImpersonationToken(tokenName, userId, endDay);
    }

    private String createImpersonationToken(String tokenName, Long userId, LocalDate expires) {
        if (adminId == null) {
            throw new IllegalArgumentException("Token may be issued only for local Gitlab.");
        }
        HttpHeaders headers = getHeaders();
        headers.add(TOKEN_HEADER, token);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", tokenName);
        parameters.put("expires_at", gitDateFormat.format(expires));
        parameters.put("scopes[]", "api");
        HttpEntity entity = new HttpEntity<>(headers);

        try {
            String url = addUrlParameters(String.format(GIT_ISSUE_TOKEN, gitHost, userId), parameters);
            URI uri = new URI(url);
            ResponseEntity<GitToken> response = new RestTemplate().exchange(
                uri, HttpMethod.POST, entity, new ParameterizedTypeReference<GitToken>() {}
            );
            if (response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody().getToken();
            } else {
                throw new IllegalArgumentException("Failed to issue Gitlab token");
            }
        } catch (URISyntaxException | UnsupportedEncodingException | HttpClientErrorException e) {
            throw new IllegalArgumentException("Failed to issue Gitlab token");
        }
    }

    private Optional<GitlabUser> findUser(String userName) {
        final RestTemplate template = new RestTemplate();
        final HttpEntity headers = getAuthHeaders();
        final String searchUri = UriComponentsBuilder.fromHttpUrl(String.format(GITLAB_API_USERS, gitHost))
                                                     .queryParam("search", userName)
                                                     .build().toUriString();
        ResponseEntity<List<GitlabUser>> response = template.exchange(
            searchUri, HttpMethod.GET, headers, new ParameterizedTypeReference<List<GitlabUser>>() {}
        );
        return Optional.of(response)
                       .filter(r -> r.getStatusCode() == HttpStatus.OK)
                       .map(ResponseEntity::getBody)
                       .map(List::stream)
                       .flatMap(Stream::findFirst);
    }

    private GitProject createRepo(String repoName, String description)
            throws UnexpectedResponseStatusException, URISyntaxException {

        HttpHeaders headers = getHeaders();
        headers.add(TOKEN_HEADER, token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("name", repoName);
        if (!StringUtils.isEmpty(description)) {
            parameters.put("description", description);
        }
        parameters.put("visibility", PUBLIC_VISIBILITY);
        HttpEntity entity = new HttpEntity<>(parameters, headers);

        URI uri = new URI(String.format(GIT_POST_PROJECT_URL, gitHost));
        LOGGER.trace("Creating repo {}, URL: {}, token: {}", repoName, uri, token);
        ResponseEntity<GitProject> response = new RestTemplate().exchange(uri,
                HttpMethod.POST, entity, new ParameterizedTypeReference<GitProject>() {});
        if (response.getStatusCode() == HttpStatus.CREATED) {
            return response.getBody();
        } else {
            throw new UnexpectedResponseStatusException(response.getStatusCode());
        }
    }

    private GitProject createGitProject(Template template,
                                        String description,
                                        String repoName,
                                        boolean indexingEnabled,
                                        String hookUrl)
            throws UnexpectedResponseStatusException, URISyntaxException, IOException {
        GitProject project = createRepo(repoName, description);
        if (indexingEnabled) {
            addProjectHook(String.valueOf(project.getId()), hookUrl);
        }
        uploadFolder(template, repoName, project);
        try {
            boolean fileExists = getFileContents(project.getId().toString(), DEFAULT_README, DEFAULT_BRANCH) != null;
            if (!fileExists) {
                createFile(project, DEFAULT_README, README_DEFAULT_CONTENTS);
            }
        } catch (HttpClientErrorException e) {
            createFile(project, DEFAULT_README, README_DEFAULT_CONTENTS);
        } catch (GitClientException exception) {
            LOGGER.debug(exception.getMessage(), exception);
        }
        return project;
    }

    private GitRepositoryEntry addProjectHook(String projectId, String hookUrl)
            throws UnsupportedEncodingException, UnexpectedResponseStatusException, URISyntaxException {
        HttpHeaders headers = getHeaders();
        headers.add(TOKEN_HEADER, token);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("url", hookUrl);
        parameters.put("push_events", true);
        parameters.put("push_events_branch_filter", DEFAULT_BRANCH);
        parameters.put("tag_push_events", true);
        parameters.put("enable_ssl_verification", false);

        String url = addUrlParameters(String.format(GITLAB_PROJECT_HOOKS, gitHost, projectId), parameters);
        URI uri = new URI(url);
        HttpEntity entity = new HttpEntity(headers);

        ResponseEntity<GitRepositoryEntry> response = new RestTemplate().exchange(uri,
                HttpMethod.POST, entity, new ParameterizedTypeReference<GitRepositoryEntry>() {});
        if (response.getStatusCode() == HttpStatus.CREATED) {
            return response.getBody();
        } else {
            throw new UnexpectedResponseStatusException(response.getStatusCode());
        }
    }

    private void uploadFolder(Template template, String repoName, GitProject project)
            throws IOException {
        String templateRootFolder = Paths.get(template.getDirPath()).toAbsolutePath().toString();
        if (!Paths.get(template.getDirPath()).toFile().exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(template.getDirPath()))) {
            walk.forEach(path -> {
                File file = path.toFile();
                if (file.isFile()) {
                    String relativePath = file.getAbsolutePath().substring(templateRootFolder.length() + 1);
                    try {
                        if (relativePath.equals(TEMPLATE_DESCRIPTION)) {
                            return;
                        }
                        if (relativePath.equals(CONFIG_JSON)) {
                            createFile(project, CONFIG_JSON,
                                    getFileContent(file.getAbsolutePath()).replaceAll(
                                            TEMPLATE_PLACEHOLDER, repoName));
                        } else {
                            createFile(project, normalizePath(relativePath.replaceAll("@", repoName)),
                                    getFileContent(file.getAbsolutePath()));
                        }
                    } catch (URISyntaxException | UnexpectedResponseStatusException | UnsupportedEncodingException e) {
                        throw new IllegalArgumentException(e.getMessage(), e);
                    }
                }
            });
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return httpHeaders;
    }

    private String normalizePath(String path) {
        if (File.separator.equals("\\")) {
            return path.replaceAll("\\\\", "/");
        }
        return path;
    }
}
