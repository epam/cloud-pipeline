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

package com.epam.pipeline.controller.cluster;

import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.manager.cluster.ClusterApiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Api(value = "Cluster methods")
public class ClusterController extends AbstractRestController {

    private static final String NAME = "name";

    @Autowired
    private ClusterApiService clusterApiService;

    @RequestMapping(value = "/cluster/node/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all ec2 nodes used in cluster",
            notes = "Returns all ec2 nodes used in cluster",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<List<NodeInstance>> loadNodes() {
        return Result.success(clusterApiService.getNodes());
    }

    @RequestMapping(value = "/cluster/node/filter", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Returns all ec2 nodes used in cluster, filtered by runId or address",
            notes = "Returns all ec2 nodes used in cluster, filtered by runId or address",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)}
    )
    public Result<List<NodeInstance>> filterNodes(@RequestBody FilterNodesVO filterNodesVO) {
        return Result.success(clusterApiService.filterNodes(filterNodesVO));
    }

    @RequestMapping(value = "/cluster/node/{name}/load", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns an ec2 node, specified by name.",
            notes = "Returns an ec2 node, specified by name.",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> loadNode(@PathVariable(value = NAME) final String name) {
        return Result.success(clusterApiService.getNode(name));
    }

    @RequestMapping(value = "/cluster/node/{name}/load", method = RequestMethod.POST)
    @ResponseBody
    @ApiOperation(
            value = "Returns an ec2 node, specified by name. Filter pods by statuses",
            notes = "Returns an ec2 node, specified by name. Filter pods by statuses",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> loadNodeFiltered(@PathVariable(value = NAME) final String name,
            @RequestBody FilterPodsRequest request) {
        return Result.success(clusterApiService.getNode(name, request));
    }

    @RequestMapping(value = "/cluster/node/{name}", method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(
            value = "Terminates an ec2 node, specified by name.",
            notes = "Terminates an ec2 node, specified by name.",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> terminateNode(@PathVariable(value = NAME) final String name) {
        return Result.success(clusterApiService.terminateNode(name));
    }

    @RequestMapping(value = "/cluster/instance/loadAll", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns all instance types.",
            notes = "Returns all instance types in the specified or default region.",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<InstanceType>> loadAllInstanceTypes(
            @RequestParam(required = false) final Long regionId,
            @RequestParam(required = false, defaultValue = "false") final boolean toolInstances,
            @RequestParam(required = false) final Boolean spot) {
        return toolInstances
            ? Result.success(clusterApiService.getAllowedToolInstanceTypes(regionId, spot))
            : Result.success(clusterApiService.getAllowedInstanceTypes(regionId, spot));
    }

    @RequestMapping(value = "/cluster/instance/allowed", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns allowed instance types and allowed prices types for the authorized user.",
            notes = "Returns allowed instance types and allowed prices types for the authorized user " +
                    "in the specified or default region.",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<AllowedInstanceAndPriceTypes> loadAllowedInstanceAndPriceTypes(
            @RequestParam(required = false) final Long toolId,
            @RequestParam(required = false) final Long regionId,
            @RequestParam(required = false) final Boolean spot) {
        return Result.success(clusterApiService.getAllowedInstanceAndPriceTypes(toolId, regionId, spot));
    }

    @RequestMapping(value = "/cluster/node/{name}/usage", method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(
            value = "Returns stats from instance by given IP address",
            notes = "Returns stats from instance by given IP address",
            produces = MediaType.APPLICATION_JSON_VALUE
        )
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<MonitoringStats>> getNodeUsageStatistics(@PathVariable(value = NAME) final String name) {
        return Result.success(clusterApiService.getStatsForNode(name));
    }
}
