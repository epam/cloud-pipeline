/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class PipelineRepositoryService {
    private static final String TEMPLATE_DESCRIPTION = "description.txt";
    private static final String CONFIG_JSON = "config.json";
    private static final String TEMPLATE_PLACEHOLDER = "@";
    private static final String README_DEFAULT_CONTENTS = "# Job definition\n\n"
            + "This is an initial job definition `README`\n\n"
            + "Feel free to customize it\n\n# Quick start\n\n"
            + "1. Modify job scripts using `CODE` tab above\n"
            + "2. Fine - tune job parameters and execution environment using `CONFIGURATION`"
            + " tab above or keep default values\n"
            + "3. Launch you job using `RUN` button\n";
    private static final String DEFAULT_README = "docs/README.md";
    private static final String DEFAULT_BRANCH = "master";
    private static final String GITKEEP_CONTENT = "keep";

    private final PipelineRepositoryProviderService providerService;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final String defaultTemplate;
    private final String templatesDirectoryPath;

    public PipelineRepositoryService(final PipelineRepositoryProviderService providerService,
                                     final MessageHelper messageHelper,
                                     final PreferenceManager preferenceManager,
                                     @Value("${templates.default.template}") final String defaultTemplate,
                                     @Value("${templates.directory}") final String templatesDirectoryPath) {
        this.providerService = providerService;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.defaultTemplate = defaultTemplate;
        this.templatesDirectoryPath = templatesDirectoryPath;
    }

    public GitProject createGitRepositoryWithRepoUrl(final PipelineVO pipelineVO) throws GitClientException {
        if (pipelineVO.getPipelineType() == PipelineType.PIPELINE) {
            return createTemplateRepository(
                    pipelineVO.getRepositoryType(),
                    pipelineVO.getTemplateId(),
                    pipelineVO.getDescription(),
                    pipelineVO.getRepository(),
                    pipelineVO.getRepositoryToken(),
                    pipelineVO.getBranch());
        }
        return createEmptyRepository(pipelineVO.getRepositoryType(), pipelineVO.getDescription(),
                pipelineVO.getRepository(), pipelineVO.getRepositoryToken(), true, pipelineVO.getBranch());
    }

    public GitProject updateRepositoryName(final Pipeline pipeline, final String currentRepositoryPath,
                                           final String newName) {
        return providerService.renameRepository(pipeline.getRepositoryType(), currentRepositoryPath, newName,
                pipeline.getRepositoryToken());
    }

    public void deletePipelineRepository(final Pipeline pipeline) {
        providerService.deleteRepository(pipeline.getRepositoryType(), pipeline);
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return providerService.getRepository(repositoryType, repositoryPath, token);
    }

    public List<String> getBranches(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return providerService.getBranches(repositoryType, repositoryPath, token);
    }

    public List<Revision> getPipelineRevisions(final RepositoryType repositoryType, final Pipeline pipeline) {
        final List<Revision> tags = providerService.getTags(repositoryType, pipeline).stream()
                .filter(revision -> Objects.nonNull(revision.getCreatedDate()))
                .sorted(Comparator.comparing(Revision::getCreatedDate).reversed())
                .collect(Collectors.toList());
        final String ref = buildBranchRefOrNull(pipeline.getBranch());
        final Revision commit = providerService.getLastCommit(pipeline, ref);
        final List<Revision> revisions = new ArrayList<>(tags.size());
        if (isDraftCommit(tags, commit)) {
            commit.setName(getCommitName(commit, repositoryType));
            commit.setDraft(true);
            revisions.add(commit);
        }
        CollectionUtils.addAll(revisions, tags);
        return revisions;
    }

    public Revision createPipelineRevision(final Pipeline pipeline,
                                           final String revisionName,
                                           final String commit,
                                           final String message,
                                           final String releaseDescription) {
        Assert.isTrue(GitUtils.checkGitNaming(revisionName),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_REVISION_NAME, revisionName));
        if (revisionName == null) {
            throw new GitClientException("Tag name is required");
        }
        if (commit == null) {
            throw new GitClientException("Ref (commit SHA, another tag name, or branch name) is required");
        }
        return providerService.createTag(pipeline, revisionName, commit, message, releaseDescription);
    }

    public byte[] getFileContents(final Pipeline pipeline, final String revision, final String path) {
        final RepositoryType repositoryType = pipeline.getRepositoryType();
        final String token = pipeline.getRepositoryToken();
        final GitProject gitProject = new GitProject();
        gitProject.setRepoUrl(pipeline.getRepository());
        return getFileContents(repositoryType, gitProject, path, GitUtils.getRevisionName(revision), token);
    }

    public byte[] getTruncatedPipelineFileContent(final Pipeline pipeline, final String revision,
                                                  final String path, final int byteLimit) throws GitClientException {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        return providerService.getTruncatedFileContents(pipeline, GitUtils.withoutLeadingDelimiter(path),
                GitUtils.getRevisionName(revision), byteLimit);
    }

    public GitCredentials getPipelineCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                                      final boolean issueToken, final Long duration) {
        return providerService.getCloneCredentials(pipeline, useEnvVars, issueToken, duration);
    }

    public GitTagEntry loadRevision(final Pipeline pipeline, final String version) throws GitClientException {
        Assert.notNull(version, "Revision is required.");
        if (version.startsWith(GitUtils.DRAFT_PREFIX)) {
            final GitCommitEntry repositoryCommit = providerService
                    .getCommit(pipeline, GitUtils.getRevisionName(version));
            if (repositoryCommit == null) {
                throw new IllegalArgumentException(String.format("Commit %s not found.", version));
            }
            return new GitTagEntry(repositoryCommit);
        } else {
            final GitTagEntry revision = providerService.getTag(pipeline, version);
            if (revision == null) {
                throw new IllegalArgumentException(
                        String.format("Revision %s not found.", version));
            }
            return revision;
        }
    }

    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version, final boolean recursive) {
        return getRepositoryContents(pipeline, path, version, recursive, false);
    }

    public List<GitRepositoryEntry> getRepositoryContents(final Pipeline pipeline, final String path,
                                                          final String version, final boolean recursive,
                                                          final boolean showHiddenFiles) {
        return ListUtils.emptyIfNull(providerService.getRepositoryContents(pipeline,
                GitUtils.withoutLeadingDelimiter(path), GitUtils.getRevisionName(version), recursive)).stream()
                .filter(entry -> showHiddenFiles || !entry.getName().startsWith(Constants.DOT))
                .collect(Collectors.toList());
    }

    public GitCommitEntry updateFile(final Pipeline pipeline,
                                     final String filePath,
                                     final String fileContent,
                                     final String lastCommitId,
                                     final String commitMessage,
                                     final boolean checkCommit) {
        if (checkCommit) {
            Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                    messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, filePath));
        }
        final boolean fileExists = fileExists(pipeline, filePath);
        final String message = GitUtils.buildModifyFileCommitMessage(commitMessage, filePath, fileExists);
        return providerService.updateFile(pipeline, filePath, fileContent, message, fileExists);
    }

    public GitCommitEntry renameFile(final Pipeline pipeline,
                                     final String filePath,
                                     final String filePreviousPath,
                                     final String lastCommitId,
                                     final String commitMessage) {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, filePath));

        final String message = StringUtils.isNotBlank(commitMessage)
                ? commitMessage
                : String.format("Renaming %s to %s", filePreviousPath, filePath);

        Assert.isTrue(!fileExists(pipeline, filePath),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_ALREADY_EXISTS, filePath));
        return providerService.renameFile(pipeline, message, filePreviousPath, filePath);
    }

    public GitCommitEntry deleteFile(final Pipeline pipeline,
                                     final String filePath,
                                     final String lastCommitId,
                                     final String commitMessage) throws GitClientException {
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, filePath));
        return providerService.deleteFile(pipeline, filePath, commitMessage);
    }


    public GitCommitEntry createFolder(final Pipeline pipeline,
                                       final String folder,
                                       final String lastCommitId,
                                       final String commitMessage) throws GitClientException {
        if (pipeline.getRepositoryType() == RepositoryType.BITBUCKET_CLOUD) {
            throw new UnsupportedOperationException("Folder creation is not supported for Bitbucket Cloud repository");
        }
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        Assert.isTrue(!folderExists(pipeline, folder),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_ALREADY_EXISTS, folder));

        final String message = StringUtils.isNotBlank(commitMessage)
                ? commitMessage
                : String.format("Creating folder %s", folder);

        final List<String> filesToCreate = new ArrayList<>();
        final List<String> foldersToCheck = new ArrayList<>();
        Path folderToCreate = Paths.get(folder);
        while (folderToCreate != null && StringUtils.isNotBlank(folderToCreate.toString())) {
            if (!folderExists(pipeline, folderToCreate.toString())) {
                filesToCreate.add(Paths.get(folderToCreate.toString(), GitUtils.GITKEEP_FILE).toString());
                foldersToCheck.add(folderToCreate.toString());
            }
            folderToCreate = folderToCreate.getParent();
        }

        checkFolderHierarchyIfFileExists(pipeline, foldersToCheck);

        return providerService.createFolder(pipeline, filesToCreate, message);
    }

    public GitCommitEntry renameFolder(final Pipeline pipeline,
                                       final String folder,
                                       final String newFolderName,
                                       final String lastCommitId,
                                       final String commitMessage) throws GitClientException {
        if (pipeline.getRepositoryType() == RepositoryType.BITBUCKET_CLOUD) {
            throw new UnsupportedOperationException("Folder renaming is not supported for Bitbucket Cloud repository");
        }
        final String message = StringUtils.isNotBlank(commitMessage)
                ? commitMessage
                : String.format("Renaming folder %s to %s", folder, newFolderName);
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        Assert.isTrue(folderExists(pipeline, folder),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_NOT_FOUND, folder));
        Assert.isTrue(!folderExists(pipeline, newFolderName),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_ALREADY_EXISTS, newFolderName));
        return providerService.renameFolder(pipeline, message, folder, newFolderName);
    }

    public GitCommitEntry removeFolder(final Pipeline pipeline,
                                       final String folder,
                                       final String lastCommitId,
                                       final String commitMessage,
                                       final String srcDirectory) throws GitClientException {
        final String message = StringUtils.isNotBlank(commitMessage)
                ? commitMessage
                : String.format("Removing folder %s", folder);
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_WAS_UPDATED, folder));
        Assert.isTrue(!com.amazonaws.util.StringUtils.isNullOrEmpty(folder),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_ROOT_FOLDER_CANNOT_BE_REMOVED));
        Assert.isTrue(!folder.equalsIgnoreCase(srcDirectory),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FOLDER_CANNOT_BE_REMOVED, folder));

        return providerService.deleteFolder(pipeline, message, folder);
    }

    public GitCommitEntry updateFiles(final Pipeline pipeline, final PipelineSourceItemsVO sourceItemVOList)
            throws GitClientException {
        if (CollectionUtils.isEmpty(sourceItemVOList.getItems())) {
            return null;
        }
        final String lastCommitId = sourceItemVOList.getLastCommitId();
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_WAS_UPDATED));
        final String message = StringUtils.isNotBlank(sourceItemVOList.getComment())
                ? sourceItemVOList.getComment()
                : String.format("Updating files %s", sourceItemVOList.getItems().stream()
                .map(PipelineSourceItemVO::getPath)
                .collect(Collectors.joining(", ")));
        sourceItemVOList.getItems().stream()
                .peek(sourceItemVO -> sourceItemVO.setPath(GitUtils.withoutLeadingDelimiter(sourceItemVO.getPath())))
                .forEach(sourceItemVO -> validateFilePath(sourceItemVO.getPath()));

        return providerService.updateFiles(pipeline, sourceItemVOList, message);
    }

    public GitCommitEntry uploadFiles(final Pipeline pipeline,
                                      final String folder,
                                      final List<UploadFileMetadata> files,
                                      final String lastCommitId,
                                      final String commitMessage) throws GitClientException {
        if (CollectionUtils.isEmpty(files)) {
            return null;
        }
        Assert.isTrue(lastCommitId.equals(pipeline.getCurrentVersion().getCommitId()),
                messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_WAS_UPDATED));
        ListUtils.emptyIfNull(files).stream()
                .peek(file -> file.setFileName(getFilePath(folder, file.getFileName())))
                .forEach(file -> validateFilePath(file.getFileName()));
        final String message = StringUtils.isNotBlank(commitMessage)
                ? commitMessage
                : String.format("Uploading files to folder %s", folder);

        return providerService.uploadFiles(pipeline, files, message);
    }

    public boolean fileExists(final Pipeline pipeline, final String filePath) {
        if (StringUtils.isBlank(filePath)) {
            return true;
        }
        try {
            return providerService.fileExists(pipeline, filePath);
        } catch (UnexpectedResponseStatusException exception) {
            log.debug(exception.getMessage(), exception);
            return false;
        }
    }

    private byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                   final String path, final String revision, final String token) {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        return providerService
                .getFileContents(repositoryType, repository, GitUtils.withoutLeadingDelimiter(path), revision, token);
    }

    private void uploadFolder(final RepositoryType repositoryType, final Template template,
                              final GitProject project, final String token, final String branch)
            throws GitClientException {
        final String templateRootFolder = Paths.get(template.getDirPath()).toAbsolutePath().toString();
        if (!Paths.get(template.getDirPath()).toFile().exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(template.getDirPath()))) {
            walk.forEach(path -> uploadFile(repositoryType, project, templateRootFolder, path, token, branch));
        } catch (IOException e) {
            throw new GitClientException(e.getMessage());
        }
    }

    private void uploadFile(final RepositoryType repositoryType, final GitProject project,
                            final String templateRootFolder, final Path path, final String token, final String branch) {
        final String repoName = project.getName();
        final File file = path.toFile();
        if (!file.isFile()) {
            return;
        }
        final String relativePath = file.getAbsolutePath().substring(templateRootFolder.length() + 1);
        if (relativePath.equals(TEMPLATE_DESCRIPTION)) {
            return;
        }
        if (relativePath.equals(CONFIG_JSON)) {
            final String content = getFileContent(file.getAbsolutePath()).replaceAll(TEMPLATE_PLACEHOLDER, repoName);
            providerService.createFile(repositoryType, project, CONFIG_JSON, content, token, branch);
            return;
        }
        providerService.createFile(repositoryType, project,
                normalizePath(relativePath.replaceAll(TEMPLATE_PLACEHOLDER, repoName)),
                getFileContent(file.getAbsolutePath()), token, branch);
    }

    private String getFileContent(final String path) {
        try (InputStream stream = new FileInputStream(path)) {
            final List<String> lines = IOUtils.readLines(stream);
            return String.join(Constants.NEWLINE, lines);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Strings.EMPTY;
        }
    }

    private String normalizePath(String path) {
        if (File.separator.equals("\\")) {
            return path.replaceAll("\\\\", "/");
        }
        return path;
    }

    private boolean isDraftCommit(final List<Revision> tags, final Revision lastCommit) {
        if (Objects.isNull(lastCommit)) {
            return false;
        }
        if (CollectionUtils.isEmpty(tags)) {
            return true;
        }
        return !Objects.equals(tags.get(0).getCommitId(), String.valueOf(lastCommit.getCommitId()));
    }

    private GitProject createEmptyRepository(final RepositoryType repositoryType, final String description,
                                             final String repositoryPath, final String token, final boolean initCommit,
                                             final String branch) throws GitClientException {
        final GitProject repository = providerService.createRepository(repositoryType, description,
                repositoryPath, token, preferenceManager.getPreference(SystemPreferences.GITLAB_PROJECT_VISIBILITY));

        providerService.handleHook(repositoryType, repository, token);

        if (initCommit) {
            providerService.createFile(repositoryType, repository, GitUtils.GITKEEP_FILE, GITKEEP_CONTENT,
                    token, branch);
        }

        return repository;
    }

    private GitProject createTemplateRepository(final RepositoryType repositoryType, final String pipelineTemplateId,
                                                final String description, final String repositoryPath,
                                                final String token, final String branch) {
        final String templateId = Optional.ofNullable(pipelineTemplateId).orElse(defaultTemplate);
        final TemplatesScanner templatesScanner = new TemplatesScanner(templatesDirectoryPath);
        final Template template = templatesScanner.listTemplates().get(templateId);
        Assert.notNull(template, "There is no such a template: " + templateId);

        final GitProject repository = createEmptyRepository(repositoryType, description, repositoryPath,
                token, false, branch);

        uploadFolder(repositoryType, template, repository, token, branch);

        try {
            boolean fileExists = Objects.nonNull(getFileContents(repositoryType, repository,
                    DEFAULT_README, DEFAULT_BRANCH, token));
            if (!fileExists) {
                providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS,
                        token, branch);
            }
        } catch (UnexpectedResponseStatusException e) {
            providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS,
                    token, branch);
        } catch (GitClientException exception) {
            log.debug(exception.getMessage(), exception);
        }
        return repository;
    }

    private boolean folderExists(final Pipeline pipeline, final String folder) {
        try {
            return !getRepositoryContents(pipeline, folder, GitUtils.getBranchRefOrDefault(pipeline.getBranch()),
                    false, true).isEmpty();
        } catch (GitClientException e) {
            return false;
        }
    }

    private void checkFolderHierarchyIfFileExists(final Pipeline pipeline, final List<String> folders)
            throws GitClientException {
        for (String folder : folders) {
            if (!folderExists(pipeline, folder)) {
                Assert.isTrue(!fileExists(pipeline, folder),
                        messageHelper.getMessage(MessageConstants.ERROR_REPOSITORY_FILE_ALREADY_EXISTS, folder));
            }
        }
    }

    private String getFilePath(final String folder, final String fileName) {
        if (StringUtils.isBlank(folder) || folder.equals(Constants.PATH_DELIMITER)) {
            return fileName;
        }
        final String folderPath = GitUtils.withoutLeadingDelimiter(ProviderUtils.withoutTrailingDelimiter(folder));
        return folderPath + Constants.PATH_DELIMITER + fileName;
    }

    private void validateFilePath(final String path) {
        Arrays.stream(path.split(Constants.PATH_DELIMITER)).forEach(pathPart ->
                Assert.isTrue(GitUtils.checkGitNaming(pathPart),
                        messageHelper.getMessage(MessageConstants.ERROR_INVALID_PIPELINE_FILE_NAME, path)));
    }

    private String buildBranchRefOrNull(final String branch) {
        return StringUtils.isNotBlank(branch) ? String.format(GitUtils.BRANCH_REF_PATTERN, branch) : null;
    }

    private static String getCommitName(final Revision commit, final RepositoryType repositoryType) {
        return GitUtils.DRAFT_PREFIX +
                (RepositoryType.BITBUCKET_CLOUD.equals(repositoryType) ?
                        commit.getCommitId().substring(0, 6) :
                        commit.getName());
    }
}
