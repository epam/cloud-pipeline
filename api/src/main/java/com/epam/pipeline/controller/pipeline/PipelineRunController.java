/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.pipeline;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.CommitRunStatusVO;
import com.epam.pipeline.controller.vo.FilterFieldVO;
import com.epam.pipeline.controller.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunFilterVO;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.controller.vo.RunCommitVO;
import com.epam.pipeline.controller.vo.RunStatusVO;
import com.epam.pipeline.controller.vo.TagsVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.controller.vo.run.OffsetPagingFilter;
import com.epam.pipeline.controller.vo.run.OffsetPagingOrder;
import com.epam.pipeline.controller.vo.run.RunChartFilterVO;
import com.epam.pipeline.entity.cluster.PipelineRunPrice;
import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.KubernetesService;
import com.epam.pipeline.entity.pipeline.KubernetesServicePort;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineRunWithTool;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskFilter;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunChartInfo;
import com.epam.pipeline.entity.pipeline.run.RunInfo;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.pipeline.run.runtime.RunRuntimeData;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;
import com.epam.pipeline.entity.run.CommitRunConditions;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.acl.run.RunApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Pipeline runs")
public class PipelineRunController extends AbstractRestController {

    private static final String RUN_ID = "runId";
    private static final String TRUE = "true";
    private static final String ENGINE_TYPE = "engineType";

    @Autowired
    private RunApiService runApiService;

    @Value("${run.prolong.redirect:/prolong.html}")
    private String prolongRedirect;

    @PostMapping(value = "/run")
    @Operation(
            summary = "Launches pipeline version execution.",
            description = "Launches pipeline version execution.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRun> runPipeline(@RequestBody PipelineStart runVo) {
        if (runVo.getPipelineId() == null) {
            return Result.success(runApiService.runCmd(runVo));
        } else {
            return Result.success(runApiService.runPipeline(runVo));
        }
    }

    @PostMapping(value = "/runConfiguration")
    @Operation(
            summary = "Launches execution according to passed configuration.",
            description = "Launches execution according to passed configuration.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineRun>> runPipeline(
            @RequestHeader(value = Constants.FIRECLOUD_TOKEN_HEADER, required = false) String refreshToken,
            @RequestBody RunConfigurationWithEntitiesVO configuration,
            @RequestParam(required = false) String expansionExpression) {
        return Result.success(runApiService.runConfiguration(refreshToken, configuration, expansionExpression));
    }

    @PostMapping(value = "/run/{runId}/log")
    @Operation(
            summary = "Adds log entry for specified pipeline run.",
            description = "Adds log entry for specified pipeline run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<RunLog> addLog(@PathVariable(value = RUN_ID) Long runId, @RequestBody RunLog log) {
        Assert.notNull(runId, "Run id is required");
        log.setRunId(runId);
        return Result.success(runApiService.saveLog(log));
    }

