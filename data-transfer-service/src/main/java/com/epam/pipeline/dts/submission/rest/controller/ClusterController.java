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

package com.epam.pipeline.dts.submission.rest.controller;


import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.common.rest.controller.AbstractRestController;
import com.epam.pipeline.dts.submission.model.cluster.ClusterConfiguration;
import com.epam.pipeline.dts.submission.service.cluster.ClusterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.epam.pipeline.dts.common.rest.controller.AbstractRestController.API_STATUS_DESCRIPTION;

@RequestMapping("cluster")
@Tag(name = "SGE cluster configuration")
@ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
@RestController
@RequiredArgsConstructor
public class ClusterController extends AbstractRestController {

    private final ClusterService clusterService;

    @GetMapping
    @Operation(summary = "Returns list of available hosts.")
    public Result<ClusterConfiguration> loadClusterConfiguration() {
        return Result.success(clusterService.getClusterConfiguration());
    }
}
