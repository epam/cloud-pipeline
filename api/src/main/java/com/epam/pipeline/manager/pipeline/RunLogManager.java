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

package com.epam.pipeline.manager.pipeline;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.ResultWriter;
import com.epam.pipeline.controller.vo.run.OffsetPagingFilter;
import com.epam.pipeline.controller.vo.run.OffsetPagingOrder;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class RunLogManager {

    @Autowired
    private RunLogDao runLogDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private PipelineRunCRUDService runCRUDService;

    @Autowired
    private RunLogExporter runLogExporter;

    private RunLogManager self;

    @Value("${runs.console.log.task:Console}")
    private String consoleLogTask;

    @PostConstruct
    public void init() {
        self = applicationContext.getBean(RunLogManager.class);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public RunLog saveLog(final RunLog runLog) {
        Assert.notNull(runLog.getRunId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "runId", RunLog.class.getSimpleName()));
        Assert.notNull(runLog.getDate(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "date", RunLog.class.getSimpleName()));
        Assert.notNull(runLog.getStatus(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "status", RunLog.class.getSimpleName()));
        PipelineRun run = runCRUDService.loadRunById(runLog.getRunId());
        Assert.notNull(run,
                messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_NOT_FOUND, runLog.getRunId()));
        if (!StringUtils.isEmpty(runLog.getLogText())) {
            runLog.setLogText(runLog.getLogText().replaceAll("\\u0000", ""));
        }
        // Check previous status, it may differ from pod status as error may occur during
        // results upload to s3
        TaskStatus statusToSave = runLog.getStatus();
        if (!StringUtils.isEmpty(runLog.getTaskName())) {
            PipelineTask task = self.loadPreviousTaskStatus(run, runLog);
            if (task != null && task.getStatus().isFinal()) {
                statusToSave = task.getStatus();
            }
        }
        // if task reports its non final status after the whole run is already finished, overwrite
        // this status
        if (!statusToSave.isFinal() && run.getStatus().isFinal()) {
            statusToSave = run.getStatus();
        }

        runLog.setStatus(statusToSave);

        runLogDao.createRunLog(runLog);
        return runLog;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public PipelineTask loadPreviousTaskStatus(PipelineRun pipelineRun, RunLog runLog) {
        return runLogDao.loadTaskStatus(pipelineRun.getId(), runLog.getTaskName());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadLogsByRunId(Long runId, OffsetPagingFilter filter) {
        runCRUDService.loadRunById(runId);
        return runLogDao.loadLogsForRun(runId, normalize(filter));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadLogsForTask(Long runId, String taskName) {
        return loadLogsForTask(runId, taskName, null, OffsetPagingFilter.builder().build());
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadLogsForTask(Long runId, String taskName, String parameters,
                                        OffsetPagingFilter filter) {
        PipelineRun run = runCRUDService.loadRunById(runId);
        if (consoleLogTask.equals(taskName)) {
            return getPodLogs(run, normalize(filter));
        }
        String taskId = PipelineTask.buildTaskId(taskName, parameters);
        return runLogDao.loadLogsForTask(runId, taskId, normalize(filter));
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksByRunIdExternal(Long runId) {
        return loadTasksByRunId(runId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksByRunId(Long runId) {
        PipelineRun run = runCRUDService.loadRunById(runId);
        List<PipelineTask> tasks = runLogDao.loadTasksForRun(runId);
        tasks.forEach(task -> {
            if (run.getStatus().isFinal() && !task.getStatus().isFinal()) {
                task.setStatus(run.getStatus());
                if (task.getFinished() == null) {
                    task.setFinished(run.getEndDate());
                }
            }
            if ((StringUtils.isEmpty(task.getInstance()) || task.getInstance().equals(run.getPodId()))
                    && !task.getStatus().isFinal()) {
                task.setStatus(run.getStatus());
            }
            if (task.getStarted() == null &&
                    (StringUtils.isEmpty(task.getInstance()) || task.getInstance().equals(run.getPodId())
                            || task.getStatus().isFinal())) {
                task.setStarted(task.getCreated());
            }
            if (!task.getStatus().isFinal()) {
                task.setFinished(null);
            }
            if (task.getName().equals(run.getPipelineName())) {
                task.setCreated(run.getStartDate());
                task.setStarted(tasks.get(0).getCreated());
            }
        });
        if (!run.getStatus().isFinal()) {
            //for running tasks we add dummy 'console' task to get online logs from Kubernetes
            PipelineTask consoleTask = new PipelineTask();
            consoleTask.setName(consoleLogTask);
            consoleTask.setStatus(TaskStatus.RUNNING);
            consoleTask.setStarted(run.getStartDate());
            tasks.add(consoleTask);
        }
        return tasks;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public String loadTaskByInstance(String pod, PipelineRun pipelineRun) {
        List<PipelineTask> tasks = runLogDao.loadTaskByInstance(pipelineRun.getId(), pod);
        if (tasks == null || tasks.isEmpty()) {
            return null;
        } else {
            return PipelineTask.buildTaskId(tasks.get(0).getName(), tasks.get(0).getParameters());
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public ResultWriter exportLogs(Long runId) {
        PipelineRun run = runCRUDService.loadRunById(runId);
        return ResultWriter.unchecked(getTitle(run), out -> exportLogs(run, out));
    }

    private String getTitle(final PipelineRun run) {
        String pipelineName = Optional.ofNullable(run.getPipelineName())
                .filter(StringUtils::isNotBlank)
                .orElse(PipelineRun.DEFAULT_PIPELINE_NAME);
        String pipelineVersion = Optional.ofNullable(run.getVersion())
                .filter(StringUtils::isNotBlank)
                .orElse(StringUtils.EMPTY);
        return String.format("%s_%s_%d.log", pipelineName, pipelineVersion, run.getId());
    }

    private void exportLogs(PipelineRun run, OutputStream out) {
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out);
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)) {
            runLogExporter.export(run, bufferedWriter);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<RunLog> getPodLogs(PipelineRun run, OffsetPagingFilter filter) {
        String logText = Optional.ofNullable(run.getPodId())
                .filter(StringUtils::isNotBlank)
                .map(podIp -> kubernetesManager.getPodLogs(podIp, filter.getLimit()))
                .orElse("Node initialization in progress.");
        RunLog log = RunLog.builder()
                .runId(run.getId())
                .status(TaskStatus.RUNNING)
                .date(run.getCreatedDate())
                .task(new PipelineTask(consoleLogTask))
                .logText(logText)
                .build();
        return Collections.singletonList(log);
    }

    private OffsetPagingFilter normalize(OffsetPagingFilter filter) {
        return OffsetPagingFilter.builder()
                .offset(Optional.ofNullable(filter.getOffset()).orElse(0))
                .limit(Optional.ofNullable(filter.getLimit()).orElseGet(this::getRunLogDefaultLimit))
                .order(Optional.ofNullable(filter.getOrder()).orElse(OffsetPagingOrder.DESC))
                .build();
    }

    private int getRunLogDefaultLimit() {
        return preferenceManager.findPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES)
                .orElseGet(SystemPreferences.SYSTEM_LIMIT_LOG_LINES::getDefaultValue);
    }
}
