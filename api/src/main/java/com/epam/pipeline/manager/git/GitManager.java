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

import com.amazonaws.util.StringUtils;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.PipelineSourceItemRevertVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueCommentRequest;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueFilter;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueRequest;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitGroup;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitProjectStorage;
import com.epam.pipeline.entity.git.GitPushCommitActionEntry;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import com.epam.pipeline.entity.git.GitlabUser;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryIteratorListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderEntryListing;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogsPathFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderObject;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryCommit;
import com.epam.pipeline.entity.git.gitreader.GitReaderRepositoryLogEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.GitUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A service class, responsible for loading pipeline scripts from GitLab
 *
 * @author Mikhail Miroliubov
 */
@Service
public class GitManager {
    private static final String GIT_CLONE_CMD = "git clone %s %s";
    private static final String GIT_CHECKOUT_CMD = "git checkout %s";
    private static final String PATH_DELIMITER = "/";
    private static final String CONFIG_FILE_NAME = "config.json";
    public static final String DRAFT_PREFIX = "draft-";
    private static final String ACTION_UPDATE = "update";
    private static final String BASE64_ENCODING = "base64";
    public static final String EXCLUDE_MARK = ":!";

    private static final long DEFAULT_TOKEN_DURATION = 1L;
    private static final String EMPTY = "";
    private static final String COMMA = ",";
    private static final String ANY_SUB_PATH = "*";
    private static final String ROOT_PATH = "/";
    public static final String REVERT_MESSAGE = "Revert %s to commit %s";
    private static final String GIT_REPO_EXTENSION = ".git";
    private static final String ON_BEHALF_OF = "On behalf of %s";

    private CmdExecutor cmdExecutor = new CmdExecutor();

