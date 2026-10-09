/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller.async;

import com.epam.pipeline.acl.async.AsyncTaskApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskStatusVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/async-task")
public class AsyncTaskController extends AbstractRestController {

    private static final String ID = "id";

    private final AsyncTaskApiService asyncTaskApiService;

    @PostMapping("/filter")
    @ResponseBody
    @Operation(
            summary = "Lists asynchronous tasks of the current user.",
            description = "Lists asynchronous tasks of the current user. An administrator may leave the "
                    + "owner empty to list tasks of all users.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<AsyncTask>> filter(@RequestBody(required = false) final AsyncTaskFilterVO filter) {
        return Result.success(asyncTaskApiService.loadByFilter(filter));
    }

    @GetMapping("/{id}")
    @ResponseBody
    @Operation(
            summary = "Returns an asynchronous task by id.",
            description = "Returns an asynchronous task by id.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<AsyncTask> load(@PathVariable(value = ID) final String id) {
        return Result.success(asyncTaskApiService.load(id));
    }

    @GetMapping("/{id}/url")
    @ResponseBody
    @Operation(
            summary = "Returns a download url for the artifact a task produced.",
            description = "Returns a download url for the artifact a task produced. The url is signed "
                    + "anew on every call and the task is not changed by it.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<DataStorageDownloadFileUrl> generateUrl(@PathVariable(value = ID) final String id) {
        return Result.success(asyncTaskApiService.generateUrl(id));
    }

    @PostMapping("/{id}/cancel")
    @ResponseBody
    @Operation(
            summary = "Cancels an asynchronous task.",
            description = "Cancels an asynchronous task. A task nobody has taken is cancelled at once, "
                    + "a running one is asked to stop and confirms the cancellation itself.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<AsyncTask> cancel(@PathVariable(value = ID) final String id) {
        return Result.success(asyncTaskApiService.cancel(id));
    }

    @PostMapping("/next")
    @ResponseBody
    @Operation(
            summary = "Hands out the oldest pending task of the given type.",
            description = "Hands out the oldest pending task of the given type and marks it running. "
                    + "Returns an empty payload when the queue holds nothing of that type, which is "
                    + "the ordinary answer rather than an error. "
                    + "One task per call, by design: between the moment a task is marked running and "
                    + "the moment an executor actually picks it up, nobody is working on it, and a "
                    + "crash in that window leaves it hanging until it times out. Claim one, hand it "
                    + "to a worker, then claim the next - so at most one task is ever exposed to that "
                    + "window.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<AsyncTask> next(@RequestParam final AsyncTaskType type) {
        return Result.success(asyncTaskApiService.claimNext(type).orElse(null));
    }

    @PostMapping("/{id}/status")
    @ResponseBody
    @Operation(
            summary = "Reports a status change of a task.",
            description = "Reports a status change of a task. Only the transitions the task model "
                    + "allows are accepted; repeating the status a task already holds changes nothing.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<AsyncTask> updateStatus(@PathVariable(value = ID) final String id,
                                          @RequestBody final AsyncTaskStatusVO status) {
        return Result.success(asyncTaskApiService.updateStatus(id, status));
    }
}
