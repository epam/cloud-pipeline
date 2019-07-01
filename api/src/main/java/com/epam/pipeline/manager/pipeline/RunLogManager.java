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

import java.util.Collections;
import java.util.List;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.LogsFormatter;
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
public class RunLogManager {

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private RunLogDao runLogDao;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KubernetesManager kubernetesManager;

    @Autowired
    private PreferenceManager preferenceManager;

    private RunLogManager self;

    private LogsFormatter logsFormatter = new LogsFormatter();

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
        PipelineRun run = pipelineRunDao.loadPipelineRun(runLog.getRunId());
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
    public List<RunLog> loadAllLogsByRunId(Long runId) {
        pipelineRunManager.loadPipelineRun(runId);
        return runLogDao.loadAllLogsForRun(runId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<RunLog> loadAllLogsForTask(Long runId, String taskName, String parameters) {
        PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
        if (consoleLogTask.equals(taskName)) {
            return getPodLogs(run);
        }
        String taskId = PipelineTask.buildTaskId(taskName, parameters);
        return runLogDao.loadAllLogsForTask(runId, taskId);
    }


    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksByRunIdExternal(Long runId) {
        return loadTasksByRunId(runId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineTask> loadTasksByRunId(Long runId) {
        PipelineRun run = pipelineRunManager.loadPipelineRun(runId);
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
    public String downloadLogs(PipelineRun run) {
        List<RunLog> logs = loadAllLogsByRunId(run.getId());
        StringBuilder builder = new StringBuilder();
        logs.forEach(log -> builder.append(logsFormatter.formatLog(log)).append('\n'));
        return builder.toString();
    }

    private List<RunLog> getPodLogs(PipelineRun run) {
        int logLimit = preferenceManager.getPreference(SystemPreferences.SYSTEM_LIMIT_LOGS_BYTES);
        String logText = StringUtils.isBlank(run.getPodIP()) ?
                "Node initialization in progress." : kubernetesManager.getPodLogs(run.getPodId(), logLimit);
        RunLog log = new RunLog();
        log.setRunId(run.getId());
        log.setStatus(TaskStatus.RUNNING);
        log.setDate(run.getCreatedDate());
        log.setTask(new PipelineTask(consoleLogTask));
        log.setLogText(logText);
        return Collections.singletonList(log);
    }

}
