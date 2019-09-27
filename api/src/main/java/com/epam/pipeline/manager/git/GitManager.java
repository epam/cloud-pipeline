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

import com.amazonaws.util.StringUtils;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitPushCommitActionEntry;
import com.epam.pipeline.entity.git.GitPushCommitEntry;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.GitUtils;
import org.apache.commons.collections4.CollectionUtils;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
    private static final String GIT_FOLDER_TOKEN_FILE = ".gitkeep";
    public static final String GIT_MASTER_REPOSITORY = "master";
    public static final String DRAFT_PREFIX = "draft-";
    private static final String ACTION_MOVE = "move";
    private static final String BASE64_ENCODING = "base64";

    private static final long DEFAULT_TOKEN_DURATION = 1L;

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

    @Value("${git.src.directory}")
    private String srcDirectory;

    @Value("${git.docs.directory}")
    private String docsDirectory;

    @Value("${templates.directory}")
    private String templatesDirectoryPath;

    @Autowired
    private MessageHelper messageHelper;

    @PostConstruct
    public void configure() {
        File baseDir = new File(workingDirPath);
        boolean result = baseDir.exists() || baseDir.mkdirs();
        Assert.isTrue(result, "Could not create directory");
    }

    public GitlabClient getGitlabRootClient(String gitHost,
                                            String gitToken,
                                            Long gitAdminId,
                                            String gitAdminName) {
        return GitlabClient
                .initializeRootGitlabClientFromHostAndToken(gitHost, gitToken, authManager.getAuthorizedUser(),
                        gitAdminId, gitAdminName);
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
        return GitlabClient.initializeGitlabClientFromRepositoryAndToken(
                rootClient ? adminName : authManager.getAuthorizedUser(),
                repository, token, adminId, adminName, externalHost);
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
            return getGitlabClientForPipeline(pipeline)
                    .buildCloneCredentials(useEnvVars, issueToken, DEFAULT_TOKEN_DURATION);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public GitCredentials getGitlabCredentials(Long duration) {
        Long expiration = Optional.ofNullable(duration).orElse(DEFAULT_TOKEN_DURATION);
        try {
            return getDefaultGitlabClient()
                    .withUserName(authManager.getAuthorizedUser())
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
        Pipeline pipeline = pipelineManager.load(id);
        try {
            loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
        return this.getGitlabClientForPipeline(pipeline)
                .getRepositoryContents(path, getRevisionName(version), recursive).stream()
                .filter(e -> !e.getName().startsWith(".")).collect(Collectors.toList());
    }

    public List<GitRepositoryEntry> getPipelineSources(Long id, String version)
            throws GitClientException {
        return this.getPipelineSources(id, version, srcDirectory, false);
    }

    public List<GitRepositoryEntry> getPipelineSources(Long id,
                                                       String version,
                                                       String path,
                                                       boolean appendConfigurationFileIfNeeded,
                                                       boolean recursive)
            throws GitClientException {
        List<GitRepositoryEntry> entries;
        if (StringUtils.isNullOrEmpty(path)) {
            entries = this.getPipelineSources(id, version, srcDirectory, recursive);
            if (appendConfigurationFileIfNeeded) {
                GitRepositoryEntry configurationEntry = this.getConfigurationFileEntry(id, version);
                if (configurationEntry != null) {
                    entries.add(configurationEntry);
                }
            }
        } else {
            entries = this.getPipelineSources(id, version, path, recursive);
        }
        return entries;
    }

    private GitRepositoryEntry getConfigurationFileEntry(Long id, String version) throws GitClientException {
        Pipeline pipeline = pipelineManager.load(id);
        try {
            loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
        GitRepositoryEntry configurationFileEntry = null;
        List<GitRepositoryEntry> rootEntries = this.getGitlabClientForPipeline(pipeline)
                .getRepositoryContents("", getRevisionName(version), false);
        for (GitRepositoryEntry rootEntry : rootEntries) {
            if (rootEntry.getName().equalsIgnoreCase(CONFIG_FILE_NAME)) {
                configurationFileEntry = rootEntry;
                break;
            }
        }
        return configurationFileEntry;
    }

    public String getRevisionName(String version) {
        return version.startsWith(DRAFT_PREFIX) ? version.substring(DRAFT_PREFIX.length()) : version;
    }

    private Date parseGitDate(String dateStr) {
        LocalDateTime localDateTime =
                LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    public List<Revision> getPipelineRevisions(Pipeline pipeline, Long pageSize)
            throws GitClientException {
        GitlabClient client = this.getGitlabClientForPipeline(pipeline);
        List<Revision> tags = client.getRepositoryRevisions(pageSize).stream()
                .map(i -> new Revision(i.getName(), i.getMessage(),
                        parseGitDate(i.getCommit().getAuthoredDate()), i.getCommit().getId(),
                        i.getCommit().getAuthorName(), i.getCommit().getAuthorEmail()))
                .sorted(Comparator.comparing(Revision::getCreatedDate).reversed())
                .collect(Collectors.toList());
        List<Revision> revisions = new ArrayList<>(tags.size());
        addDraftRevision(client, revisions, tags);
        CollectionUtils.addAll(revisions, tags);
        return revisions;
    }

    public Revision createPipelineRevision(Pipeline pipeline,
                                           String revisionName,
                                           String commit,
                                           String message,
                                           String releaseDescription) throws GitClientException {
        Assert.isTrue(GitUtils.checkGitNaming(revisionName),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_REVISION_NAME, revisionName));
        GitlabClient client = this.getGitlabClientForPipeline(pipeline);
        GitTagEntry gitTagEntry = client.createRepositoryRevision(revisionName, commit, message, releaseDescription);
        return new Revision(gitTagEntry.getName(),
                gitTagEntry.getMessage(),
                parseGitDate(gitTagEntry.getCommit().getAuthoredDate()), gitTagEntry.getCommit().getId(),
                gitTagEntry.getCommit().getAuthorName(), gitTagEntry.getCommit().getAuthorEmail());
    }

    private void addDraftRevision(GitlabClient client, List<Revision> revisions,
                                  List<Revision> tags) throws GitClientException {
        List<GitCommitEntry> commits = client.getCommits();
        if (!CollectionUtils.isEmpty(commits)) {
            GitCommitEntry commit = commits.get(0);
            if (CollectionUtils.isEmpty(tags) || !tags.get(0).getCommitId()
                    .equals(commit.getId())) {
                revisions.add(new Revision(DRAFT_PREFIX + commit.getShortId(), commit.getMessage(),
                        parseGitDate(commit.getCreatedAt()), commit.getId(), Boolean.TRUE,
                        commit.getAuthorName(), commit.getAuthorEmail()));
            }
        }
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

    private boolean folderExists(Pipeline pipeline, String folder) throws GitClientException {
        return this.fileExists(pipeline, Paths.get(folder, GIT_FOLDER_TOKEN_FILE).toString());
    }

    private boolean fileExists(Pipeline pipeline, String filePath) throws GitClientException {
        if (StringUtils.isNullOrEmpty(filePath)) {
            return true;
        }
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipeline);
        boolean fileExists = false;
        try {
            fileExists = gitlabClient.getFileContents(filePath, GIT_MASTER_REPOSITORY) != null;
        } catch (UnexpectedResponseStatusException exception) {
            LOGGER.debug(exception.getMessage(), exception);
        }
        return fileExists;
    }

    public GitCommitEntry createOrRenameFolder(Long id, PipelineSourceItemVO folderVO) throws GitClientException {
        String folderName = FilenameUtils.getName(folderVO.getPath());
        Assert.isTrue(GitUtils.checkGitNaming(folderName),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_FOLDER_NAME, folderName));
        Pipeline pipeline = pipelineManager.load(id, true);
        if (folderVO.getPreviousPath() == null) {
            // Previous path is missing: creating update
            return createFolder(pipeline,
                    folderVO.getPath(),
                    folderVO.getLastCommitId(),
                    folderVO.getComment());
        } else {
            // else: renaming update
            return renameFolder(pipeline,
                    folderVO.getPreviousPath(),
                    folderVO.getPath(),
                    folderVO.getLastCommitId(),
                    folderVO.getComment());
        }
    }

    private GitCommitEntry createFolder(Pipeline pipeline,
                                       String folder,
                                       String lastCommitId,
                                       String commitMessage) throws GitClientException {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        if (commitMessage == null) {
            commitMessage = String.format("Creating update %s", folder);
        }
        List<String> filesToCreate = new ArrayList<>();
        Path folderToCreate = Paths.get(folder);
        while (folderToCreate != null && !StringUtils.isNullOrEmpty(folderToCreate.toString())) {
            if (!this.folderExists(pipeline, folderToCreate.toString())) {
                filesToCreate.add(Paths.get(folderToCreate.toString(), GIT_FOLDER_TOKEN_FILE).toString());
            }
            folderToCreate = folderToCreate.getParent();
        }
        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);
        for (String file : filesToCreate) {
            GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setAction("create");
            gitPushCommitActionEntry.setFilePath(file);
            gitPushCommitActionEntry.setContent("");
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return this.getGitlabClientForPipeline(pipeline).commit(gitPushCommitEntry);
    }

    private GitCommitEntry renameFolder(Pipeline pipeline,
                                       String folder,
                                       String newFolderName,
                                       String lastCommitId,
                                       String commitMessage) throws GitClientException {
        if (commitMessage == null) {
            commitMessage = String.format("Renaming update %s to %s", folder, newFolderName);
        }
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        Assert.isTrue(folderExists(pipeline, folder),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_NOT_FOUND, folder));
        Assert.isTrue(!folderExists(pipeline, newFolderName),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_ALREADY_EXISTS, newFolderName));
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);
        final List<GitRepositoryEntry> allFiles = gitlabClient.getRepositoryContents(folder,
                GIT_MASTER_REPOSITORY, true);

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);

        for (GitRepositoryEntry file : allFiles) {
            if (file.getType().equalsIgnoreCase("tree")) {
                continue;
            }
            prepareFileForRenaming(file.getPath().replaceFirst(folder, newFolderName), file.getPath(),
                    gitPushCommitEntry, gitlabClient);
        }
        return gitlabClient.commit(gitPushCommitEntry);
    }

    public GitCommitEntry removeFolder(Pipeline pipeline,
                                       String folder,
                                       String lastCommitId,
                                       String commitMessage) throws GitClientException {
        if (commitMessage == null) {
            commitMessage = String.format("Removing update %s", folder);
        }
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        Assert.isTrue(!StringUtils.isNullOrEmpty(folder),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_ROOT_FOLDER_CANNOT_BE_REMOVED));
        Assert.isTrue(!folder.equalsIgnoreCase(srcDirectory),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_CANNOT_BE_REMOVED, folder));
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipeline);
        List<GitRepositoryEntry> allFiles = gitlabClient.getRepositoryContents(folder,
                GIT_MASTER_REPOSITORY,
                true);

        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);
        for (GitRepositoryEntry file : allFiles) {
            if (file.getType().equalsIgnoreCase("tree")) {
                continue;
            }
            GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setAction("delete");
            gitPushCommitActionEntry.setFilePath(file.getPath());
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return gitlabClient.commit(gitPushCommitEntry);
    }

    public GitCommitEntry modifyFile(Pipeline pipeline,
                                     PipelineSourceItemVO sourceItemVO) throws GitClientException {
        String sourcePath = sourceItemVO.getPath();
        Arrays.stream(sourcePath.split(PATH_DELIMITER)).forEach(pathPart ->
            Assert.isTrue(GitUtils.checkGitNaming(pathPart),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_FILE_NAME, sourcePath)));
        if (StringUtils.isNullOrEmpty(sourceItemVO.getPreviousPath())) {
            return this.updateFile(pipeline,
                    sourcePath,
                    sourceItemVO.getContents(),
                    sourceItemVO.getLastCommitId(),
                    sourceItemVO.getComment());
        } else {
            return this.renameFile(pipeline,
                    sourcePath,
                    sourceItemVO.getPreviousPath(),
                    sourceItemVO.getLastCommitId(),
                    sourceItemVO.getComment());
        }
    }

    protected GitCommitEntry updateFile(Pipeline pipeline,
                                     String filePath,
                                     String fileContent,
                                     String lastCommitId,
                                     String commitMessage) throws GitClientException {
        return updateFile(pipeline, filePath, fileContent, lastCommitId, commitMessage, false);
    }

    protected GitCommitEntry updateFile(Pipeline pipeline,
            String filePath,
            String fileContent,
            String lastCommitId,
            String commitMessage,
            boolean checkCommit) throws GitClientException {
        if (checkCommit) {
            Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                    messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED,
                            filePath));
        }
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipeline);
        boolean fileExists = false;
        try {
            fileExists = gitlabClient.getFileContents(filePath, GIT_MASTER_REPOSITORY) != null;
        } catch (UnexpectedResponseStatusException exception) {
            LOGGER.debug(exception.getMessage(), exception);
        }

        GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        String message =
                getCommitMessage(commitMessage, filePath, fileExists, gitPushCommitActionEntry);
        gitPushCommitActionEntry.setFilePath(filePath);
        gitPushCommitActionEntry.setContent(fileContent);

        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(message);
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return gitlabClient.commit(gitPushCommitEntry);
    }

    public GitCommitEntry updateFiles(Pipeline pipeline, PipelineSourceItemsVO sourceItemVOList)
            throws GitClientException {
        if (CollectionUtils.isEmpty(sourceItemVOList.getItems())) {
            return null;
        }
        String lastCommitId = sourceItemVOList.getLastCommitId();
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_WAS_UPDATED));
        GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);
        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        if (StringUtils.isNullOrEmpty(sourceItemVOList.getComment())) {
            gitPushCommitEntry.setCommitMessage(String.format("Updating files %s", StringUtils.join(", ",
                    String.valueOf(sourceItemVOList.getItems().stream().map(PipelineSourceItemVO::getPath)))));
        } else {
            gitPushCommitEntry.setCommitMessage(sourceItemVOList.getComment());
        }
        for (PipelineSourceItemVO sourceItemVO : sourceItemVOList.getItems()) {
            String sourcePath = sourceItemVO.getPath();
            Arrays.stream(sourcePath.split(PATH_DELIMITER)).forEach(pathPart ->
                    Assert.isTrue(GitUtils.checkGitNaming(pathPart),
                            messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_FILE_NAME, sourcePath)));

            String action;
            if (!StringUtils.isNullOrEmpty(sourceItemVO.getPreviousPath())) {
                action = ACTION_MOVE;
            } else {
                boolean fileExists = false;
                try {
                    fileExists = gitlabClient.getFileContents(sourcePath, GIT_MASTER_REPOSITORY) != null;
                } catch (UnexpectedResponseStatusException exception) {
                    LOGGER.debug(exception.getMessage(), exception);
                }
                if (fileExists) {
                    action = "update";
                } else {
                    action = "create";
                }
            }
            GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            gitPushCommitActionEntry.setFilePath(sourcePath);
            gitPushCommitActionEntry.setAction(action);
            if (StringUtils.isNullOrEmpty(sourceItemVO.getPreviousPath())) {
                gitPushCommitActionEntry.setContent(sourceItemVO.getContents());
            } else {
                gitPushCommitActionEntry.setPreviousPath(sourceItemVO.getPreviousPath());
            }
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }
        return gitlabClient.commit(gitPushCommitEntry);
    }

    private GitCommitEntry renameFile(Pipeline pipeline,
                                     String filePath,
                                     String filePreviousPath,
                                     String lastCommitId,
                                     String commitMessage) throws GitClientException {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, filePath));
        final GitlabClient gitlabClient = getGitlabClientForPipeline(pipeline);

        if (StringUtils.isNullOrEmpty(commitMessage)) {
            commitMessage = String.format("Renaming '%s' to '%s", filePreviousPath, filePath);
        }

        final GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);
        prepareFileForRenaming(filePath, filePreviousPath, gitPushCommitEntry, gitlabClient);

        return gitlabClient.commit(gitPushCommitEntry);
    }

    private GitPushCommitEntry prepareFileForRenaming(final String filePath,
                                                      final String filePreviousPath,
                                                      final GitPushCommitEntry gitPushCommitEntry,
                                                      final GitlabClient gitlabClient) throws GitClientException {
        final byte[] fileContents = gitlabClient.getFileContents(filePreviousPath, GIT_MASTER_REPOSITORY);

        final GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        gitPushCommitActionEntry.setAction(ACTION_MOVE);
        gitPushCommitActionEntry.setFilePath(filePath);
        gitPushCommitActionEntry.setPreviousPath(filePreviousPath);
        gitPushCommitActionEntry.setContent(Base64.getEncoder().encodeToString(fileContents));
        gitPushCommitActionEntry.setEncoding(BASE64_ENCODING);
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return gitPushCommitEntry;
    }

    public GitCommitEntry uploadFiles(Pipeline pipeline,
                                     String folder,
                                     List<UploadFileMetadata> files,
                                     String lastCommitId,
                                     String commitMessage) throws GitClientException {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_WAS_UPDATED));
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipeline);
        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();

        for (UploadFileMetadata file : files) {
            String filePath = folder + PATH_DELIMITER + file.getFileName();
            Arrays.stream(filePath.split(PATH_DELIMITER)).forEach(pathPart ->
                    Assert.isTrue(GitUtils.checkGitNaming(pathPart),
                            messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_FILE_NAME, filePath)));

            boolean fileExists = false;
            try {
                fileExists = gitlabClient.getFileContents(filePath, GIT_MASTER_REPOSITORY) != null;
            } catch (UnexpectedResponseStatusException exception) {
                LOGGER.debug(exception.getMessage(), exception);
            }
            GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
            commitMessage =
                    getCommitMessage(commitMessage, filePath, fileExists, gitPushCommitActionEntry);
            gitPushCommitActionEntry.setFilePath(filePath);
            gitPushCommitActionEntry.setContent(Base64.getEncoder().encodeToString(file.getBytes()));
            gitPushCommitActionEntry.setEncoding(BASE64_ENCODING);
            gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        }

        gitPushCommitEntry.setCommitMessage(commitMessage);
        return gitlabClient.commit(gitPushCommitEntry);
    }

    private String getCommitMessage(String commitMessage, String filePath, boolean fileExists,
            GitPushCommitActionEntry gitPushCommitActionEntry) {
        String message = commitMessage;
        if (fileExists) {
            gitPushCommitActionEntry.setAction("update");
            if (StringUtils.isNullOrEmpty(commitMessage)) {
                message = String.format("Updating '%s'", filePath);
            }
        } else {
            gitPushCommitActionEntry.setAction("create");
            if (StringUtils.isNullOrEmpty(commitMessage)) {
                message = String.format("Creating '%s'", filePath);
            }
        }
        return message;
    }

    public GitCommitEntry deleteFile(Pipeline pipeline,
                                     String filePath,
                                     String lastCommitId,
                                     String commitMessage) throws GitClientException {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, filePath));
        GitlabClient gitlabClient = this.getGitlabClientForPipeline(pipeline);

        GitPushCommitActionEntry gitPushCommitActionEntry = new GitPushCommitActionEntry();
        gitPushCommitActionEntry.setAction("delete");
        gitPushCommitActionEntry.setFilePath(filePath);

        GitPushCommitEntry gitPushCommitEntry = new GitPushCommitEntry();
        gitPushCommitEntry.setCommitMessage(commitMessage);
        gitPushCommitEntry.getActions().add(gitPushCommitActionEntry);
        return gitlabClient.commit(gitPushCommitEntry);
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
    public List<GitRepositoryEntry> getPipelineDocs(Long id, String version)
            throws GitClientException {
        Pipeline pipeline = pipelineManager.load(id);
        try {
            loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
        return this.getGitlabClientForPipeline(pipeline)
                .getRepositoryContents(docsDirectory, getRevisionName(version), false).stream()
                .filter(e -> !e.getName().startsWith(".")).collect(Collectors.toList());
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
        byte[] configBytes = getPipelineFileContents(pipeline, getRevisionName(version),
                CONFIG_FILE_NAME);
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
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL));
    }

    public boolean checkProjectExists(String name) {
        try {
            return getDefaultGitlabClient().projectExists(name);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private GitlabClient getDefaultGitlabClient() {
        String gitHost = preferenceManager.getPreference(SystemPreferences.GIT_HOST);
        String gitToken = preferenceManager.getPreference(SystemPreferences.GIT_TOKEN);
        Long gitAdminId = Long.valueOf(preferenceManager.getPreference(SystemPreferences.GIT_USER_ID));
        String gitAdminName = preferenceManager.getPreference(SystemPreferences.GIT_USER_NAME);
        return GitlabClient.initializeRootGitlabClientFromHostAndToken(gitHost, gitToken,
                authManager.getAuthorizedUser(), gitAdminId, gitAdminName);
    }

    public GitProject createRepository(String templateId,
                                       String description,
                                       String repository,
                                       String token)
            throws GitClientException {
        TemplatesScanner templatesScanner = new TemplatesScanner(templatesDirectoryPath);
        Template template = templatesScanner.listTemplates().get(templateId);
        Assert.notNull(template, "There is no such a template: " + templateId);
        return getGitlabClientForRepository(repository, token, true)
                .createTemplateRepository(template,
                        description,
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_INDEXING_ENABLED),
                        preferenceManager.getPreference(SystemPreferences.GIT_REPOSITORY_HOOK_URL));
    }

    public void deletePipelineRepository(Pipeline pipeline) throws GitClientException {
        this.getGitlabClientForPipeline(pipeline, true).deleteRepository();
    }

    private void checkRevision(Pipeline pipeline, String version) {
        try {
            loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private File checkoutConfigToDirectory(Pipeline pipeline, String version, String repoPath)
            throws GitClientException {
        checkoutRepo(pipeline, version, repoPath);
        return new File(repoPath, CONFIG_FILE_NAME);
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
        Pipeline pipeline = pipelineManager.load(id);
        try {
            loadRevision(pipeline, version);
        } catch (GitClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage());
        }

        return this.getGitlabClientForPipeline(pipeline)
                .getRepositoryContents(path, getRevisionName(version), true).stream()
                .filter(e -> !e.getName().startsWith(".")).collect(Collectors.toList());
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

    public GitProject getRepository(String repository, String token) {
        try {
            return getGitlabClientForRepository(repository, token, false).getProject();
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public GitProject getRepository(String name) {
        try {
            return getDefaultGitlabClient().getProject(name);
        } catch (GitClientException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
