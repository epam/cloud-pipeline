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

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueCommentRequest;
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
import com.epam.pipeline.entity.git.GitReleaseEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitToken;
import com.epam.pipeline.entity.git.GitTokenRequest;
import com.epam.pipeline.entity.git.GitlabBranch;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueAttachment;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import com.epam.pipeline.entity.git.GitlabIssueRequest;
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
import lombok.With;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.git.RestApiUtils.execute;
import static com.epam.pipeline.manager.git.RestApiUtils.getResponse;

@With
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
    public static final String TOTAL_HEADER = "X-Total";
    public static final int MAX_PAGE_SIZE = 100;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.");
    private static final int NAMESPACE_PATH_MAJOR_VERSION = 13;
    private static final int RELEASES_API_MAJOR_VERSION = 14;
    private static final int DELAYED_DELETION_MAJOR_VERSION = 18;
    private static final long MAX_TOKEN_LIFETIME_DAYS = 365;

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
        try {
            return execute(gitLabApi.getRepositoryTree(projectId, path, revision, recursive));
        } catch (UnexpectedResponseStatusException e) {
            // Gitlab 17.7 and later answers 404 for a path that does not exist, where older versions return
            // an empty list. The root tree tells a missing path from a missing project or revision.
            if (e.getStatus() == HttpStatus.NOT_FOUND && StringUtils.isNotBlank(path)
                    && rootTreeExists(projectId, revision)) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    private boolean rootTreeExists(final String projectId, final String revision) throws GitClientException {
        try {
            execute(gitLabApi.getRepositoryTree(projectId, null, revision, false));
            return true;
        } catch (UnexpectedResponseStatusException e) {
            return false;
        }
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

    public GitProject getProject(Long id) throws GitClientException {
        return execute(gitLabApi.getProject(apiVersion, String.valueOf(id)));
    }

    /**
     * Deletes the project. Gitlab 18.0 and later only marks a project for deletion and renames it, while the old
     * path still redirects to it and keeps the name taken. There the marked project is then removed permanently,
     * under the path it got at the marking.
     */
    public void deleteRepository() throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        if (getMajorVersion() < DELAYED_DELETION_MAJOR_VERSION) {
            execute(gitLabApi.deleteProject(apiVersion, projectId));
            return;
        }
        final String id = String.valueOf(execute(gitLabApi.getProject(apiVersion, projectId)).getId());
        execute(gitLabApi.deleteProject(apiVersion, id));
        final Optional<GitProject> markedProject = findProject(id);
        if (!markedProject.isPresent()) {
            return;
        }
        execute(gitLabApi.removeProjectPermanently(apiVersion, id, true, markedProject.get().getPath()));
    }

    private Optional<GitProject> findProject(final String id) throws GitClientException {
        try {
            return Optional.ofNullable(execute(gitLabApi.getProject(apiVersion, id)));
        } catch (UnexpectedResponseStatusException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
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

    /**
     * Creates a tag. A non-blank release description is saved as the release of the tag: with the Releases API
     * on Gitlab 14.0 and later, which ignores the release_description parameter of the tag creation.
     * A failure to create the release is logged, and the created tag is returned without it.
     */
    public GitTagEntry createRepositoryRevision(String name, String ref, String message, String releaseDescription)
            throws GitClientException {
        String projectId = makeProjectId(namespace, projectName);
        if (StringUtils.isBlank(releaseDescription) || getMajorVersion() < RELEASES_API_MAJOR_VERSION) {
            return execute(gitLabApi.createRevision(apiVersion, projectId, name, ref, message, releaseDescription));
        }
        final GitTagEntry tag = execute(gitLabApi.createRevision(apiVersion, projectId, name, ref, message, null));
        final GitReleaseEntry release = new GitReleaseEntry();
        release.setTagName(name);
        release.setDescription(releaseDescription);
        try {
            final GitReleaseEntry createdRelease = execute(gitLabApi.createRelease(apiVersion, projectId, release));
            if (tag != null) {
                tag.setRelease(createdRelease);
            }
        } catch (GitClientException e) {
            LOGGER.warn("The tag {} of the project {} is created, but its release description is not saved: {}",
                    name, projectId, e.getMessage());
        }
        return tag;
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

    /**
     * Loads the major number of the Gitlab version.
     * @return the major version, or -1 if the version cannot be parsed
     */
    private int getMajorVersion() throws GitClientException {
        final String version = Optional.ofNullable(getVersion()).map(GitlabVersion::getVersion).orElse(null);
        final Matcher matcher = VERSION_PATTERN.matcher(StringUtils.defaultString(version));
        if (!matcher.find()) {
            LOGGER.warn("Cannot parse the Gitlab version '{}'.", version);
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
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

    /**
     * Deletes a group. GitLab 18.0 and later only schedules a top-level group for deletion, and renames it.
     * The group and its projects are removed after the deletion delay, 30 days by default.
     */
    public GitGroup deleteGroup(final String groupName) throws GitClientException {
        return execute(gitLabApi.deleteGroup(apiVersion, groupName));
    }

    /**
     * Forks a project. The namespace is passed as namespace_path on Gitlab 13.0 and later: the namespace parameter
     * of older versions is deprecated there. Gitlab 12.10 already has namespace_path: 13.0 is on the safe side.
     */
    public GitProject forkProject(final String projectName, final String namespaceFrom, final String namespaceTo)
            throws GitClientException {
        final String projectId = makeProjectId(namespaceFrom, GitUtils.convertPipeNameToProject(projectName));
        if (getMajorVersion() < NAMESPACE_PATH_MAJOR_VERSION) {
            return execute(gitLabApi.forkProject(apiVersion, projectId, namespaceTo, null));
        }
        return execute(gitLabApi.forkProject(apiVersion, projectId, null, namespaceTo));
    }

    public GitlabIssue createIssue(final String project,
                                   final GitlabIssueRequest issue,
                                   final List<GitlabIssueAttachment> attachments) throws GitClientException {
        issue.setDescription(formatTextWithAttachments(project,
                attachments,
                issue.getDescription()));
        return execute(gitLabApi.createIssue(apiVersion, project, issue));
    }

    public GitlabIssue updateIssue(final String project,
                                   final GitlabIssueRequest issue,
                                   final List<GitlabIssueAttachment> attachments) throws GitClientException {
        issue.setDescription(formatTextWithAttachments(project,
                attachments,
                issue.getDescription()));
        return issue.getId() == null ? execute(gitLabApi.createIssue(apiVersion, project, issue)) :
                execute(gitLabApi.updateIssue(apiVersion, project, issue.getId(), issue));
    }

    public Boolean deleteIssue(final String project, final Long issueId) throws GitClientException {
        return execute(gitLabApi.deleteIssue(apiVersion, project, issueId));
    }

    public GitlabUpload upload(final String project, final String name, final String content)
            throws GitClientException {
        final MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", name,
                RequestBody.create(MediaType.parse("multipart/form-data"), Base64.getDecoder().decode(content)));
        return execute(gitLabApi.upload(apiVersion, project, filePart));
    }

    public PagedResult<List<GitlabIssue>> getIssues(final String project, final List<String> labels,
                                                    final List<String> notLabels, final Integer page,
                                                    final Integer pageSize, final String search,
                                                    final boolean serverFiltering)
            throws GitClientException {
        final String labelStr = CollectionUtils.isEmpty(labels) ? null : String.join(",", labels);
        final String notLabelStr = CollectionUtils.isEmpty(notLabels) ? null : String.join(",", notLabels);
        if (StringUtils.isNotBlank(notLabelStr) && serverFiltering) {
            return filterIssues(project, notLabels, page, pageSize, search, labelStr);
        }
        final Response<List<GitlabIssue>> response = getResponse(gitLabApi.getIssues(apiVersion, project, labelStr,
                notLabelStr, page, pageSize, search));
        final int totalPages = Integer.parseInt(Objects.requireNonNull(response.headers().get(TOTAL_HEADER)));
        return new PagedResult<>(response.body(), totalPages);
    }

    public List<GitlabIssue> getAllIssues(final String project, final String labels,
                                          final String search) {
        int page = 1;
        final List<GitlabIssue> result = new ArrayList<>();
        final Response<List<GitlabIssue>> response =
                fetchIssuePage(project, labels, search, page, MAX_PAGE_SIZE, result);
        final int totalPages = Integer.parseInt(Objects.requireNonNull(response.headers().get(TOTAL_HEADER)));
        while (totalPages >  result.size()) {
            page++;
            fetchIssuePage(project, labels, search, page, MAX_PAGE_SIZE, result);
        }
        return result;
    }

    public GitlabIssue getIssue(final String project, final Long issueId) throws GitClientException {
        final GitlabIssue issue = execute(gitLabApi.getIssue(apiVersion, project, issueId));
        final Pair<String, List<GitlabIssueAttachment>> a = extractAttachments(issue.getDescription());
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
            Pair<String, List<GitlabIssueAttachment>> a = extractAttachments(c.getBody());
            c.setBody(a.getKey());
            c.setAttachments(a.getValue());
        });
        return comments;
    }

    public GitlabIssueComment addIssueComment(final String project,
                                              final Long issueId,
                                              final GitlabIssueCommentRequest comment) throws GitClientException {
        comment.setBody(formatTextWithAttachments(project,
                comment.getAttachments(),
                comment.getBody()));
        return execute(gitLabApi.addIssueComment(apiVersion, project, issueId, comment.toComment()));
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

    public GitProjectStorage getProjectStorageFullPath(final String project) throws GitClientException {
        return execute(gitLabApi.getProjectStorage(project));
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
        // Recent Gitlab versions reject a token, that expires in more than 365 days. Gitlab counts the days
        // from its own date, which is UTC by default.
        final LocalDate maxEndDay = LocalDate.now(ZoneOffset.UTC).plusDays(MAX_TOKEN_LIFETIME_DAYS);
        return createImpersonationToken(tokenName, userId, endDay.isAfter(maxEndDay) ? maxEndDay : endDay);
    }

    private String createImpersonationToken(String tokenName, Long userId, LocalDate expires)
            throws GitClientException {
        return issueImpersonationToken(tokenName, userId, expires, Collections.singletonList("api")).getToken();
    }

    /**
     * Issues an impersonation token for a user.
     * @param tokenName a name of the new token
     * @param userId an ID of the Gitlab user
     * @param expires a date when the token expires
     * @param scopes scopes of the new token
     * @return the issued token, including its value and ID
     * @throws GitClientException if the request fails
     */
    public GitToken issueImpersonationToken(final String tokenName, final Long userId, final LocalDate expires,
                                            final List<String> scopes) throws GitClientException {
        if (adminId == null) {
            throw new IllegalArgumentException("Token may be issued only for local Gitlab.");
        }
        return execute(gitLabApi.issueToken(
                apiVersion,
                String.valueOf(userId),
                GitTokenRequest.builder()
                        .name(tokenName)
                        .expires(DATE_TIME_FORMATTER.format(expires))
                        .scopes(scopes).build(),
                adminToken));
    }

    /**
     * Loads the token, that this client authenticates with.
     * @return the token info without its value
     * @throws GitClientException if the request fails, e.g. on Gitlab older than 15.5
     */
    public GitToken getCurrentToken() throws GitClientException {
        return execute(gitLabApi.getCurrentToken());
    }

    /**
     * Revokes an impersonation token of a user.
     * @param userId an ID of the Gitlab user
     * @param tokenId an ID of the token
     * @throws GitClientException if the request fails
     */
    public void revokeImpersonationToken(final Long userId, final Long tokenId) throws GitClientException {
        execute(gitLabApi.revokeImpersonationToken(apiVersion, String.valueOf(userId), String.valueOf(tokenId)));
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
    private List<String> uploadAttachments(final String project, final Map<String, String> attachments) {
        return attachments.entrySet()
                .stream()
                .map(a -> upload(project, a.getKey(), a.getValue())
                        .getMarkdown().replace("!", ""))
                .collect(Collectors.toList());
    }

    private static Pair<String, List<GitlabIssueAttachment>> extractAttachments(String text) {
        final String[] description = text.split("!");
        final List<String> descriptionList = Arrays.stream(description).map(String::trim).collect(Collectors.toList());
        final List<String> attachments = description.length > 1 ?
                descriptionList.subList(1, descriptionList.size()) : Collections.emptyList();
        return new ImmutablePair<>(descriptionList.get(0),  attachments.stream()
                .map(GitlabClient::parseAttachment)
                .collect(Collectors.toList()));
    }

    private static GitlabIssueAttachment parseAttachment(final String markdown) {
        final String[] parts = markdown.split(Pattern.quote("](/"));
        final String fileName = parts[0].replace("[", "");
        final String secret = parts[1].replace("uploads/", "").replace(")", "");
        return GitlabIssueAttachment.builder()
                .fileName(fileName)
                .secret(secret)
                .markdown(markdown)
                .build();
    }

    private String formatTextWithAttachments(final String project,
                                             final List<GitlabIssueAttachment> attachments,
                                             final String text) {
        if (CollectionUtils.isEmpty(attachments)) {
            return text;
        }
        final List<String> uploaded = attachments.stream()
                .filter(a -> TextUtils.isBlank(a.getContent())).map(GitlabIssueAttachment::getMarkdown)
                .collect(Collectors.toList());
        final Map<String, String> toUpload = attachments.stream()
                .filter(a -> !TextUtils.isBlank(a.getContent()))
                .collect(Collectors.toMap(GitlabIssueAttachment::getFileName, GitlabIssueAttachment::getContent));
        final List<String> uploads = uploadAttachments(project, toUpload);
        final List<String> body = new ArrayList<>();
        body.add(text);
        body.addAll(uploaded);
        body.addAll(uploads);
        return String.join("\n\n!", body);
    }

    private PagedResult<List<GitlabIssue>> filterIssues(final String project,
                                                        final List<String> notLabels,
                                                        final Integer page,
                                                        final Integer pageSize,
                                                        final String search,
                                                        final String labelStr) {
        final List<GitlabIssue> allIssues = getAllIssues(project, labelStr, search);
        final List<GitlabIssue> filteredIssues = ListUtils.emptyIfNull(allIssues)
                .stream()
                .filter(issue -> ListUtils.emptyIfNull(issue.getLabels())
                        .stream()
                        .noneMatch(notLabels::contains))
                .collect(Collectors.toList());
        final int pageStart = pageSize * (page - 1);
        final int size = filteredIssues.size();
        if (pageStart >= size) {
            return new PagedResult<>(Collections.emptyList(), size);
        }
        return new PagedResult<>(filteredIssues.subList(pageStart, Math.min(size, pageStart + pageSize)), size);
    }

    private Response<List<GitlabIssue>> fetchIssuePage(final String project,
                                                       final String labels,
                                                       final String search,
                                                       final int page,
                                                       final int pageSize,
                                                       final List<GitlabIssue> result) {
        final Response<List<GitlabIssue>> response = getResponse(gitLabApi.getIssues(apiVersion, project, labels,
                null, page, pageSize, search));
        if (Objects.nonNull(response.body())) {
            result.addAll(response.body());
        }
        return response;
    }
}
