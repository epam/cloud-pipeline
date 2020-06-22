/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitFile;
import com.epam.pipeline.entity.git.GitGroup;
import com.epam.pipeline.entity.git.GitGroupRequest;
import com.epam.pipeline.entity.git.GitHookRequest;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitProjectRequest;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitTokenRequest;
import com.epam.pipeline.entity.git.GitlabUser;
import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.entity.git.UpdateGitFileRequest;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.utils.GitUtils;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Stream;

@Wither
@AllArgsConstructor
@NoArgsConstructor
public class GitlabClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabClient.class);

    private static final String TEMPLATE_DESCRIPTION = "description.txt";
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

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String INITIAL_COMMIT = "New pipeline initial commit";
    private static final String PUBLIC_VISIBILITY = "public";
    public static final String NEW_LINE = "\n";
    public static final long MAINTAINER = 40L;
    private static final String DOT_CHAR = ".";
    private static final String DOT_CHAR_URL_ENCODING_REPLACEMENT = "%2E";

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private String user;
    private String namespace;
    private String projectName;
    private String gitHost;
    private String adminToken;
    private String fullUrl;
    private Long adminId;
    private String adminName;
    private GitLabApi gitLabApi;

    /**
     * Indicates that user-provided adminToken shall be used for authentication. Mainly it means
     * that host is an external Gitlab.
     */
    private boolean externalHost;

    GitlabClient(String host, String namespace, String user, String adminToken, String project,
                         String fullUrl, Long gitAdminId, String adminName, boolean externalHost) {
        this.gitHost = host;
        this.namespace = namespace;
        this.projectName = project;
        this.adminToken = adminToken;
        this.fullUrl = fullUrl;
        this.adminId = gitAdminId;
        this.adminName = adminName;
        this.externalHost = externalHost;
        this.gitLabApi = buildGitLabApi(host, adminToken);
        this.user = user;
    }

    public static GitlabClient initializeGitlabClientFromRepositoryAndToken(String user, String repository,
                                                                            String token, Long adminId,
                                                                            String adminName, boolean externalHost) {
        final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(repository);
        final String host = gitRepositoryUrl.getProtocol() + gitRepositoryUrl.getHost();
        final String namespace = gitRepositoryUrl.getNamespace()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));

        final String userOrNamespace = externalHost ? gitRepositoryUrl.getUsername().orElse(namespace) : user;

        LOGGER.trace("Created Git client for repository {}", repository);
        return new GitlabClient(host, namespace, userOrNamespace, token, project,
                repository, adminId, adminName, externalHost);
    }

    public static GitlabClient initializeGitlabClientFromHostAndToken(String gitHost, String token, String userName,
                                                                      Long gitAdminId, String adminName) {
        return new GitlabClient(gitHost, adminName, userName, token, null, null, gitAdminId, adminName, false);
    }

    public GitCredentials buildCloneCredentials(boolean useEnvVars, boolean issueToken, Long duration)
            throws GitClientException {
        final String gitUrl = StringUtils.isNotBlank(fullUrl) ? fullUrl : gitHost;
        Assert.state(StringUtils.isNotBlank(gitUrl), "Gitlab URL is required to issue credentials.");
        final GitCredentials.GitCredentialsBuilder credentialsBuilder = GitCredentials.builder();
        if (StringUtils.isEmpty(adminToken)) {
            return credentialsBuilder.url(gitUrl).build();
        }
        final String cloneToken;
        final String userName;
        final String email;
        if (issueToken && !externalHost) {
            final GitlabUser gitlabUser = findUser(this.user)
                    .orElseGet(() -> GitlabUser.builder().username(adminName).id(adminId).build());
            userName = gitlabUser.getUsername();
            cloneToken = createImpersonationToken(projectName, gitlabUser.getId(), duration);
            email = gitlabUser.getEmail();
        } else {
            userName = externalHost ? this.user.replaceAll("@.*$", "") : adminName;
            cloneToken = adminToken;
            email = null;
        }

        GitRepositoryUrl repositoryUrl = GitRepositoryUrl.from(gitUrl);

        repositoryUrl = useEnvVars
                ? repositoryUrl.withUsername("${GIT_USER}").withPassword("${GIT_TOKEN}")
                : repositoryUrl.withUsername(userName).withPassword(cloneToken);

        LOGGER.debug("Ready url for user {} with adminToken {}", userName, cloneToken);
        return credentialsBuilder.url(repositoryUrl.asString())
                .userName(userName)
                .token(cloneToken)
                .email(email).build();
    }

    public GitCredentials buildCloneCredentials(boolean useEnvVars, Long duration) throws GitClientException {
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
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getRepositoryTree(projectId, path, revision, recursive));
    }

    public GitProject createTemplateRepository(Template template, String name, String description,
                                               boolean indexingEnabled, String hookUrl) throws GitClientException {
        return createGitProject(template, description,
                                GitUtils.convertPipeNameToProject(name),
                                indexingEnabled, hookUrl);
    }

    public boolean projectExists(String name) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, GitUtils.convertPipeNameToProject(name));
            Response<GitProject> response = gitLabApi.getProject(projectId).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public GitProject getProject() throws GitClientException {
        return getProject(projectName);
    }

    public GitProject getProject(String name) throws GitClientException {
        String project = GitUtils.convertPipeNameToProject(name);
        String projectId = makeProjectId(namespace, project);
        return execute(gitLabApi.getProject(projectId));
    }

    public GitProject createTemplateRepository(Template template, String description,
                                               boolean indexingEnabled, String hookUrl) throws GitClientException {
        Assert.notNull(this.projectName, "Project name cannot be empty");
        try {
            String repoName = this.projectName;
            return createGitProject(template, description, repoName, indexingEnabled, hookUrl);
        } catch (HttpClientErrorException e) {
            throw new GitClientException("Failed to create GIT repository: " + e.getMessage(), e);
        }
    }

    public void deleteRepository() throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        execute(gitLabApi.deleteProject(projectId));
    }

    public GitTagEntry getRepositoryRevision(String tag) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getRevision(projectId, tag));
    }

    public GitCommitEntry getRepositoryCommit(String commitId) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getCommit(projectId, commitId, null));
    }

    public List<GitTagEntry> getRepositoryRevisions(Long pageSize) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getRevisions(projectId, null, null));
    }

    public GitTagEntry createRepositoryRevision(String name, String ref, String message, String releaseDescription)
            throws GitClientException {
        if (name == null) {
            throw new GitClientException("Tag name is required");
        }
        if (ref == null) {
            throw new GitClientException("Ref (commit SHA, another tag name, or branch name) is required");
        }

        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.createRevision(projectId, name, ref, message, releaseDescription));
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
        String projectId = makeProjectId(namespace, projectName);
        String sinceDate = since != null ? DATE_FORMAT.format(since) : null;
        String untilDate = until != null ? DATE_FORMAT.format(until) : null;
        return execute(gitLabApi.getCommits(projectId, refName, sinceDate, untilDate, null, null, null));
    }

    /**
     * Loads Gitlab version information
     * @return a GitlabVersion object
     * @throws GitClientException
     */
    public GitlabVersion getVersion() throws GitClientException {
        return execute(gitLabApi.getVersion());
    }

    public GitCommitEntry commit(GitPushCommitEntry commitEntry) throws GitClientException {
        // use user as author if it exists on gitlab
        findUser(user).ifPresent(u -> {
            commitEntry.setAuthorEmail(u.getEmail());
            commitEntry.setAuthorName(u.getUsername());
        });

        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.postCommit(projectId, commitEntry));
    }

    public byte[] getFileContents(String path, String revision) throws GitClientException {
        return getFileContents(null, path, revision);
    }

    public byte[] getFileContents(String projectId, String path, String revision) throws GitClientException {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        if (StringUtils.isBlank(projectId)) {
            projectId = makeProjectId(namespace, projectName);
        }
        GitFile gitFile = execute(gitLabApi.getFiles(projectId, path, revision));
        return Base64.getDecoder().decode(gitFile.getContent());
    }

    public byte[] getTruncatedFileContents(final String path, final String revision,
                                           final int byteLimit) throws GitClientException {
        return getTruncatedFileContents(null, path, revision, byteLimit);
    }

    /**
     * Retrieves only first bytes of file content to avoid out-of-memory issues in case of enormous file size. If the
     * file size is less than the limit, then the whole file is loaded.
     */
    public byte[] getTruncatedFileContents(final String projectId, final String path,
                                           final String revision, final int byteLimit) throws GitClientException {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        final String currentProjectId = StringUtils.isBlank(projectId)
                                        ? makeProjectId(namespace, projectName)
                                        : projectId;

        try {
            final Call<ResponseBody> filesRawContent = gitLabApi.
                getFilesRawContent(currentProjectId, encodePath(path), revision);
            final ResponseBody body = filesRawContent.execute().body();
            if (body != null) {
                try(InputStream inputStream = body.byteStream()) {
                    final int bufferSize = calculateBufferSize(byteLimit, body);
                    final byte[] receivedContent = new byte[bufferSize];
                    inputStream.read(receivedContent);
                    return receivedContent;
                }
            } else {
                return new byte[0];
            }
        } catch (IOException e) {
            throw new GitClientException("Error receiving raw file content!", e);
        }
    }

    public GitRepositoryEntry createProjectHook(String hookUrl) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return addProjectHook(projectId, hookUrl);
    }

    public GitProject updateProjectName(final String currentName, final String newName) throws GitClientException {
        return updateProjectName(currentName, newName, namespace);
    }

    public GitProject updateProjectName(final String currentName, final String newName, final String namespace)
            throws GitClientException {
        final String normalizedNewName = GitUtils.convertPipeNameToProject(newName);
        return execute(gitLabApi.updateProject(makeProjectId(namespace, GitUtils.convertPipeNameToProject(currentName)),
                                               GitProjectRequest.builder()
                                                   .name(normalizedNewName)
                                                   .path(normalizedNewName)
                                                   .build()));
    }

    public GitGroup createGroup(final String newGroupName) throws GitClientException {
        return execute(gitLabApi.createGroup(
                GitGroupRequest.builder()
                        .name(newGroupName)
                        .path(newGroupName)
                        .build()));
    }

    public GitGroup deleteGroup(final String groupName) throws GitClientException {
        return execute(gitLabApi.deleteGroup(groupName));
    }

    public GitProject forkProject(final String projectName, final String namespaceFrom, final String namespaceTo)
            throws GitClientException {
        return execute(gitLabApi.forkProject(
                makeProjectId(namespaceFrom, GitUtils.convertPipeNameToProject(projectName)), namespaceTo));
    }

    private <R> R execute(Call<R> call) throws GitClientException {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new UnexpectedResponseStatusException(HttpStatus.valueOf(response.code()),
                        response.errorBody() != null ? response.errorBody().string() : "");
            }
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    private void createFile(GitProject project, String path, String content) {
        try {
            final Response<GitFile> response = gitLabApi.createFiles(
                    project.getId().toString(), path, buildCreateFileRequest(content)).execute();
            if (!response.isSuccessful()) {
                throw new HttpException(response);
            }
        } catch (IOException | GitClientException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private UpdateGitFileRequest buildCreateFileRequest(final String content)
            throws GitClientException {

        final UpdateGitFileRequest.UpdateGitFileRequestBuilder requestBuilder = UpdateGitFileRequest.builder()
                .branch(DEFAULT_BRANCH)
                .message(INITIAL_COMMIT)
                .content(content);

        // use user as author if it exists on gitlab
        findUser(user).ifPresent(u -> {
            requestBuilder.authorEmail(u.getEmail());
            requestBuilder.authorName(u.getUsername());
        });

        return requestBuilder.build();
    }

    private String getFileContent(String path) {
        try (InputStream stream = new FileInputStream(path)) {
            List<String> lines = IOUtils.readLines(stream);
            return String.join(NEW_LINE, lines);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }
    }

    private String makeProjectId(String gitUserName, String projectName) {
        return gitUserName + "/" + projectName;
    }

    private String createImpersonationToken(String repositoryName, Long userId, Long duration)
            throws GitClientException {
        //issue adminToken for one day
        final String tokenName = repositoryName + "-adminToken";
        final LocalDate endDay = LocalDate.now().plusDays(duration);
        return createImpersonationToken(tokenName, userId, endDay);
    }

    private String createImpersonationToken(String tokenName, Long userId, LocalDate expires)
            throws GitClientException {
        if (adminId == null) {
            throw new IllegalArgumentException("Token may be issued only for local Gitlab.");
        }
        return execute(gitLabApi.issueToken(
                String.valueOf(userId),
                GitTokenRequest.builder()
                        .name(tokenName)
                        .expires(DATE_TIME_FORMATTER.format(expires))
                        .scopes(Collections.singletonList("api")).build(),
                adminToken)).getToken();
    }

    private Optional<GitlabUser> findUser(String userName) throws GitClientException {
        return Optional.of(execute(gitLabApi.searchUser(userName)))
                .map(List::stream)
                .flatMap(Stream::findFirst);
    }

    private GitProject createRepo(String repoName, String description) throws GitClientException {
        GitProjectRequest gitProject = GitProjectRequest.builder().name(repoName).description(description)
                .visibility(PUBLIC_VISIBILITY).build();
        return execute(gitLabApi.createProject(gitProject));
    }

    private GitRepositoryEntry addProjectHook(String projectId, String hookUrl) throws GitClientException {
        return execute(
                gitLabApi.addProjectHook(
                        projectId,
                        GitHookRequest.builder().hookUrl(hookUrl)
                                .pushEvents(true).branch(DEFAULT_BRANCH)
                                .tagPushEvents(true).sslVerify(false).build()));
    }

    private GitProject createGitProject(Template template, String description, String repoName,
                                        boolean indexingEnabled, String hookUrl) throws GitClientException {
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
        } catch (UnexpectedResponseStatusException e) {
            createFile(project, DEFAULT_README, README_DEFAULT_CONTENTS);
        } catch (GitClientException exception) {
            LOGGER.debug(exception.getMessage(), exception);
        }
        return project;
    }

    private GitLabApi buildGitLabApi(final String gitHost, final String adminToken) {
        return new GitLabApiBuilder(gitHost, adminToken).build();
    }

    private void uploadFolder(Template template, String repoName, GitProject project) throws GitClientException {
        String templateRootFolder = Paths.get(template.getDirPath()).toAbsolutePath().toString();
        if (!Paths.get(template.getDirPath()).toFile().exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(template.getDirPath()))) {
            walk.forEach(path -> {
                File file = path.toFile();
                if (file.isFile()) {
                    String relativePath = file.getAbsolutePath().substring(templateRootFolder.length() + 1);
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
                }
            });
        } catch (IOException e) {
            throw new GitClientException(e.getMessage());
        }
    }

    private String normalizePath(String path) {
        if (File.separator.equals("\\")) {
            return path.replaceAll("\\\\", "/");
        }
        return path;
    }

    private int calculateBufferSize(final int byteLimit, final ResponseBody body) {
        final long length = body.contentLength();
        return (length >= 0 && length <= Integer.MAX_VALUE)
               ? Math.min((int) length, byteLimit)
               : byteLimit;
    }

    private String encodePath(final String path) throws UnsupportedEncodingException {
        return URLEncoder.encode(path, StandardCharsets.UTF_8.toString()).replace(DOT_CHAR,
                                                                                  DOT_CHAR_URL_ENCODING_REPLACEMENT);
    }
}
