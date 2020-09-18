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

package com.epam.pipeline.dts.submission.service.execution.impl;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.common.service.FileService;
import com.epam.pipeline.dts.submission.configuration.SubmissionPreference;
import com.epam.pipeline.dts.submission.exception.SubmissionException;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionState;
import com.epam.pipeline.dts.submission.model.execution.SubmissionStatus;
import com.epam.pipeline.dts.submission.model.execution.SubmissionTemplate;
import com.epam.pipeline.dts.submission.service.execution.SubmissionConverter;
import com.epam.pipeline.dts.submission.service.execution.SubmissionScheduler;
import com.epam.pipeline.dts.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class SubmissionSchedulerImpl implements SubmissionScheduler {

    private final Path workDir;
    private final Path qsubTemplate;
    private final SubmissionPreference preference;
    private final FileService fileService;
    private final TemplateEngine templateEngine;
    private final CmdExecutor cmdExecutor;
    private final SubmissionConverter converter;

    public SubmissionSchedulerImpl(final SubmissionPreference preference,
                                   final FileService fileService,
                                   final TemplateEngine templateEngine,
                                   final CmdExecutor submissionCmdExecutor,
                                   final SubmissionConverter converter) {
        this.workDir = fileService.getOrCreateFolder(preference.getWorkdir());
        this.qsubTemplate = fileService.getLocalFile(preference.getQsubTemplate());
        this.preference = preference;
        this.fileService = fileService;
        this.templateEngine = templateEngine;
        this.cmdExecutor = submissionCmdExecutor;
        this.converter = converter;
    }

    @Override
    public String schedule(final Submission submission) throws SubmissionException {
        final Path submissionFolder = fileService.createFolder(getSubmissionFolder(submission.getId()));
        final Path logFile = getLogFile(submissionFolder);
        final Path scriptFile = buildScriptFile(submissionFolder, submission,
                getDoneFile(submissionFolder), logFile);
        final String command = buildSubmitCommand(preference.getQsubCommand(), scriptFile,
                logFile, submission);
        try {
            log.debug("Submission {} starting with cmd: {}", submission.getId(), command);
            String output = cmdExecutor.executeCommand(
                    command, Collections.emptyMap(), submissionFolder.toFile());
            return readJobId(output);
        } catch (CmdExecutionException e) {
            log.error("Execution of submission script failed: {}", e.getMessage());
            throw new SubmissionException(e.getMessage(), e);
        }
    }

    @Override
    public SubmissionState getState(final Long submissionId) throws SubmissionException {
        final Path submissionFolder = validateSubmissionFolder(submissionId);
        final Path tokenFile = getDoneFile(submissionFolder);
        if (!Utils.fileExists(tokenFile)) {
            return SubmissionState.builder().status(SubmissionStatus.RUNNING).build();
        }
        try {
            final String content = fileService.readFileContent(tokenFile);
            return buildStateFromContent(content);
        } catch (IOException e) {
            return SubmissionState.builder()
                    .status(SubmissionStatus.FAILURE)
                    .reason(String.format("Failed to read token %s with error %s",
                            submissionFolder.toString(), e.getMessage()))
                    .build();
        }

    }

    @Override
    public String getLogs(Long submissionId) throws SubmissionException {
        final Path submissionFolder = validateSubmissionFolder(submissionId);
        final Path logFile = getLogFile(submissionFolder);
        if (!Utils.fileExists(logFile)) {
            return StringUtils.EMPTY;
        }
        try {
            return fileService.readFileContent(logFile);
        } catch (IOException e) {
            log.error("Failed to read log file {}, error {} ", logFile, e.getMessage(), e);
            return StringUtils.EMPTY;
        }
    }

    private Path validateSubmissionFolder(Long submissionId) throws SubmissionException {
        final Path submissionFolder = getSubmissionFolder(submissionId);
        if (!submissionFolder.toFile().exists()) {
            throw new SubmissionException(
                    String.format("Submission folder doesn't exist %s", submissionFolder.toString()));
        }
        return submissionFolder;
    }

    private SubmissionState buildStateFromContent(final String content) {
        final SubmissionState.SubmissionStateBuilder builder = SubmissionState.builder();
        if (StringUtils.isBlank(content) || !NumberUtils.isDigits(content.trim())) {
            builder.status(SubmissionStatus.FAILURE);
            builder.reason(String.format("Token contains unexpected content: %s", content));
        } else {
            final int exitCode = Integer.parseInt(content.trim());
            builder.status(exitCode == 0 ? SubmissionStatus.SUCCESS : SubmissionStatus.FAILURE);
            builder.reason(String.format("Submission finished with exit code: %d", exitCode));
        }
        return builder.build();
    }

    private String readJobId(final String qsubOutput) {
        log.debug("Parsing qsub output: {}", qsubOutput);
        return StringUtils.defaultIfEmpty(qsubOutput, StringUtils.EMPTY).trim();
    }

    private Path getSubmissionFolder(final Long submissionId) {
        return workDir.resolve(String.valueOf(submissionId));
    }

    private Path buildScriptFile(final Path submissionFolder,
                                 final Submission submission,
                                 final Path doneToken,
                                 final Path logFile) throws SubmissionException {
        final SubmissionTemplate template = converter.convertToTemplate(submission);
        final String scriptContent = buildScriptText(template, doneToken, logFile);
        final Path script = submissionFolder.resolve(qsubTemplate.getFileName());
        try {
            fileService.writeToFile(script, scriptContent);
            return script;
        } catch (IOException e) {
            log.error("Failed to create submission script: {}, error {}", script, e.getMessage());
            throw new SubmissionException(e.getMessage(), e);
        }
    }

    private String buildSubmitCommand(final String qsubTemplate, final Path scriptFile,
                                      final Path logFile, final Submission submission) {
        final Integer actualCores = Optional.ofNullable(submission.getCores()).orElse(preference.getCoresNumber());
        return new SubmissionCommandBuilder()
                .withScriptFile(scriptFile)
                .withLogFile(logFile)
                .withCores(actualCores)
                .withJobName(submission.getJobName())
                .build(qsubTemplate);
    }

    private String buildScriptText(final SubmissionTemplate template, final Path doneToken, final Path logFile) {
        final Context ctx = new Context(Locale.getDefault());
        ctx.setVariable("template", template);
        ctx.setVariable("logFile", logFile.toAbsolutePath().toString());
        ctx.setVariable("doneToken", doneToken.toAbsolutePath().toString());
        return templateEngine.process(qsubTemplate.toString(), ctx);
    }

    private Path getLogFile(Path submissionFolder) {
        return getSubmissionFile(submissionFolder, preference.getLogFile());
    }

    private Path getDoneFile(Path submissionFolder) {
        return getSubmissionFile(submissionFolder, preference.getDoneFile());
    }

    private Path getSubmissionFile(Path submissionFolder, String filename) {
        return submissionFolder.resolve(filename);
    }
}
