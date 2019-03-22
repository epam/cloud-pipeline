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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.entity.configuration.DtsRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.entity.dts.SubmissionParameter;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.ResolvedConfiguration;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.DtsExecutionPreferences;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.DtsRequestException;
import com.epam.pipeline.manager.dts.DtsSubmissionManager;
import com.epam.pipeline.manager.execution.CommandBuilder;
import com.epam.pipeline.manager.execution.PipelineLauncher;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.pipeline.ParameterMapper;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.PipelineStringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DtsRunner implements ExecutionRunner<DtsRunConfigurationEntry> {

    private final ParameterMapper parameterMapper;
    private final PipelineRunManager pipelineRunManager;
    private final PipelineManager pipelineManager;
    private final DtsSubmissionManager submissionManager;
    private final PipelineLauncher pipelineLauncher;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final CommandBuilder commandBuilder;
    private final ToolManager toolManager;

    @Override
    public List<PipelineRun> runAnalysis(
            AnalysisConfiguration<DtsRunConfigurationEntry> configuration) {
        checkClusterAvailability(configuration.getEntries());
        return parameterMapper
                .resolveConfigurations(configuration)
                .stream()
                .map(conf -> runConfiguration(configuration.getConfigurationId(), configuration.getEntries(), conf))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private void checkClusterAvailability(List<DtsRunConfigurationEntry> entries) {
        Map<Long, DtsClusterConfiguration> configurations = entries.stream()
                .map(DtsRunConfigurationEntry::getDtsId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        submissionManager::getClusterConfiguration));
        entries.forEach(entry -> Assert.state(
                checkFreeSlots(entry.getCoresNumber(), configurations.get(entry.getDtsId())),
                String.format("DTS Service cannot provide %d cores", entry.getCoresNumber())));

    }

    private boolean checkFreeSlots(Integer coresNumber, DtsClusterConfiguration dtsClusterConfiguration) {
        if (coresNumber == null || coresNumber <= 0) {
            return true;
        }
        return ListUtils.emptyIfNull(dtsClusterConfiguration.getNodes())
                .stream()
                .anyMatch(node -> node.getSlotsTotal() >= coresNumber);
    }

    private List<PipelineRun> runConfiguration(Long configurationId,
                                               List<DtsRunConfigurationEntry> entries,
                                               ResolvedConfiguration configuration) {
        return entries
                .stream()
                .map(entry -> runEntry(entry, configurationId,
                        configuration.getAllAssociatedIds(), configuration.getConfiguration(entry.getName())))
                .collect(Collectors.toList());
    }

    private PipelineRun runEntry(DtsRunConfigurationEntry entry,
                                 Long configurationId,
                                 List<Long> entitiesIds,
                                 PipelineConfiguration configuration) {

        Pipeline pipeline = entry.getPipelineId() == null ? null : pipelineManager.load(entry.getPipelineId());
        PipelineRun run = pipelineRunManager.createPipelineRun(
                entry.getPipelineVersion(), configuration, pipeline, null, entitiesIds, configurationId);

        run.setConfigName(entry.getConfigName());
        run.setDockerImage(toolManager.getExternalToolName(run.getDockerImage()));
        run.setExecutionPreferences(DtsExecutionPreferences.builder()
                .dtsId(entry.getDtsId())
                .coresNumber(entry.getCoresNumber())
                .build());

        //TODO: check do we need cloud specific envvars for DTS
        Map<SystemParams, String> systemParams = pipelineLauncher.matchCommonParams(run,
                preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL),
                configuration.getGitCredentials());

        systemParams.put(SystemParams.DISTRIBUTION_URL,
                preferenceManager.getPreference(SystemPreferences.DTS_DISTRIBUTION_URL));

        GitCredentials gitCredentials = configuration.getGitCredentials();

        String gitCloneUrl = gitCredentials == null ? run.getRepository() : gitCredentials.getUrl();

        String pipelineCommand = commandBuilder.build(configuration, systemParams);
        run.setActualCmd(pipelineCommand);

        String launchCmd = preferenceManager.getPreference(SystemPreferences.DTS_LAUNCH_CMD_TEMPLATE);
        String launchScriptUrl = preferenceManager.getPreference(SystemPreferences.DTS_LAUNCH_URL);

        String fullCmd = String.format(launchCmd, launchScriptUrl,
                launchScriptUrl, gitCloneUrl, run.getRevisionName(), pipelineCommand);

        pipelineRunManager.save(run);
        DtsSubmission submission = buildSubmission(
                run, configuration, entry.getCoresNumber(), systemParams, fullCmd);
        log.debug("Creating DTS submission");
        try {
            DtsSubmission scheduled = submissionManager.createSubmission(entry.getDtsId(), submission);
            if (scheduled.getState().getStatus() != TaskStatus.RUNNING) {
                return failRun(run, String.format("Submission failed to start: %s", scheduled.getState().getReason()));
            }
            log.debug("Successfully scheduled submission on DTS");
            return run;
        } catch (DtsRequestException e) {
            return failRun(run, e.getMessage());
        }
    }

    private PipelineRun failRun(PipelineRun run, String error) {
        log.error("Failed to start submission on DTS: {}", error);
        //TODO: save error to logs
        run.setStatus(TaskStatus.FAILURE);
        run.setEndDate(DateUtils.now());
        return pipelineRunManager.updatePipelineStatus(run);
    }

    private DtsSubmission buildSubmission(PipelineRun run,
                                          PipelineConfiguration configuration,
                                          Integer coresNumber,
                                          Map<SystemParams, String> systemParams,
                                          String fullCmd) {
        return DtsSubmission.builder()
                .runId(run.getId())
                .runName(run.getPodId())
                .jobName(buildJobName(run.getPodId()))
                .api(preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL))
                .token(authManager.issueTokenForCurrentUser().getToken())
                .cores(coresNumber)
                .dockerImage(run.getDockerImage())
                .command(fullCmd)
                .parameters(buildParameters(run, configuration, systemParams))
                .build();
    }

    private String buildJobName(final String podId) {
        return PipelineStringUtils.convertToAlphanumericWithDashes(
                String.join(PipelineStringUtils.DASH,
                        preferenceManager.getPreference(SystemPreferences.UI_DEPLOYMENT_NAME),
                        authManager.getAuthorizedUser(), podId));
    }

    private List<SubmissionParameter> buildParameters(PipelineRun run,
                                                      PipelineConfiguration configuration,
                                                      Map<SystemParams, String> systemParams) {
        List<SubmissionParameter> parameters = new ArrayList<>();
        MapUtils.emptyIfNull(configuration.getParameters()).forEach((name, param) -> {
            SubmissionParameter.SubmissionParameterBuilder builder = SubmissionParameter.builder().name(name);
            if (param != null) {
                builder.value(param.getValue()).type(param.getType());
            }
            parameters.add(builder.build());
        });
        MapUtils.emptyIfNull(systemParams).forEach((param, value) ->
                parameters.add(SubmissionParameter.builder()
                .name(param.getEnvName())
                .value(value)
                .build()));
        MapUtils.emptyIfNull(run.getEnvVars()).forEach((param, value) ->
                parameters.add(SubmissionParameter.builder()
                        .name(param)
                        .value(value)
                        .build()));
        parameters.add(SubmissionParameter.builder()
                .name(PipelineConfiguration.EXECUTION_ENVIRONMENT)
                .value(ExecutionEnvironment.DTS.name())
                .build());
        return parameters;
    }
}
