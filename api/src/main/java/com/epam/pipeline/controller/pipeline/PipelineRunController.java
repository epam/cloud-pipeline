/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.cluster.PipelineRunPrice;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.filter.WrongFilterException;
import com.epam.pipeline.acl.run.RunApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@Api(value = "Pipeline runs")
public class PipelineRunController extends AbstractRestController {

    private static final String RUN_ID = "runId";
    private static final String TRUE = "true";

    @Autowired
    private RunApiService runApiService;

    @Value("${run.prolong.redirect:/prolong.html}")
    private String prolongRedirect;

    @RequestMapping(value = "/run", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Launches pipeline version execution.",
            notes = "Launches pipeline version execution.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRun> runPipeline(@RequestBody PipelineStart runVo) {
        if (runVo.getPipelineId() == null) {
            return Result.success(runApiService.runCmd(runVo));
        } else {
            return Result.success(runApiService.runPipeline(runVo));
        }
    }

    @RequestMapping(value = "/runConfiguration", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Launches execution according to passed configuration.",
            notes = "Launches execution according to passed configuration.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<PipelineRun>> runPipeline(
            @RequestHeader(value = Constants.FIRECLOUD_TOKEN_HEADER, required = false) String refreshToken,
            @RequestBody RunConfigurationWithEntitiesVO configuration,
            @RequestParam(required = false) String expansionExpression) {
        return Result.success(runApiService.runConfiguration(refreshToken, configuration, expansionExpression));
    }

    @RequestMapping(value = "/run/{runId}/log", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Adds log entry for specified pipeline run.",
            notes = "Adds log entry for specified pipeline run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<RunLog> addLog(@PathVariable(value = RUN_ID) Long runId, @RequestBody RunLog log) {
        Assert.notNull(runId, "Run id is required");
        log.setRunId(runId);
        return Result.success(runApiService.saveLog(log));
    }

    @RequestMapping(value = "/run/{runId}/logs", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads pipeline run logs.",
            notes = "Loads pipeline run logs.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunLog>> loadLogs(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.loadAllLogsByRunId(runId));
    }

    @RequestMapping(value = "/run/{runId}/price", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets estimated price for pipeline run.",
            notes = "Gets estimated price for pipeline run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRunPrice> getRunEstimatedPrice(@PathVariable(value = RUN_ID) Long runId,
                                                         @RequestParam(required = false) Long regionId) {
        return Result.success(runApiService.getPipelineRunEstimatedPrice(runId, regionId));
    }

