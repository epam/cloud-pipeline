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
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.epam.pipeline.utils.GitUtils;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public boolean checkRepositoryExists(final RepositoryType repositoryType, final String name) {
        return providerService.checkRepositoryExists(repositoryType, name);
    }

    public GitProject createRepository(final PipelineVO pipelineVO) throws GitClientException {
        if (pipelineVO.getPipelineType() == PipelineType.PIPELINE) {
            return createTemplateRepository(pipelineVO);
        }
        return providerService.createEmptyRepository(pipelineVO.getRepositoryType(),
                GitUtils.convertPipeNameToProject(pipelineVO.getName()),
                pipelineVO.getDescription());
    }

    public GitProject getRepository(final RepositoryType repositoryType, final String path, final String token) {
        return providerService.getRepository(repositoryType, path, token);
    }

    private GitProject createTemplateRepository(final PipelineVO pipelineVO) {
        final RepositoryType repositoryType = pipelineVO.getRepositoryType();
        final String repoName = GitUtils.convertPipeNameToProject(pipelineVO.getName());

        final String templateId = Optional.ofNullable(pipelineVO.getTemplateId()).orElse(defaultTemplate);
        final TemplatesScanner templatesScanner = new TemplatesScanner(templatesDirectoryPath);
        final Template template = templatesScanner.listTemplates().get(templateId);
        Assert.notNull(template, "There is no such a template: " + templateId);

        final GitProject repository = providerService.createEmptyRepository(repositoryType, repoName,
                pipelineVO.getDescription());
        providerService.handleHook(repositoryType, repository);

        uploadFolder(repositoryType, template, repoName, repository);

        try {
            boolean fileExists = Objects.nonNull(getFileContents(repositoryType, repository,
                    DEFAULT_README, DEFAULT_BRANCH));
            if (!fileExists) {
                providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS);
            }
        } catch (UnexpectedResponseStatusException e) {
            providerService.createFile(repositoryType, repository, DEFAULT_README, README_DEFAULT_CONTENTS);
        } catch (GitClientException exception) {
            log.debug(exception.getMessage(), exception);
        }
        return repository;
    }

    private byte[] getFileContents(final RepositoryType repositoryType, final GitProject repository,
                                   final String path, final String revision) {
        Assert.isTrue(StringUtils.isNotBlank(path), "File path can't be null");
        Assert.isTrue(StringUtils.isNotBlank(revision), "Revision can't be null");
        return providerService.getFileContents(repositoryType, repository, path, revision);
    }

    private void uploadFolder(final RepositoryType repositoryType, final Template template, final String repoName,
                              final GitProject project) throws GitClientException {
        final String templateRootFolder = Paths.get(template.getDirPath()).toAbsolutePath().toString();
        if (!Paths.get(template.getDirPath()).toFile().exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(template.getDirPath()))) {
            walk.forEach(path -> uploadFile(repositoryType, repoName, project, templateRootFolder, path));
        } catch (IOException e) {
            throw new GitClientException(e.getMessage());
        }
    }

    private void uploadFile(final RepositoryType repositoryType, final String repoName, final GitProject project,
                            final String templateRootFolder, final Path path) {
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
            providerService.createFile(repositoryType, project, CONFIG_JSON, content);
            return;
        }
        providerService.createFile(repositoryType, project,
                normalizePath(relativePath.replaceAll(TEMPLATE_PLACEHOLDER, repoName)),
                getFileContent(file.getAbsolutePath()));
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
}
