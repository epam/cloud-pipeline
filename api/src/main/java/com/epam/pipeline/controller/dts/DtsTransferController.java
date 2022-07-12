/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.dts;

import com.epam.pipeline.acl.dts.TransferTaskApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.dts.TransferTask;
import com.epam.pipeline.controller.vo.dts.TransferTaskFilter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@Api(value = "Data Transfer Task Management")
@RequestMapping(value = "/dts-task")
@AllArgsConstructor
public class DtsTransferController extends AbstractRestController {

    private TransferTaskApiService transferTaskService;

    @PostMapping
    @ApiOperation(
            value = "Creates new transfer tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<TransferTask>> createTasks(@RequestBody final List<TransferTask> tasks) {
        return Result.success(transferTaskService.create(tasks));
    }

    @PostMapping(path = "/filter")
    @ApiOperation(
            value = "Filters tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<Page<TransferTask>> filterTasks(@RequestBody final TransferTaskFilter filter) {
        return Result.success(transferTaskService.filter(filter));
    }

    @GetMapping
    @ApiOperation(
            value = "Returns all tasks.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<List<TransferTask>> getAll() {
        return Result.success(transferTaskService.loadAll());
    }
}
