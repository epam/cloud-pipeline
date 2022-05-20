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

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.vo.PipelineVO;
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
    private static final String DRAFT_PREFIX = "draft-";
    private static final String GITKEEP_FILE = ".gitkeep";
    private static final String GITKEEP_CONTENT = "keep";

    private final PipelineRepositoryProviderService providerService;
    private final String defaultTemplate;
    private final String templatesDirectoryPath;

    public PipelineRepositoryService(final PipelineRepositoryProviderService providerService,
                                     @Value("${templates.default.template}") final String defaultTemplate,
                                     @Value("${templates.directory}") final String templatesDirectoryPath) {
        this.providerService = providerService;
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
                    pipelineVO.getRepositoryToken());
        }
        return createEmptyRepository(pipelineVO.getRepositoryType(), pipelineVO.getDescription(),
                pipelineVO.getRepository(), pipelineVO.getRepositoryToken(), true);
    }

    public void deletePipelineRepository(final Pipeline pipeline) {
        providerService.deleteRepository(pipeline.getRepositoryType(), pipeline);
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String repositoryPath,
                                    final String token) {
        return providerService.getRepository(repositoryType, repositoryPath, token);
    }

    public List<Revision> getPipelineRevisions(final RepositoryType repositoryType, final Pipeline pipeline) {
        final List<Revision> tags = providerService.getTags(repositoryType, pipeline).stream()
                .filter(revision -> Objects.nonNull(revision.getCreatedDate()))
                .sorted(Comparator.comparing(Revision::getCreatedDate).reversed())
                .collect(Collectors.toList());
        final Revision commit = providerService.getLastCommit(repositoryType, pipeline);
        final List<Revision> revisions = new ArrayList<>(tags.size());
        if (isDraftCommit(tags, commit)) {
            commit.setName(DRAFT_PREFIX + commit.getName());
            commit.setDraft(true);
            revisions.add(commit);
        }
        CollectionUtils.addAll(revisions, tags);
        return revisions;
    }

    public byte[] getFileContents(final Pipeline pipeline, final String revision, final String path) {
        final RepositoryType repositoryType = pipeline.getRepositoryType();
        final String token = pipeline.getRepositoryToken();
        final GitProject gitProject = new GitProject();
        gitProject.setRepoUrl(pipeline.getRepository());
        return getFileContents(repositoryType, gitProject, path, getRevisionName(revision), token);
    }

    public GitCredentials getPipelineCloneCredentials(final Pipeline pipeline, final boolean useEnvVars,
                                                      final boolean issueToken, final Long duration) {
        return providerService.getCloneCredentials(pipeline, useEnvVars, issueToken, duration);
    }

    public GitTagEntry loadRevision(final Pipeline pipeline, final String version) throws GitClientException {
        Assert.notNull(version, "Revision is required.");
        if (version.startsWith(DRAFT_PREFIX)) {
            final GitCommitEntry repositoryCommit = providerService.getCommit(pipeline, getRevisionName(version));
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
        return ListUtils.emptyIfNull(providerService
                .getRepositoryContents(pipeline, path, getRevisionName(version), recursive)).stream()
                .filter(entry -> showHiddenFiles || !entry.getName().startsWith(Constants.DOT))
                .collect(Collectors.toList());
    }

    private byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                   final String path, final String revision, final String token) {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        return providerService.getFileContents(repositoryType, repository, path, revision, token);
    }

    private void uploadFolder(final RepositoryType repositoryType, final Template template,
                              final GitProject project, final String token) throws GitClientException {
        final String templateRootFolder = Paths.get(template.getDirPath()).toAbsolutePath().toString();
        if (!Paths.get(template.getDirPath()).toFile().exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(template.getDirPath()))) {
            walk.forEach(path -> uploadFile(repositoryType, project, templateRootFolder, path, token));
        } catch (IOException e) {
            throw new GitClientException(e.getMessage());
        }
    }

    private void uploadFile(final RepositoryType repositoryType, final GitProject project,
                            final String templateRootFolder, final Path path, final String token) {
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
            providerService.createFile(repositoryType, project, CONFIG_JSON, content, token);
            return;
        }
        providerService.createFile(repositoryType, project,
                normalizePath(relativePath.replaceAll(TEMPLATE_PLACEHOLDER, repoName)),
                getFileContent(file.getAbsolutePath()), token);
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
                                             final String repositoryPath, final String token, final boolean initCommit)
            throws GitClientException {
        final GitProject repository = providerService.createRepository(repositoryType, description,
                repositoryPath, token);

        providerService.handleHook(repositoryType, repository, token);

        if (initCommit) {
            providerService.createFile(repositoryType, repository, GITKEEP_FILE, GITKEEP_CONTENT, token);
        }

        return repository;
    }

    private GitProject createTemplateRepository(final RepositoryType repositoryType, final String pipelineTemplateId,
                                                final String description, final String repositoryPath,
                                                final String token) {
        final String templateId = Optional.ofNullable(pipelineTemplateId).orElse(defaultTemplate);
        final TemplatesScanner templatesScanner = new TemplatesScanner(templatesDirectoryPath);
        final Template template = templatesScanner.listTemplates().get(templateId);
        Assert.notNull(template, "There is no such a template: " + templateId);

        final GitProject repository = createEmptyRepository(repositoryType, description, repositoryPath,
                token, false);

        uploadFolder(repositoryType, template, repository, token);

        try {
            boolean fileExists = Objects.nonNull(getFileContents(repositoryType, repository,
                    DEFAULT_README, DEFAULT_BRANCH, token));
            if (!fileExists) {
                providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS, token);
            }
        } catch (UnexpectedResponseStatusException e) {
            providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS, token);
        } catch (GitClientException exception) {
            log.debug(exception.getMessage(), exception);
        }
        return repository;
    }

    private String getRevisionName(final String version) {
        return version.startsWith(DRAFT_PREFIX) ? version.substring(DRAFT_PREFIX.length()) : version;
    }
}
