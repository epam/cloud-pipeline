/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.git.GitProjectStorage;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitTokenRequest;
import com.epam.pipeline.entity.git.GitlabBranch;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import com.epam.pipeline.entity.git.GitlabUpload;
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
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.web.util.UriUtils;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.git.RestApiUtils.execute;

@Wither
@AllArgsConstructor
@NoArgsConstructor
public class GitlabClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabClient.class);

    private static final String PRIVATE_TOKEN = "PRIVATE-TOKEN";
    private static final String DATA_FORMAT = "yyyy-MM-dd";
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
    private static final String INITIAL_COMMIT = "Initial commit";
    private static final String PUBLIC_VISIBILITY = "public";
    public static final String NEW_LINE = "\n";
    public static final long MAINTAINER = 40L;
    public static final String DOT_CHAR = ".";
    public static final String DOT_CHAR_URL_ENCODING_REPLACEMENT = "%2E";
    public static final String GITKEEP_FILE = ".gitkeep";
    public static final String EMAIL_SEPARATOR = "@";

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
    private String apiVersion;

    /**
     * Indicates that user-provided adminToken shall be used for authentication. Mainly it means
     * that host is an external Gitlab.
     */
    private boolean externalHost;

    GitlabClient(String host, String namespace, String user, String adminToken, String project,
                 String fullUrl, Long gitAdminId, String adminName, boolean externalHost,
                 String apiVersion) {
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
        this.apiVersion = apiVersion;
    }

    public static GitlabClient initializeGitlabClientFromRepositoryAndToken(final String user,
                                                                            final String repository,
                                                                            final String token,
                                                                            final Long adminId,
                                                                            final String adminName,
                                                                            final boolean externalHost,
                                                                            final String apiVersion) {
        final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(repository);
        final String host = gitRepositoryUrl.getProtocol() + gitRepositoryUrl.getHost();
        final String namespace = gitRepositoryUrl.getNamespace()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));

        final String userOrNamespace = externalHost ? gitRepositoryUrl.getUsername().orElse(namespace) : user;

        LOGGER.trace("Created Git client for repository {}", repository);
        return new GitlabClient(host, namespace, userOrNamespace, token, project,
                repository, adminId, adminName, externalHost, apiVersion);
    }

    public static GitlabClient initializeGitlabClientFromHostAndToken(final String gitHost, final String token,
                                                                      final String userName, final Long gitAdminId,
                                                                      final String adminName, final String apiVersion) {
        return new GitlabClient(gitHost, adminName, userName, token, null, null, gitAdminId,
                adminName, false, apiVersion);
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
                                               boolean indexingEnabled, String hookUrl,
                                               String visibility) throws GitClientException {
        return createGitProject(template, description,
                                GitUtils.convertPipeNameToProject(name),
                                indexingEnabled, hookUrl, visibility);
    }

    public GitProject createEmptyRepository(final String name, final String description,
                                            final boolean indexingEnabled, final boolean initCommit,
                                            final String hookUrl,
                                            final String visibility) throws GitClientException {
        final GitProject project = createRepo(name, description, visibility);
        if (indexingEnabled) {
            addProjectHook(String.valueOf(project.getId()), hookUrl);
        }
        if (initCommit) {
            createFile(project, GITKEEP_FILE, "keep");
        }
        return project;
    }

    public boolean projectExists(final String namespace, final String name) throws GitClientException {
        try {
            String projectId = makeProjectId(namespace, GitUtils.convertPipeNameToProject(name));
            Response<GitProject> response = gitLabApi.getProject(apiVersion, projectId).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

    public boolean projectExists(final String name) throws GitClientException {
        return projectExists(namespace, name);
    }

    public GitProject getProject() throws GitClientException {
        return getProject(projectName);
    }

    public List<GitlabBranch> getBranches() {
        String project = GitUtils.convertPipeNameToProject(projectName);
        String projectId = makeProjectId(namespace, project);
        return execute(gitLabApi.getBranches(apiVersion, projectId));
    }

    public GitProject getProject(String name) throws GitClientException {
        String project = GitUtils.convertPipeNameToProject(name);
        String projectId = makeProjectId(namespace, project);
        return execute(gitLabApi.getProject(apiVersion, projectId));
    }

    public void deleteRepository() throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        execute(gitLabApi.deleteProject(apiVersion, projectId));
    }

    public GitTagEntry getRepositoryRevision(String tag) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getRevision(apiVersion, projectId, tag));
    }

    public GitCommitEntry getRepositoryCommit(String commitId) throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getCommit(apiVersion, projectId, commitId, null));
    }

    public List<GitTagEntry> getRepositoryRevisions(final String namespace, final String projectName)
            throws GitClientException {
        final String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.getRevisions(apiVersion, projectId, null, null));
    }

    public List<GitTagEntry> getRepositoryRevisions() throws GitClientException {
        return getRepositoryRevisions(namespace, projectName);
    }

    public GitTagEntry createRepositoryRevision(String name, String ref, String message, String releaseDescription)
            throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        return execute(gitLabApi.createRevision(apiVersion, projectId, name, ref, message, releaseDescription));
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
        return execute(gitLabApi.getCommits(apiVersion, projectId, refName, sinceDate, untilDate,
                null, null, null));
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
        return execute(gitLabApi.postCommit(apiVersion, projectId, commitEntry));
    }

    public byte[] getFileContents(String path, String revision) throws GitClientException {
        return getFileContents(null, path, revision);
    }

    public byte[] getFileContents(String projectId, String path, String revision) throws GitClientException {
        if (StringUtils.isBlank(projectId)) {
            projectId = makeProjectId(namespace, projectName);
        }
        try {
            GitFile gitFile = execute(gitLabApi.getFiles(apiVersion, projectId, encodePath(path), revision));
            return Base64.getDecoder().decode(gitFile.getContent());
        } catch (IOException e) {
            throw new GitClientException("Error receiving file content!", e);
        }
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
        final String currentProjectId = StringUtils.isBlank(projectId)
                                        ? makeProjectId(namespace, projectName)
                                        : projectId;
        try {
            return RestApiUtils.getFileContent(gitLabApi
                    .getFilesRawContent(apiVersion, currentProjectId, encodePath(path), revision), byteLimit);
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
        return execute(gitLabApi.updateProject(apiVersion,
                makeProjectId(namespace, GitUtils.convertPipeNameToProject(currentName)),
                GitProjectRequest.builder()
                        .name(normalizedNewName)
                        .path(normalizedNewName)
                        .build()));
    }

    public GitGroup createGroup(final String newGroupName) throws GitClientException {
        return execute(gitLabApi.createGroup(
                apiVersion,
                GitGroupRequest.builder()
                        .name(newGroupName)
                        .path(newGroupName)
                        .build()));
    }

    public GitGroup deleteGroup(final String groupName) throws GitClientException {
        return execute(gitLabApi.deleteGroup(apiVersion, groupName));
    }

    public GitProject forkProject(final String projectName, final String namespaceFrom, final String namespaceTo)
            throws GitClientException {
        return execute(gitLabApi.forkProject(apiVersion,
                makeProjectId(namespaceFrom, GitUtils.convertPipeNameToProject(projectName)), namespaceTo));
    }

    public GitlabIssue createOrUpdateIssue(final String project, final GitlabIssue issue) throws GitClientException {
        issue.setDescription(formatTextWithAttachments(project,
                issue.getAttachments(),
                issue.getDescription()));
        return issue.getId() == null ? execute(gitLabApi.createIssue(apiVersion, project, issue)) :
                execute(gitLabApi.updateIssue(apiVersion, project, issue.getId(), issue));
    }

    public Boolean deleteIssue(final String project, final Long issueId) throws GitClientException {
        return execute(gitLabApi.deleteIssue(apiVersion, project, issueId));
    }

    public GitlabUpload upload(final String project, final String path) throws GitClientException {
        final File file = new File(path);
        final MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file",
                file.getName(),
                RequestBody.create(MediaType.parse("*/*"), file)
        );
        return execute(gitLabApi.upload(apiVersion, project, filePart));
    }

    public List<GitlabIssue> getIssues(final String project, final String authorId, final List<String> labels)
            throws GitClientException {
        return execute(gitLabApi.getIssues(apiVersion, project, authorId, labels));
    }

    public GitlabIssue getIssue(final String project, final Long issueId) throws GitClientException {
        final GitlabIssue issue = execute(gitLabApi.getIssue(apiVersion, project, issueId));
        final Pair<String, List<String>> a = extractAttachments(issue.getDescription());
        issue.setDescription(a.getKey());
        issue.setAttachments(a.getValue());
        final List<GitlabIssueComment> comments = getIssueComments(project, issueId);
        issue.setComments(comments);
        return issue;
    }

    public List<GitlabIssueComment> getIssueComments(final String project,
                                                     final Long issueId) throws GitClientException {
        final List<GitlabIssueComment> comments = execute(gitLabApi.getIssueComments(apiVersion, project, issueId));
        comments.forEach(c -> {
            Pair<String, List<String>> a = extractAttachments(c.getBody());
            c.setBody(a.getKey());
            c.setAttachments(a.getValue());
        });
        return comments;
    }

    public GitlabIssueComment addIssueComment(final String project,
                                              final Long issueId,
                                              final GitlabIssueComment comment) throws GitClientException {
        comment.setBody(formatTextWithAttachments(project,
                comment.getAttachments(),
                comment.getBody()));
        return execute(gitLabApi.addIssueComment(apiVersion, project, issueId, comment));
    }

    public Optional<GitlabUser> findUser(final String userName) throws GitClientException {
        Optional<GitlabUser> gitlabUser = Optional.empty();
        for (String name : generateGitLabUsernames(userName)) {
            gitlabUser = Optional.of(execute(gitLabApi.searchUser(apiVersion, name)))
                    .map(List::stream)
                    .flatMap(Stream::findFirst);
            if (gitlabUser.isPresent()) {
                break;
            }
        }
        return gitlabUser;
    }

    public GitProjectStorage getProjectStorage(final String project) throws GitClientException {
        return execute(gitLabApi.getProjectStorage(makeProjectId(namespace, project)));
    }

    private GitProject createRepo(String repoName, String description, String visibility) throws GitClientException {
        GitProjectRequest gitProject = GitProjectRequest.builder()
                .name(repoName)
                .description(description)
                .visibility(visibility)
                .build();
        return execute(gitLabApi.createProject(apiVersion, gitProject));
    }

    public GitProject createRepo(final String description, final String visibility) throws GitClientException {
        return createRepo(projectName, description, visibility);
    }

    public void createFile(final GitProject project, final String path, final String content, final String branch) {
        try {
            final Response<GitFile> response = gitLabApi.createFiles(apiVersion,
                    project.getId().toString(), path, buildCreateFileRequest(content, branch)).execute();
            if (!response.isSuccessful()) {
                throw new HttpException(response);
            }
        } catch (IOException | GitClientException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    public void createFile(GitProject project, String path, String content) {
        createFile(project, path, content, null);
    }

    private UpdateGitFileRequest buildCreateFileRequest(final String content, final String branch)
            throws GitClientException {
        final UpdateGitFileRequest.UpdateGitFileRequestBuilder requestBuilder = UpdateGitFileRequest.builder()
                .branch(StringUtils.isBlank(branch) ? DEFAULT_BRANCH : branch)
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
                apiVersion,
                String.valueOf(userId),
                GitTokenRequest.builder()
                        .name(tokenName)
                        .expires(DATE_TIME_FORMATTER.format(expires))
                        .scopes(Collections.singletonList("api")).build(),
                adminToken)).getToken();
    }

    private List<String> generateGitLabUsernames(final String userName) {
        // try to split by @ if user name is an email it will crop it by @, if not - it will leave it as is
        final String trimmed = userName.split(EMAIL_SEPARATOR)[0];
        return Arrays.asList(trimmed.toUpperCase(), trimmed);
    }

    public GitRepositoryEntry addProjectHook(String projectId, String hookUrl) throws GitClientException {
        return execute(
                gitLabApi.addProjectHook(
                        apiVersion,
                        projectId,
                        GitHookRequest.builder().hookUrl(hookUrl)
                                .pushEvents(true).branch(DEFAULT_BRANCH)
                                .tagPushEvents(true).sslVerify(false).build()));
    }

    private GitProject createGitProject(Template template, String description, String repoName,
                                        boolean indexingEnabled, String hookUrl,
                                        String visibility) throws GitClientException {
        final GitProject project = createEmptyRepository(repoName, description, indexingEnabled,
                false, hookUrl, visibility);
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
        return new ApiBuilder<>(GitLabApi.class, gitHost, PRIVATE_TOKEN, adminToken, DATA_FORMAT).build();
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

    private String encodePath(final String path) throws UnsupportedEncodingException {
        return UriUtils.encodePathSegment(path, StandardCharsets.UTF_8.toString()).replace(DOT_CHAR,
                                                                                  DOT_CHAR_URL_ENCODING_REPLACEMENT);
    }
    private List<String> uploadAttachments(final String project, final List<String> attachments) {
        return attachments.stream()
                .map(a -> upload(project, a).getMarkdown().replace("!", ""))
                .collect(Collectors.toList());
    }

    private static Pair<String, List<String>> extractAttachments(String text) {
        final String[] description = text.split("!");
        final List<String> descriptionList = Arrays.stream(description).map(String::trim).collect(Collectors.toList());
        return new ImmutablePair<>(descriptionList.get(0), description.length > 1 ?
                descriptionList.subList(1, descriptionList.size()) : Collections.emptyList());
    }
    private String formatTextWithAttachments(final String project,
                                             final List<String> attachments,
                                             final String text) {
        final List<String> uploads = uploadAttachments(project, ListUtils.emptyIfNull(attachments));
        final List<String> body = new ArrayList<>();
        body.add(text);
        body.addAll(uploads);
        return String.join("\n\n!", body);
    }
}