    @RequestMapping(value = "/run/{runId}/logfile", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Downloads pipeline run logs as a text file.",
            notes = "Downloads pipeline run logs a text file.",
            produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public void downloadLogs(@PathVariable(value = RUN_ID) Long runId, HttpServletResponse response)
            throws IOException {
        PipelineRun run = runApiService.loadPipelineRun(runId);
        byte[] logs = runApiService.downloadLogs(runId).getBytes(Charset.defaultCharset());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        String pipelineName = !StringUtils.isEmpty(run.getPipelineName())
                ? run.getPipelineName() : PipelineRun.DEFAULT_PIPELINE_NAME;
        String pipelineVersion = !StringUtils.isEmpty(run.getVersion()) ? run.getVersion() : "";
        response.setHeader("Content-Disposition", String.format("attachment;filename=%s_%s_%d.log",
                        pipelineName, pipelineVersion, run.getId()));
        response.setContentLengthLong(logs.length);
        try (ServletOutputStream stream = response.getOutputStream()) {
            stream.write(logs);
            stream.flush();
        }
    }

    @RequestMapping(value = "/run/{runId}/tasks", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads pipeline run tasks.",
            notes = "Loads pipeline run tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineTask>> loadTasks(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.loadTasksByRunId(runId));
    }

    @RequestMapping(value = "/run/{runId}/task", method= RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads logs for a task.",
            notes = "Loads logs for a task.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<RunLog>> loadTaskLogs(@PathVariable(value = RUN_ID) Long runId,
            @RequestParam(value = "taskName") String taskName,
            @RequestParam(value = "parameters", required = false) String parameters) {
        return Result.success(runApiService.loadAllLogsForTask(runId, taskName, parameters));
    }

    @RequestMapping(value = "/run/{runId}/status", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline run status.",
            notes = "Updates pipeline run status.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunStatus(@PathVariable(value = RUN_ID) Long runId,
            @RequestBody RunStatusVO statusVO) {
        return Result.success(runApiService.updatePipelineStatusIfNotFinal(runId,
                statusVO.getStatus()));
    }

    @RequestMapping(value = "/run/{runId}/instance", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline run instance.",
            notes = "Updates pipeline run instance.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunInstance(@PathVariable(value = RUN_ID) Long runId,
                                               @RequestBody RunInstance instance) {
        return Result.success(runApiService.updateRunInstance(runId, instance));
    }

    @RequestMapping(value = "/run/{runId}/commit", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
        value = "Commit and push docker container in which run is executing.",
        notes = "Commit and push docker container in which run is executing.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
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

    @GetMapping(value = "/run/{runId}/commit/check")
    @ResponseBody
    @ApiOperation(
            value = "Checks if free disk space is available.",
            notes = "Checks if free disk space is available.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<Boolean> checkFreeSpaceAvailable(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.checkFreeSpaceAvailable(runId));
    }

    @RequestMapping(value = "/run/{runId}/commitStatus", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Update commit status of the pipeline.",
            notes = "Update commit status of the pipeline.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateCommitRunStatus(@PathVariable(value = RUN_ID) Long runId,
                                         @RequestBody CommitRunStatusVO commitRunStatusVO) {
        return Result.success(runApiService.updateCommitRunStatus(runId, commitRunStatusVO.getCommitStatus()));
    }

    @RequestMapping(value = "/run/{runId}/serviceUrl", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline run service url.",
            notes = "Updates pipeline run service url.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunServiceUrl(@PathVariable(value = RUN_ID) Long runId,
                                               @RequestBody PipelineRunServiceUrlVO serviceUrlVO) {
        return Result.success(runApiService.updateServiceUrl(runId, serviceUrlVO));
    }

    @RequestMapping(value = "/run/{runId}/prettyUrl", method= RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline run pretty url.",
            notes = "Updates pipeline run pretty url.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunServiceUrl(@PathVariable(value = RUN_ID) Long runId,
                                                   @RequestParam String url) {
        return Result.success(runApiService.updatePrettyUrl(runId, url));
    }

    @RequestMapping(value = "/run/{runId}", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Loads pipeline run details with full list of it's restarted runs.",
            notes = "Loads pipeline run details with full list of it's restarted runs.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PipelineRun> loadRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.loadPipelineRunWithRestartedRuns(runId));
    }

    @RequestMapping(value = "/run/{runId}/ssh", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Return URL to access run ssh client.",
            notes = "Return URL to access run ssh client.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<String> buildSshUrl(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.buildSshUrl(runId));
    }

    @RequestMapping(value = "/run/{runId}/fsbrowser", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Return URL to access run fsbrowser client.",
            notes = "Return URL to access run fsbrowser client.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<String> buildFSBrowserUrl(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.buildFSBrowserUrl(runId));
    }

    @RequestMapping(value = "/run/filter", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Filters pipeline runs.",
            notes = "Filters pipeline runs by specified criteria.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<PipelineRun>>> filterRuns(
            @RequestBody PagingRunFilterVO filterVO,
            @RequestParam(value = "loadLinks", defaultValue = "false") boolean loadStorageLinks) {
        return Result.success(runApiService.searchPipelineRuns(filterVO, loadStorageLinks));
    }

    @RequestMapping(value = "/run/search", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Search pipeline runs.",
            notes = "Search pipeline runs by specified criteria.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<PipelineRun>>> searchRuns(@RequestBody PagingRunFilterExpressionVO filterVO)
            throws WrongFilterException {
        return Result.success(runApiService.searchPipelineRunsByExpression(filterVO));
    }

    @RequestMapping(value = "/run/search/keywords", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Gets pipeline runs search query keywords.",
            notes = "Gets pipeline runs search query keywords.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<FilterFieldVO>> searchRunsKeywords() {
        return Result.success(runApiService.getRunSearchQueryKeywords());
    }

    @RequestMapping(value = "/run/count", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Returns number of pipeline runs matching filter.",
            notes = "Returns number of pipeline runs matching filter.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Integer> countRuns(@RequestBody PipelineRunFilterVO filterVO) {
        return Result.success(runApiService.countPipelineRuns(filterVO));
    }


    @RequestMapping(value = "/run/defaultParameters", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns list of predefined run parameters.",
            notes = "Returns list of predefined run parameters.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<DefaultSystemParameter>> getSystemParameters() {
        return Result.success(runApiService.getSystemParameters());
    }

    @PostMapping(value = "/run/{runId}/pause")
    @ResponseBody
    @ApiOperation(
            value = "Pauses executing run.",
            notes = "Pauses executing run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> pauseRun(@PathVariable(value = RUN_ID) Long runId,
                                        @RequestParam(defaultValue = TRUE) boolean checkSize) {
        return Result.success(runApiService.pauseRun(runId, checkSize));
    }

    @PostMapping("/run/{runId}/resume")
    @ResponseBody
    @ApiOperation(
            value = "Resumes paused run.",
            notes = "Resumes paused run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> resumeRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.resumeRun(runId));
    }

    @PostMapping(value = "/run/{runId}/updateSids")
    @ResponseBody
    @ApiOperation(
            value = "Updates pipeline run sids.",
            notes = "Updates pipeline run sids.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> updateRunSids(@PathVariable(value = RUN_ID) Long runId,
                                               @RequestBody List<RunSid> runSids) {
        return Result.success(runApiService.updateRunSids(runId, runSids));
    }

    @GetMapping(value = "/run/{runId}/prolongExt", consumes = MediaType.TEXT_HTML_VALUE)
    @ApiOperation(
            value = "Prolong idle pipeline run for new period. " +
                    "As a result, method will redirect user to prolong page.",
            notes = "Prolong idle pipeline run for new period. " +
                    "As a result, method will redirect user to prolong page.",
            produces = MediaType.TEXT_HTML_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public String prolongIdleRunExt(@PathVariable(value = RUN_ID) Long runId) {
        runApiService.prolongIdleRun(runId);
        return String.format("redirect:%s", prolongRedirect);
    }

    @GetMapping(value = "/run/{runId}/prolong")
    @ResponseBody
    @ApiOperation(
            value = "Prolong idle pipeline run for new period.",
            notes = "Prolong idle pipeline run for new period.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result prolongIdleRun(@PathVariable(value = RUN_ID) Long runId) {
        runApiService.prolongIdleRun(runId);
        return Result.success();
    }

    @PostMapping(value = "/run/{runId}/terminate")
    @ResponseBody
    @ApiOperation(
            value = "Terminates paused pipeline run.",
            notes = "Terminates paused pipeline run cloud instance if it exists and stops the pipeline run.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun>  terminateRun(@PathVariable(value = RUN_ID) Long runId) {
        return Result.success(runApiService.terminateRun(runId));
    }

    @PostMapping(value = "/run/{runId}/tag")
    @ResponseBody
    @ApiOperation(
            value = "Updates tags for pipeline run.",
            notes = "Updates tags for pipeline run. To remove all the tags pass empty map or null inside VO.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun>  updateRunTags(@PathVariable(value = RUN_ID) final Long runId,
                                              @RequestBody final TagsVO tagsVO) {
        return Result.success(runApiService.updateTags(runId, tagsVO));
    }

    @PostMapping(value = "/run/{runId}/disk/attach")
    @ResponseBody
    @ApiOperation(
            value = "Creates and attaches new disk to pipeline run.",
            notes = "Creates and attaches new disk to pipeline run cloud instance by the given request. " +
                    "Disk size should be specified in GB.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<PipelineRun> attachDisk(@PathVariable(value = RUN_ID) final Long runId,
                                          @RequestBody final DiskAttachRequest request) {
        return Result.success(runApiService.attachDisk(runId, request));
    }

    @GetMapping(value = "/run/activity")
    @ResponseBody
    @ApiOperation(
        value = "Load runs with its activity statuses.",
        notes = "Load runs with its activity statuses. " +
                "Only runs that possibly could cause spending for described period will be returned.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<List<PipelineRun>> loadRunsActivityStats(@RequestParam(value = "from") final LocalDateTime start,
                                                           @RequestParam(value = "to") final LocalDateTime end) {
        return Result.success(runApiService.loadRunsActivityStats(start, end));
    }

    @PostMapping(value = "/run/cmd")
    @ResponseBody
    @ApiOperation(
            value = "Returns launch command for specified run",
            notes = "Returns launch command for specified run",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)})
    public Result<String> generateLaunchCommand(@RequestBody final PipeRunCmdStartVO runVO) {
        return Result.success(runApiService.generateLaunchCommand(runVO));
    }
}