    @Autowired
    private PipelineManager pipelineManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private AuthManager authManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitManager.class);

    @Value("${working.directory}")
    private String workingDirPath;

    @Value("${templates.directory}")
    private String templatesDirectoryPath;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PipelineRepositoryService pipelineRepositoryService;

    @PostConstruct
    public void configure() {
        File baseDir = new File(workingDirPath);
        boolean result = baseDir.exists() || baseDir.mkdirs();
        Assert.isTrue(result, "Could not create directory");
    }

    public GitlabClient getGitlabClient(String gitHost, String gitToken, Long gitAdminId, String gitAdminName) {
        final String apiVersion = preferenceManager.getPreference(SystemPreferences.GITLAB_API_VERSION);
        return GitlabClient
                .initializeGitlabClientFromHostAndToken(gitHost, gitToken, authManager.getAuthorizedUser(),
                        gitAdminId, gitAdminName, apiVersion);
    }

    private GitlabClient getGitlabClientForPipeline(Pipeline pipeline) {
        return getGitlabClientForPipeline(pipeline, false);
    }

    private GitlabClient getGitlabClientForPipeline(Pipeline pipeline, boolean rootClient) {
        return getGitlabClientForRepository(pipeline.getRepository(), pipeline.getRepositoryToken(), rootClient);
    }

    private GitlabClient getGitlabClientForRepository(String repository, String providedToken,
                                                      final boolean rootClient) {
        Long adminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        String adminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        boolean externalHost = !StringUtils.isNullOrEmpty(providedToken);
        String token = externalHost ? providedToken :
                preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        final String apiVersion = preferenceManager.getPreference(SystemPreferences.GITLAB_API_VERSION);
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                rootClient ? adminName : authManager.getAuthorizedUser(),
                repository, token, adminId, adminName, externalHost, apiVersion);
    }

    public GitCredentials getGitCredentials(Long id) {
        return getGitCredentials(id, true, false);
    }

    public GitCredentials getGitCredentials(Long id, boolean useEnvVars) {
        return getGitCredentials(id, useEnvVars, false);
    }

    public GitCredentials getGitCredentials(Long id, boolean useEnvVars, boolean issueToken) {
        Pipeline pipeline = pipelineManager.load(id);
        try {
            return pipelineRepositoryService
                    .getPipelineCloneCredentials(pipeline, useEnvVars, issueToken, DEFAULT_TOKEN_DURATION);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public GitCredentials getGitlabCredentials(Long duration) {
        Long expiration = Optional.ofNullable(duration).orElse(DEFAULT_TOKEN_DURATION);
        try {
            return getDefaultGitlabClient()
                    .withFullUrl(preferenceManager.getPreference(SystemPreferences.GIT_EXTERNAL_URL))
                    .buildCloneCredentials(false, true, expiration);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Returns source file list of specified pipeline version
     *
     * @param id id of a {@link Pipeline}
     * @return source file list of specified pipeline version
     * @throws GitClientException if something goes wrong
     */
    public List<GitRepositoryEntry> getPipelineSources(Long id, String version, String path, boolean recursive)
            throws GitClientException {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);

        return pipelineRepositoryService.getRepositoryContents(pipeline, path, version, recursive);
    }

    public List<GitRepositoryEntry> getPipelineSources(Long id, String version) throws GitClientException {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        return pipelineRepositoryService
                .getRepositoryContents(pipeline, findRepoSrcPath(pipeline), version, false);
    }

    public List<GitRepositoryEntry> getPipelineSources(Long id,
                                                       String version,
                                                       String path,
                                                       boolean appendConfigurationFileIfNeeded,
                                                       boolean recursive)
            throws GitClientException {
        List<GitRepositoryEntry> entries;
        if (StringUtils.isNullOrEmpty(path)) {
            final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
            entries = pipelineRepositoryService.getRepositoryContents(
                    pipeline, findRepoSrcPath(pipeline), version, recursive);
            if (!RepositoryType.BITBUCKET.equals(pipeline.getRepositoryType()) && appendConfigurationFileIfNeeded) {
                final GitRepositoryEntry configurationEntry = getConfigurationFileEntry(id, version,
                        pipeline.getConfigurationPath());
                if (configurationEntry != null) {
                    entries.add(configurationEntry);
                }
            }
        } else {
            entries = getPipelineSources(id, version, path, recursive);
        }
        return entries;
    }

    private GitRepositoryEntry getConfigurationFileEntry(final Long id, final String version, final String configPath)
            throws GitClientException {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        final String config = getConfigFilePath(configPath);

        if (pipelineRepositoryService.fileExists(pipeline, config)) {
            return GitRepositoryEntry.builder()
                    .name(Paths.get(config).getFileName().toString())
                    .path(config)
                    .type(GitUtils.FILE_MARKER)
                    .build();
        }
        return null;
    }

    public String getRevisionName(String version) {
        return version.startsWith(DRAFT_PREFIX) ? version.substring(DRAFT_PREFIX.length()) : version;
    }

    public List<GitCommitEntry> getCommits(Pipeline pipeline, String versionName)
            throws GitClientException {
        return this.getGitlabClientForPipeline(pipeline)
                .getCommits(getRevisionName(versionName));
    }

    public List<GitCommitEntry> getCommits(Pipeline pipeline, String versionName, Date since)
            throws GitClientException {
        return this.getGitlabClientForPipeline(pipeline).getCommits(getRevisionName(versionName), since);
    }

    public GitCommitEntry createOrRenameFolder(Long id, PipelineSourceItemVO folderVO) throws GitClientException {
        final String folderPath = GitUtils.withoutLeadingDelimiter(folderVO.getPath());
        String folderName = FilenameUtils.getName(folderPath);
        Assert.isTrue(GitUtils.checkGitNaming(folderName),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_FOLDER_NAME, folderName));
        Pipeline pipeline = pipelineManager.load(id, true);
        if (folderVO.getPreviousPath() == null) {
            // Previous path is missing: creating folder
            return pipelineRepositoryService.createFolder(pipeline,
                    folderPath,
                    folderVO.getLastCommitId(),
                    folderVO.getComment());
        }
        // else: renaming folder
        return pipelineRepositoryService.renameFolder(pipeline,
                GitUtils.withoutLeadingDelimiter(folderVO.getPreviousPath()),
                folderPath,
                folderVO.getLastCommitId(),
                folderVO.getComment());
    }

    public GitCommitEntry removeFolder(final Pipeline pipeline,
                                       final String folder,
                                       final String lastCommitId,
                                       final String commitMessage) throws GitClientException {
        return pipelineRepositoryService.removeFolder(pipeline, GitUtils.withoutLeadingDelimiter(folder),
                lastCommitId, commitMessage, GitUtils.withoutLeadingDelimiter(pipeline.getCodePath()));
    }

    public GitCommitEntry modifyFile(Pipeline pipeline,
                                     PipelineSourceItemVO sourceItemVO) throws GitClientException {
        final String sourcePath = GitUtils.withoutLeadingDelimiter(sourceItemVO.getPath());
        Arrays.stream(sourcePath.split(PATH_DELIMITER)).forEach(pathPart ->
            Assert.isTrue(GitUtils.checkGitNaming(pathPart),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_FILE_NAME, sourcePath)));
        if (StringUtils.isNullOrEmpty(sourceItemVO.getPreviousPath())) {
            return pipelineRepositoryService.updateFile(pipeline,
                    sourcePath,
                    sourceItemVO.getContents(),
                    sourceItemVO.getLastCommitId(),
                    sourceItemVO.getComment(),
                    false);
        }
        return pipelineRepositoryService.renameFile(pipeline,
                sourcePath,
                GitUtils.withoutLeadingDelimiter(sourceItemVO.getPreviousPath()),
                sourceItemVO.getLastCommitId(),
                sourceItemVO.getComment());
    }

    public GitCommitEntry revertFile(final Pipeline pipeline,
                                     final PipelineSourceItemRevertVO sourceItemRevertVO) {
        Assert.hasLength(sourceItemRevertVO.getCommitToRevert(), "Commit to revert should be provided!");
        Assert.hasLength(sourceItemRevertVO.getPath(), "Path to file should be provided!");

        final String sourcePath = GitUtils.withoutLeadingDelimiter(sourceItemRevertVO.getPath());
        final byte[] content = getPipelineFileContents(
                pipeline,
                sourceItemRevertVO.getCommitToRevert(),
                sourcePath
        );

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(getRevertMessage(sourceItemRevertVO));

        final GitPushCommitActionEntry revertGitAction = new GitPushCommitActionEntry();
        revertGitAction.setFilePath(sourcePath);
        revertGitAction.setContent(Base64.getEncoder().encodeToString(content));
        revertGitAction.setEncoding(BASE64_ENCODING);
        revertGitAction.setAction(ACTION_UPDATE);
        gitPushCommitEntry.getActions().add(revertGitAction);

        return this.getGitlabClientForPipeline(pipeline).commit(gitPushCommitEntry);
    }

    public byte[] getPipelineFileContents(Pipeline pipeline, String version, String path)
            throws GitClientException {
        return this.getGitlabClientForPipeline(pipeline)
                .getFileContents(path, getRevisionName(version));
    }

    /**
     * Returns docs file list of specified pipeline version
     *
     * @param id of a {@link Pipeline}
     * @return docs file list of specified pipeline version
     * @throws GitClientException if something goes wrong
     */
    public List<GitRepositoryEntry> getPipelineDocs(Long id, String version) throws GitClientException {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        final String docsPath = pipeline.getDocsPath();
        Assert.notNull(docsPath, messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_DOCS_NOT_FOUND, id));
        return pipelineRepositoryService.getRepositoryContents(pipeline, docsPath, version, false);
    }

    public File getConfigFile(Pipeline pipeline, String version) {
        checkRevision(pipeline, version);
        try {
            Path path = Files.createTempDirectory(getBaseDir().toPath(), "git");
            return checkoutConfigToDirectory(pipeline, getRevisionName(version),
                    path.toFile().getAbsolutePath() + PATH_DELIMITER);
        } catch (IOException | GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new CmdExecutionException(e.getMessage(), e);
        }
    }

    public String getConfigFileContent(Pipeline pipeline, String version)
            throws GitClientException {
        checkRevision(pipeline, version);
        final String configPath = getConfigFilePath(pipeline.getConfigurationPath());
        byte[] configBytes = pipelineRepositoryService.getFileContents(pipeline, getRevisionName(version), configPath);
        String config = new String(configBytes, Charset.defaultCharset());
        Assert.notNull(config, "Config.json is empty.");
        return config;
    }

    public GitProject createRepository(String templateId, String pipelineName, String description)
            throws GitClientException {
        TemplatesScanner templatesScanner = new TemplatesScanner(templatesDirectoryPath);
        Template template = templatesScanner.listTemplates().get(templateId);
        Assert.notNull(template, "There is no such a template: " + templateId);
        return getDefaultGitlabClient()
                .createTemplateRepository(template,
                        pipelineName,
                        description,
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED),
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL),
                        preferenceManager.getPreference(SystemPreferences.GITLAB_PROJECT_VISIBILITY));
    }

    public GitProject createRepository(final String pipelineName, final String description) throws GitClientException {
        return getDefaultGitlabClient().createEmptyRepository(
                        GitUtils.convertPipeNameToProject(pipelineName),
                        description,
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED),
                        true,
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL),
                        preferenceManager.getPreference(SystemPreferences.GITLAB_PROJECT_VISIBILITY)
                );
    }

    public boolean checkProjectExists(String name) {
        try {
            return getDefaultGitlabClient().projectExists(name);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitlabClient getDefaultGitlabClient() {
        final String gitHost = preferenceManager.getPreference(SystemPreferences.GIT_HOST);
        final Long gitAdminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        final String gitAdminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        final String apiVersion = preferenceManager.getPreference(SystemPreferences.GITLAB_API_VERSION);
        final String gitToken = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        return GitlabClient.initializeGitlabClientFromHostAndToken(gitHost, gitToken,
                authManager.getAuthorizedUser(), gitAdminId, gitAdminName, apiVersion);
    }

    public GitProject createEmptyRepository(final String pipelineName,
                                            final String description) throws GitClientException {
        return getDefaultGitlabClient().createEmptyRepository(
                GitUtils.convertPipeNameToProject(pipelineName),
                description,
                preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED),
                false,
                preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL),
                preferenceManager.getPreference(SystemPreferences.GITLAB_PROJECT_VISIBILITY)
        );
    }

    private void checkRevision(Pipeline pipeline, String version) {
        try {
            pipelineRepositoryService.loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private File checkoutConfigToDirectory(Pipeline pipeline, String version, String repoPath)
            throws GitClientException {
        checkoutRepo(pipeline, version, repoPath);
        final String configPath = getConfigFilePath(pipeline.getConfigurationPath());
        return new File(repoPath, configPath);
    }

    private void checkoutRepo(Pipeline pipeline, String version, String repoPath)
            throws GitClientException {
        GitCredentials gitCredentials = getGitCredentials(pipeline.getId(), false);
        final String clone = String.format(GIT_CLONE_CMD, gitCredentials.getUrl(), repoPath);
        cmdExecutor.executeCommand(clone, null, new File(workingDirPath), true);
        final String checkout = String.format(GIT_CHECKOUT_CMD, getRevisionName(version));
        cmdExecutor.executeCommand(checkout, null, new File(repoPath), true);
    }

    private File getBaseDir() {
        return new File(workingDirPath);
    }

    public GitTagEntry loadRevision(Pipeline pipeline, String version) throws GitClientException {
        Assert.notNull(version, "Revision is required.");
        if (version.startsWith(DRAFT_PREFIX)) {
            GitCommitEntry repositoryCommit =
                    this.getGitlabClientForPipeline(pipeline).getRepositoryCommit(getRevisionName(version));
            if (repositoryCommit == null) {
                throw new IllegalArgumentException(String.format("Commit %s not found.", version));
            }
            return new GitTagEntry(repositoryCommit);
        } else {
            GitTagEntry revision = this.getGitlabClientForPipeline(pipeline).getRepositoryRevision(version);
            if (revision == null) {
                throw new IllegalArgumentException(
                        String.format("Revision %s not found.", version));
            }
            return revision;
        }
    }

    /**
     * Returns all repository contents - a list of files in a repository
     * @return a list of {@link GitRepositoryEntry}, representing files and folders
     * @throws GitClientException
     */
    public List<GitRepositoryEntry> getRepositoryContents(Long id, String version, String path)
            throws GitClientException {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);

        return pipelineRepositoryService.getRepositoryContents(pipeline, path, version, true);
    }

    public GitRepositoryEntry addHookToPipelineRepository(Long id) throws GitClientException {
        if (!preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED)) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_INDEXING_DISABLED));
        }
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipelineManager.load(id));
        return gitlabClient.createProjectHook(
                preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL));
    }

    public GitProject copyRepository(final String projectName, final String newProjectName, final String uuid) {
        final String tmpGroupName = String.format("TMP_FORK_%s", uuid);
        final String defaultNamespace = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);

        createGitGroup(tmpGroupName);
        LOGGER.debug("The temporary git group '{}' was created for fork operation", tmpGroupName);

        final GitProject forkedProject = copyProject(projectName, newProjectName, tmpGroupName, defaultNamespace);

        deleteGitGroup(tmpGroupName);
        LOGGER.debug("The temporary git group '{}' was deleted", tmpGroupName);

        return forkedProject;
    }

    public GitReaderEntryListing<GitReaderObject> lsTreeRepositoryContent(final Long id, final String version,
                                                                          final String path, final Long page,
                                                                          final Integer pageSize) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryTree(
                getGitlabRepositoryPath(pipeline),
                new GitReaderLogsPathFilter(getContextPathList(path)), version, page, pageSize
        ));
    }

    public GitReaderObject lsTreeRepositoryObject(final Long id, final String version, final String path) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        final GitReaderEntryListing<GitReaderObject> listing =
                callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryTree(
                        getGitlabRepositoryPath(pipeline),
                        new GitReaderLogsPathFilter(getContextPathList(path)),
                        version, 0L, 1
                ));
        if (CollectionUtils.isNotEmpty(listing.getListing())) {
            return listing.getListing().get(0);
        }
        throw new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_PATH_DOESNT_EXIST, path)
        );
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> logsTreeRepositoryContent(final Long id,
                                                                                        final String version,
                                                                                        final String path,
                                                                                        final Long page,
                                                                                        final Integer pageSize) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryTreeLogs(
                getGitlabRepositoryPath(pipeline),
                new GitReaderLogsPathFilter(getContextPathList(path)),
                version, page, pageSize
        ));
    }

    public GitReaderEntryListing<GitReaderRepositoryLogEntry> logsTreeRepositoryContent(
        final Long id,
        final String version,
        final GitReaderLogsPathFilter paths) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, version);
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryTreeLogs(
                getGitlabRepositoryPath(pipeline), version, paths
        ));
    }

    public GitReaderEntryIteratorListing<GitReaderRepositoryCommit> logRepositoryCommits(
        final Long id,
        final Long page,
        final Integer pageSize,
        final GitCommitsFilter filter) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, filter.getRef());
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryCommits(
                getGitlabRepositoryPath(pipeline), page, pageSize,
                buildFiltersWithUsersFromGitLab(filter), getFilesToIgnore()
        ));
    }

    public GitReaderDiff logRepositoryCommitDiffs(final Long id, final Boolean includeDiff,
                                                  final GitCommitsFilter filter) {
        final Pipeline pipeline = loadPipelineAndCheckRevision(id, filter.getRef());
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryCommitDiffs(
                getGitlabRepositoryPath(pipeline),
                includeDiff, buildFiltersWithUsersFromGitLab(filter), getFilesToIgnore()
        ));
    }

    public GitReaderDiffEntry getRepositoryCommitDiff(final Long id, final String commit,
                                                      final String path) {
        final Pipeline pipeline = pipelineManager.load(id);
        return callGitReaderApi(gitReaderClient -> gitReaderClient.getRepositoryCommitDiff(
                getGitlabRepositoryPath(pipeline),
                commit,
                new GitReaderLogsPathFilter(
                        ListUtils.union(getContextPathList(path), getFilesToIgnore())
                )
        ));
    }

    public GitlabIssue createIssue(final GitlabIssueRequest request) throws GitClientException {
        final String authorizedUser = authManager.getCurrentUser().getUserName();
        final GitlabIssue issue = request.toIssue();
        final List<String> labels = Optional.ofNullable(issue.getLabels()).orElse(new ArrayList<>());
        labels.add(String.format(ON_BEHALF_OF, authorizedUser));
        final List<String> defaultLabels = preferenceManager.getPreference(SystemPreferences.GITLAB_DEFAULT_LABELS);
        if (CollectionUtils.isNotEmpty(defaultLabels)) {
            labels.addAll(defaultLabels);
        }
        issue.setLabels(labels);
        return getDefaultGitlabClient().createIssue(getProjectForIssues(), issue, request.getAttachments());
    }

    public GitlabIssue updateIssue(final GitlabIssueRequest request) throws GitClientException {
        return getDefaultGitlabClient().updateIssue(getProjectForIssues(), request.toIssue(), request.getAttachments());
    }

    public Boolean deleteIssue(final Long issueId) throws GitClientException {
        return getDefaultGitlabClient().deleteIssue(getProjectForIssues(), issueId);
    }

    public PagedResult<List<GitlabIssue>> getIssues(final Integer page,
                                                    final Integer pageSize,
                                                    final GitlabIssueFilter filter) throws GitClientException {
        final List<String> labels = Optional.ofNullable(filter.getLabels()).orElse(new ArrayList<>());
        if (!authManager.isAdmin()) {
            final String authorizedUser = authManager.getCurrentUser().getUserName();
            labels.add(String.format(ON_BEHALF_OF, authorizedUser));
        }
        return getDefaultGitlabClient().getIssues(getProjectForIssues(), labels, page, pageSize, filter.getSearch());
    }

    public GitlabIssue getIssue(final Long issueId) throws GitClientException {
        return getDefaultGitlabClient().getIssue(getProjectForIssues(), issueId);
    }

    public GitlabIssueComment addIssueComment(final Long issueId,
                                              final GitlabIssueCommentRequest comment) throws GitClientException {
        final String authorizedUser = authManager.getCurrentUser().getUserName();
        comment.setBody(String.format("On behalf of %s \n%s", authorizedUser, comment.getBody()));
        return getDefaultGitlabClient().addIssueComment(getProjectForIssues(), issueId, comment);
    }

    private String getProjectForIssues() {
        final String project = preferenceManager.getPreference(SystemPreferences.GITLAB_ISSUE_PROJECT);
        Assert.isTrue(!StringUtils.isNullOrEmpty(project),
                messageHelper.getMessage(MessageConstants.ERROR_ISSUE_PROJECT_NOT_SET));
        return project;
    }

    private List<String> getContextPathList(String path) {
        return Objects.nonNull(path) ? Collections.singletonList(path) : Collections.emptyList();
    }

    private List<String> getFilesToIgnore() {
        return Arrays.stream(Optional.ofNullable(
                        preferenceManager.getPreference(SystemPreferences.VERSION_STORAGE_IGNORED_FILES)
                ).orElse(EMPTY).split(COMMA))
                .filter(p -> !StringUtils.isNullOrEmpty(p))
                .map(String::trim)
                .map(p -> {
                    if (!p.startsWith(ROOT_PATH)) {
                        return EXCLUDE_MARK + ANY_SUB_PATH + p;
                    } else {
                        return EXCLUDE_MARK + p;
                    }
                }).collect(Collectors.toList());
    }

    private GitCommitsFilter buildFiltersWithUsersFromGitLab(GitCommitsFilter filter) {
        return filter.toBuilder().authors(
                CollectionUtils.emptyIfNull(filter.getAuthors()).stream().map(user -> {
                    try {
                        return getDefaultGitlabClient().findUser(user)
                                .map(GitlabUser::getUsername).orElse(user);
                    } catch (GitClientException e) {
                        LOGGER.warn(e.getMessage());
                        return user;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList())
        ).build();
    }

    private <T> T callGitReaderApi(final GitClientMethodCall<GitReaderClient, T> method) {
        try {
            return method.apply(
                    new GitReaderClient(
                            getGitReaderHostPreference(),
                            authManager.issueAdminToken(null)
                    )
            );
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage());
            throw new IllegalArgumentException(
                    "Something went wrong when trying to request data from Git Reader service", e);
        }
    }

    private Pipeline loadPipelineAndCheckRevision(final Long id, final String revision) {
        final Pipeline pipeline = pipelineManager.load(id);
        if (!StringUtils.isNullOrEmpty(revision)) {
            checkRevision(pipeline, revision);
        }
        return pipeline;
    }

    private String getGitReaderHostPreference() {
        final String gitReaderHost = preferenceManager.getPreference(SystemPreferences.GIT_READER_HOST);
        Assert.hasText(gitReaderHost, "Preference git.reader.service.host is empty");
        return gitReaderHost;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private GitProject copyProject(final String projectName, final String newProjectName, final String tmpGroupName,
                                   final String defaultNamespace) {
        try {
            final GitlabClient gitlabClient = getDefaultGitlabClient();
            forkProject(gitlabClient, projectName, defaultNamespace, tmpGroupName);
            LOGGER.debug("The project '{}' was forked to temporary namespace '{}'", projectName, tmpGroupName);

            gitlabClient.updateProjectName(projectName, newProjectName, tmpGroupName);

            final GitProject resultProject = forkProject(gitlabClient, newProjectName, tmpGroupName, defaultNamespace);
            LOGGER.debug("The project '{}' was forked to default namespace '{}'", newProjectName, defaultNamespace);
            return resultProject;
        } catch (Exception e) {
            deleteGitGroup(tmpGroupName);
            LOGGER.debug("The temporary git group '{}' was deleted due to unexpected error", tmpGroupName);
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitGroup createGitGroup(final String groupName) {
        try {
            return getDefaultGitlabClient().createGroup(groupName);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitGroup deleteGitGroup(final String groupName) {
        try {
            return getDefaultGitlabClient().deleteGroup(groupName);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitProject forkProject(final GitlabClient gitlabClient,
                                   final String projectName,
                                   final String sourceNamespace,
                                   final String destinationNamespace) throws GitClientException {
        final Integer timeout = preferenceManager.getPreference(SystemPreferences.GIT_FORK_WAIT_TIMEOUT);
        final Integer retryCount = preferenceManager.getPreference(SystemPreferences.GIT_FORK_RETRY_COUNT);

        final List<GitTagEntry> sourceTags = gitlabClient.getRepositoryRevisions(sourceNamespace, projectName);
        final GitProject resultProject = gitlabClient.forkProject(projectName, sourceNamespace, destinationNamespace);
        for (int i = 0; i < retryCount; i++) {
            if (forkedProjectInitialized(gitlabClient, projectName, sourceNamespace, destinationNamespace,
                    sourceTags)) {
                return resultProject;
            }
            waitTimeout(timeout);
        }
        throw new IllegalArgumentException("Cannot fork project correctly.");
    }

    private boolean forkedProjectInitialized(final GitlabClient gitlabClient,
                                             final String projectName,
                                             final String sourceNamespace,
                                             final String destinationNamespace,
                                             final List<GitTagEntry> sourceTags) {
        try {
            if (!gitlabClient.projectExists(destinationNamespace, projectName)) {
                LOGGER.debug("The project '{}' was forked but still not exists", projectName);
                return false;
            }
            final List<GitTagEntry> destinationTags = gitlabClient
                    .getRepositoryRevisions(destinationNamespace, projectName);
            if (sourceTags.size() != destinationTags.size()) {
                LOGGER.debug("The projects tag sizes are different (source namespace - '{}', " +
                        "destination namespace - '{}')", sourceNamespace, destinationNamespace);
                return false;
            }
            return true;
        } catch (GitClientException e) {
            LOGGER.debug("An error occurred during fork project initialization. ", e);
            return false;
        }
    }

    private String getRevertMessage(final PipelineSourceItemRevertVO sourceItemRevertVO) {
        return Optional.ofNullable(sourceItemRevertVO.getComment()).orElse(
                String.format(REVERT_MESSAGE, sourceItemRevertVO.getPath(), sourceItemRevertVO.getCommitToRevert()));
    }

    private void waitTimeout(final Integer waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String getConfigFilePath(final String configPath) {
        return StringUtils.isNullOrEmpty(configPath) ? CONFIG_FILE_NAME : configPath;
    }

    private String getGitlabRepositoryPath(final Pipeline pipeline) {
        final boolean hashedRepositoriesSupported = preferenceManager.getPreference(
                SystemPreferences.GITLAB_HASHED_REPO_SUPPORT);
        final GitRepositoryUrl gitRepositoryUrl = GitRepositoryUrl.from(pipeline.getRepository());
        final String namespace = gitRepositoryUrl.getNamespace()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        final String project = gitRepositoryUrl.getProject()
                .orElseThrow(() -> new IllegalArgumentException("Invalid repository URL format"));
        if (hashedRepositoriesSupported) {
            final GitProjectStorage projectStorage = getDefaultGitlabClient().getProjectStorage(project);
            if (Objects.nonNull(projectStorage)) {
                return projectStorage.getDiskPath() + GIT_REPO_EXTENSION;
            }
        }
        return Paths.get(namespace, project + GIT_REPO_EXTENSION).toString();
    }

    private String findRepoSrcPath(final Pipeline pipeline) {
        Assert.notNull(pipeline.getCodePath(),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_SRC_NOT_FOUND, pipeline.getId()));
        return pipeline.getCodePath();
    }
}