    @GetMapping(value = "/run/{runId}/logs")
    @Operation(
            summary = "Loads pipeline run logs.",
            description = "Loads pipeline run logs.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunLog>> loadLogs(@PathVariable(value = RUN_ID) Long runId,
                                         @RequestParam(required = false) Integer offset,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) OffsetPagingOrder order) {
        return Result.success(runApiService.loadLogsByRunId(runId, new OffsetPagingFilter(offset, limit, order)));
    }

    @GetMapping(value = "/run/{runId}/price")
    @Operation(
            summary = "Gets estimated price for pipeline run.",
            description = "Gets estimated price for pipeline run.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRunPrice> getRunEstimatedPrice(@PathVariable(value = RUN_ID) Long runId,
                                                         @RequestParam(required = false) Long regionId) {
        return Result.success(runApiService.getPipelineRunEstimatedPrice(runId, regionId));
    }

    @GetMapping(value = "/run/{runId}/logfile")
    @Operation(
            summary = "Downloads pipeline run logs as a text file.",
            description = "Downloads pipeline run logs a text file.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void exportLogs(@PathVariable(value = RUN_ID) Long runId,
                           HttpServletResponse response) throws IOException {
        writeToResponse(response, runApiService.exportLogs(runId));
    }

    @GetMapping(value = "/run/{runId}/tasks")
    @Operation(
            summary = "Loads pipeline run tasks.",
            description = "Loads pipeline run tasks.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineTask>> loadTasks(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.loadTasksByRunId(runId));
    }

    @GetMapping(value = "/run/{runId}/task")
    @Operation(
            summary = "Loads logs for a task.",
            description = "Loads logs for a task.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunLog>> loadTaskLogs(@PathVariable(value = RUN_ID) Long runId,
                                             @RequestParam(value = "taskName") String taskName,
                                             @RequestParam(value = "parameters", required = false) String parameters,
                                             @RequestParam(required = false) Integer offset,
                                             @RequestParam(required = false) Integer limit,
                                             @RequestParam(required = false) OffsetPagingOrder order) {
        return Result.success(runApiService.loadLogsForTask(runId, taskName, parameters,
                new OffsetPagingFilter(offset, limit, order)));
    }

    @PostMapping(value = "/run/{runId}/status")
    @Operation(
            summary = "Updates pipeline run status.",
            description = "Updates pipeline run status.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunStatus(@PathVariable(value = RUN_ID) Long runId,
            @RequestBody RunStatusVO statusVO) {
        return Result.success(runApiService.updatePipelineStatusIfNotFinal(runId,
                statusVO.getStatus()));
    }

    @PostMapping(value = "/run/{runId}/instance")
    @Operation(
            summary = "Updates pipeline run instance.",
            description = "Updates pipeline run instance.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunInstance(@PathVariable(value = RUN_ID) Long runId,
                                               @RequestBody RunInstance instance) {
        return Result.success(runApiService.updateRunInstance(runId, instance));
    }

    @PostMapping(value = "/run/{runId}/commit")
    @Operation(
        summary = "Commit and push docker container in which run is executing.",
        description = "Commit and push docker container in which run is executing.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> commitRun(@PathVariable(value = RUN_ID) Long runId,
        @RequestBody RunCommitVO commitVO, @RequestParam(defaultValue = TRUE) boolean checkSize) {
        return Result.success(runApiService.commitRun(runId,
            commitVO.getRegistryToCommitId(),
            commitVO.getNewImageName(),
            commitVO.isDeleteFiles(),
            commitVO.isStopPipeline(),
            checkSize)
        );
    }

    @GetMapping(value = "/run/{runId}/layers")
    @Operation(
        summary = "Gets run docker container layers count.",
        description = "Gets run docker container layers count.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Long> getContainerLayersCount(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.getContainerLayersCount(runId));
    }

    @GetMapping(value = "/run/{runId}/commit/check")
    @Operation(
            summary = "Checks if user can commit a run without a problem. " +
                    "Checks free disk space is available and size of the container is appropriate.",
            description = "Checks if user can commit a run without a problem. " +
                    "Checks free disk space is available and size of the container is appropriate.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<CommitRunConditions> getCommitRunCheckResult(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.getCommitRunCheckResult(runId));
    }

    @PostMapping(value = "/run/{runId}/commitStatus")
    @Operation(
            summary = "Update commit status of the pipeline.",
            description = "Update commit status of the pipeline.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateCommitRunStatus(@PathVariable(value = RUN_ID) Long runId,
                                         @RequestBody CommitRunStatusVO commitRunStatusVO) {
        return Result.success(runApiService.updateCommitRunStatus(runId, commitRunStatusVO.getCommitStatus()));
    }

    @PostMapping("/run/{runId}/serviceUrl")
    @Operation(
            summary = "Updates pipeline run service url.",
            description = "Updates pipeline run service url.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunServiceUrl(@PathVariable(value = RUN_ID) final Long runId,
                                                   @RequestParam(required = false) final String region,
                                                   @RequestBody final PipelineRunServiceUrlVO serviceUrlVO) {
        return Result.success(runApiService.updateServiceUrl(runId, region, serviceUrlVO));
    }

    @PostMapping(value = "/run/{runId}/prettyUrl")
    @Operation(
            summary = "Updates pipeline run pretty url.",
            description = "Updates pipeline run pretty url.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunPrettyUrl(@PathVariable(value = RUN_ID) Long runId,
                                                  @RequestParam String url) {
        return Result.success(runApiService.updatePrettyUrl(runId, url));
    }

    @GetMapping(value = "/run/prettyUrl")
    @Operation(
            summary = "Finds pipeline run by pretty url.",
            description = "Finds pipeline run by pretty url.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> getRunByPrettyUrl(@RequestParam String url) {
        return Result.success(runApiService.getRunByPrettyUrl(url));
    }

    @GetMapping(value = "/run/{runId}")
    @Operation(
            summary = "Loads pipeline run details with full list of it's restarted runs.",
            description = "Loads pipeline run details with full list of it's restarted runs.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRun> loadRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.loadPipelineRunWithRestartedRuns(runId));
    }

    @GetMapping(value = "/run/{runId}/ssh")
    @Operation(
            summary = "Return URL to access run ssh client.",
            description = "Return URL to access run ssh client.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> buildSshUrl(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(runApiService.buildSshUrl(runId));
    }

    @GetMapping(value = "/run/{runId}/fsbrowser")
    @Operation(
            summary = "Return URL to access run fsbrowser client.",
            description = "Return URL to access run fsbrowser client.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Map<String, String>> buildFSBrowserUrl(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(runApiService.buildFSBrowserUrl(runId));
    }

    @PostMapping(value = "/run/filter")
    @Operation(
            summary = "Filters pipeline runs.",
            description = "Filters pipeline runs by specified criteria.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<PipelineRun>>> filterRuns(
            @RequestBody PagingRunFilterVO filterVO,
            @RequestParam(value = "loadLinks", defaultValue = "false") boolean loadStorageLinks) {
        return Result.success(runApiService.searchPipelineRuns(filterVO, loadStorageLinks));
    }

    @PostMapping(value = "/run/filter/export")
    @Operation(
            summary = "Exports pipeline runs.",
            description = "Exports pipeline runs, filtered by specified criteria.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public void exportRuns(
            @RequestBody PagingRunFilterVO filterVO,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter,
            @RequestParam(value = "fieldDelimiter", defaultValue = "|") String fieldDelimiter,
            HttpServletResponse response) throws IOException {
        writeFileToResponse(response, runApiService.exportPipelineRuns(filterVO, delimiter, fieldDelimiter),
                "runs.csv");
    }

    @PostMapping(value = "/run/search")
    @Operation(
            summary = "Search pipeline runs.",
            description = "Search pipeline runs by specified criteria.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<PipelineRun>>> searchRuns(@RequestBody PagingRunFilterExpressionVO filterVO)
            throws WrongFilterException {
        return Result.success(runApiService.searchPipelineRunsByExpression(filterVO));
    }

    @GetMapping(value = "/run/search/keywords")
    @Operation(
            summary = "Gets pipeline runs search query keywords.",
            description = "Gets pipeline runs search query keywords.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<FilterFieldVO>> searchRunsKeywords() {
        return Result.success(runApiService.getRunSearchQueryKeywords());
    }

    @PostMapping(value = "/run/count")
    @Operation(
            summary = "Returns number of pipeline runs matching filter.",
            description = "Returns number of pipeline runs matching filter.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Integer> countRuns(@RequestBody PipelineRunFilterVO filterVO) {
        return Result.success(runApiService.countPipelineRuns(filterVO));
    }


    @GetMapping(value = "/run/defaultParameters")
    @Operation(
            summary = "Returns list of predefined run parameters.",
            description = "Returns list of predefined run parameters.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<DefaultSystemParameter>> getSystemParameters() {
        return Result.success(runApiService.getSystemParameters());
    }

    @PostMapping(value = "/run/{runId}/pause")
    @Operation(
            summary = "Pauses executing run.",
            description = "Pauses executing run.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> pauseRun(@PathVariable(value = RUN_ID) Long runId,
                                        @RequestParam(defaultValue = TRUE) boolean checkSize) {
        return Result.success(runApiService.pauseRun(runId, checkSize));
    }

    @PostMapping("/run/{runId}/resume")
    @Operation(
            summary = "Resumes paused run.",
            description = "Resumes paused run.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> resumeRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.resumeRun(runId));
    }

    @PostMapping(value = "/run/{runId}/updateSids")
    @Operation(
            summary = "Updates pipeline run sids.",
            description = "Updates pipeline run sids.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunSids(@PathVariable(value = RUN_ID) Long runId,
                                               @RequestBody List<RunSid> runSids) {
        return Result.success(runApiService.updateRunSids(runId, runSids));
    }

    @GetMapping(value = "/run/{runId}/prolongExt", consumes = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "Prolong idle pipeline run for new period. " +
                    "As a result, method will redirect user to prolong page.",
            description = "Prolong idle pipeline run for new period. " +
                    "As a result, method will redirect user to prolong page.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public String prolongIdleRunExt(@PathVariable(value = RUN_ID) Long runId) {
        runApiService.prolongIdleRun(runId);
        return String.format("redirect:%s", prolongRedirect);
    }

    @GetMapping(value = "/run/{runId}/prolong")
    @Operation(
            summary = "Prolong idle pipeline run for new period.",
            description = "Prolong idle pipeline run for new period.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result prolongIdleRun(@PathVariable(value = RUN_ID) Long runId) {
        runApiService.prolongIdleRun(runId);
        return Result.success();
    }

    @PostMapping(value = "/run/{runId}/terminate")
    @Operation(
            summary = "Terminates paused pipeline run.",
            description = "Terminates paused pipeline run cloud instance if it exists and stops the pipeline run.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun>  terminateRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.terminateRun(runId));
    }

    @PostMapping(value = "/run/{runId}/tag")
    @Operation(
            summary = "Updates tags for pipeline run.",
            description = "Updates tags for pipeline run. To remove all the tags pass empty map or null inside VO.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun>  updateRunTags(
            @PathVariable(value = RUN_ID) final Long runId,
            @RequestBody final TagsVO tagsVO,
            @RequestParam(defaultValue = "true", required = false) final boolean overwrite) {
        return Result.success(runApiService.updateTags(runId, tagsVO, overwrite));
    }

    @PostMapping(value = "/run/{runId}/disk/attach")
    @Operation(
            summary = "Creates and attaches new disk to pipeline run.",
            description = "Creates and attaches new disk to pipeline run cloud instance by the given request. " +
                    "Disk size should be specified in GB.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> attachDisk(@PathVariable(value = RUN_ID) final Long runId,
                                          @RequestBody final DiskAttachRequest request) {
        return Result.success(runApiService.attachDisk(runId, request));
    }

    @GetMapping(value = "/run/activity")
    @Operation(
        summary = "Load runs with its activity statuses.",
        description = "Load runs with its activity statuses. " +
                "Only runs that possibly could cause spending for described period will be returned.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineRun>> loadRunsActivityStats(
        @RequestParam(value = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        final LocalDateTime start,
        @RequestParam(value = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        final LocalDateTime end,
        @RequestParam(defaultValue = "false", required = false) final boolean archive) {
        return Result.success(runApiService.loadRunsActivityStats(start, end, archive));
    }

    @PostMapping(value = "/run/cmd")
    @Operation(
            summary = "Returns launch command for specified run",
            description = "Returns launch command for specified run")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<String> generateLaunchCommand(@RequestBody final PipeRunCmdStartVO runVO) {
        return Result.success(runApiService.generateLaunchCommand(runVO));
    }

    @GetMapping(value = "/runs")
    @Operation(
            summary = "Returns runs with associated tools",
            description = "Returns runs with associated tools")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineRunWithTool>> getRunsWithTools(@RequestParam final List<Long> runIds) {
        return Result.success(runApiService.getRunsWithTools(runIds));
    }

    @PostMapping(value = "/run/{runId}/kube/services")
    @Operation(
            summary = "Creates kubernetes service",
            description = "Creates kubernetes service")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<KubernetesService> createKubernetesService(@RequestParam final String serviceName,
                                                             @PathVariable final Long runId,
                                                             @RequestBody final List<KubernetesServicePort> ports) {
        return Result.success(runApiService.createKubernetesService(serviceName, runId, ports));
    }

    @GetMapping(value = "/run/{runId}/kube/services")
    @Operation(
            summary = "Returns kubernetes service description",
            description = "Returns kubernetes service description")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<KubernetesService> getKubernetesService(@PathVariable final Long runId) {
        return Result.success(runApiService.getKubernetesService(runId));
    }

    @GetMapping(value = "/edge/services")
    @Operation(
            summary = "Loads all edge services",
            description = "Loads all edge services")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<ServiceDescription>> loadEdgeServices() {
        return Result.success(runApiService.loadEdgeServices());
    }

    @GetMapping("/run/pools/{id}")
    @Operation(
            summary = "Loads runs associated with certain node pool ID",
            description = "Loads runs associated with certain node pool ID")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineRun>> loadRunsByPoolId(@PathVariable("id") final Long poolId) {
        return Result.success(runApiService.loadRunsByPoolId(poolId));
    }

    @GetMapping("/run/parents/{runId}")
    @Operation(
            summary = "Loads a compact representation of child runs of a cluster by parent run ID",
            description = "Loads a compact representation of child runs of a cluster by parent run ID")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<RunInfo>> loadRunsByParentId(@PathVariable(RUN_ID) final Long parentId) {
        return Result.success(runApiService.loadRunsByParentId(parentId));
    }

    @PostMapping("/runs/charts")
    @Operation(
            summary = "Loads active runs charts info",
            description = "Loads active runs charts info")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<RunChartInfo> loadActiveRunsCharts(@RequestBody final RunChartFilterVO filter) {
        return Result.success(runApiService.loadActiveRunsCharts(filter));
    }

    @PostMapping("/runs/archive")
    @Operation(
            summary = "Migrate runs to archive table according to owner's metadata configuration",
            description = "Migrate runs to archive table according to owner's metadata configuration")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> archiveRuns() {
        runApiService.archiveRuns();
        return Result.success(true);
    }

    @PostMapping("/runs/archive/owners")
    @Operation(
            summary = "Migrate runs to archive table for specified user (or group).",
            description = "If no 'days' specified try to find days in metadata. " +
                    "Otherwise, ignore metadata configuration.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> archiveRunsByOwner(@RequestParam final String ownerSid,
                                              @RequestParam final boolean principal,
                                              @RequestParam(required = false) final Integer days) {
        runApiService.archiveRuns(ownerSid, principal, days);
        return Result.success(true);
    }

    @PostMapping("/run/{runId}/network/limit")
    @Operation(
            summary = "Set limit boundary",
            description = "Sets a special tag for a run based on boundary param: NETWORK_LIMIT: <boundary> (Bytes/s) " +
                    "in case of enable = true, otherwise removes the tag.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Boolean> setLimitBoundary(@PathVariable(value = RUN_ID) final Long runId,
                                            @RequestParam(defaultValue = "true") final Boolean enable,
                                            @RequestParam(required = false) final Integer boundary) {
        runApiService.setLimitBoundary(runId, enable, boundary);
        return Result.success();
    }

    @PostMapping("/run/{runId}/runtime/data")
    @Operation(
            summary = "Get run data for the specific run by ID and data type",
            description = "Get run data for the specific run by ID and data type")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<RunRuntimeData> getPipelineRunData(
            @PathVariable(value = RUN_ID) final Long runId,
            @RequestParam final RunSyncRuntimeDataType type,
            @RequestBody(required = false) final Map<String, String> parameters) {
        return Result.success(runApiService.getPipelineRunRuntimeData(runId, type, parameters));
    }

    @PostMapping("/run/{runId}/engine/tasks")
    @Operation(
            summary = "Consumes engine task events for run",
            description = "Consumes engine task events for run")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Integer> consumeRunEngineTaskEvents(@PathVariable(value = RUN_ID) final Long runId,
                                                      @RequestBody final List<EngineRunTask> tasks) {
        return Result.success(runApiService.consumeRunEngineTaskEvents(runId, tasks));
    }

    @GetMapping("run/{runId}/engine/{engineType}/tasks/stats")
    @Operation(
            summary = "Loads engine task statistics for run and engine type",
            description = "Loads engine task statistics for run and engine type")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<Map<String, Map<EngineTaskStatus, Long>>> loadEngineRunTasksStats(
            @PathVariable(value = RUN_ID) final Long runId,
            @PathVariable(value = ENGINE_TYPE) final EngineType engineType) {
        return Result.success(runApiService.loadEngineRunTasksStats(runId, engineType));
    }

    @PostMapping("run/{runId}/engine/{engineType}/tasks/filter")
    @Operation(
            summary = "Loads engine task for run with applied filters",
            description = "Loads engine task for run with applied filters")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<PagedResult<List<EngineRunTask>>> filterEngineRunTasks(
            @PathVariable(value = RUN_ID) final Long runId,
            @PathVariable(value = ENGINE_TYPE) final EngineType engineType,
            @RequestBody final EngineRunTaskFilter filter) {
        return Result.success(runApiService.filterEngineRunTasks(runId, engineType, filter));
    }

    @PostMapping("/run/{runId}/result")
    @Operation(
            summary = "Adds set of run result objects for the specified run",
            description = "Adds set of run result objects for the specified run")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result addPipelineRunResults(@PathVariable(value = RUN_ID) final Long runId,
                                        @RequestBody final List<PipelineRunResult> results) {
        runApiService.addPipelineRunResults(runId, results);
        return Result.success();
    }

    @GetMapping("/run/{runId}/result")
    @Operation(
            summary = "Loads run result objects for the specified run",
            description = "Loads run result objects for the specified run")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineRunResult>> loadPipelineRunResults(@PathVariable(value = RUN_ID) final Long runId) {
        return Result.success(runApiService.loadPipelineRunResultsForRun(runId));
    }
}
