/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.external.datastorage.controller.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.external.datastorage.manager.log.LogService;
import com.epam.pipeline.rest.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Api(value = "Log API")
@RequestMapping("/log")
@RequiredArgsConstructor
public class LogController {

    private final LogService service;

    @PostMapping
    @ResponseBody
    @ApiOperation(
            value = "Save logs.",
            notes = "Save logs.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<Boolean> save(@RequestBody final List<LogEntry> entries) {
        service.save(entries);
        return Result.success();
    }
}
