/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.acl.cluster.InfrastructureApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MachineType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.NodeResources;
import com.epam.pipeline.entity.cluster.PodDescription;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.acl.cluster.ClusterApiService;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.platform.network.NetworkEventFilter;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramBin;
import com.epam.pipeline.entity.cluster.monitoring.platform.histogram.HistogramType;
import com.epam.pipeline.entity.pipeline.run.RunInfo;
import com.epam.pipeline.manager.cluster.MonitoringReportType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@Tag(name = "cluster-controller", description = "Cluster methods")
@RequiredArgsConstructor
public class ClusterController extends AbstractRestController {

    private static final String NAME = "name";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String INTERVAL = "interval";
    private static final String REPORT_TYPE = "type";
    private static final String FALSE = "false";
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String REPORT_NAME_TEMPLATE = "%s_%s-%s-%s.%s";
    private static final char TIME_SEPARATION_CHAR = ':';
    private static final char UNDERSCORE = '_';
    private static final String KUBE = "KUBE";

    private final ClusterApiService clusterApiService;
    private final InfrastructureApiService infrastructureApiService;

    @GetMapping(value = "/cluster/master")
    @Operation(
            summary = "Returns kubernetes nodes used in cluster as a master API node",
            description = "Returns kubernetes nodes used in cluster as a master API node"
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<List<MasterNode>> loadMasterNodes() {
        return Result.success(clusterApiService.getMasterNodes());
    }

    @GetMapping("/cluster/edge/externalUrl")
    @Operation(
            summary = "Return EDGE external URL.",
            description = "Return EDGE external URL.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<String> buildEdgeExternalUrl(@RequestParam(required = false) final String region) {
        return Result.success(clusterApiService.buildEdgeExternalUrl(region));
    }

    @GetMapping(value = "/cluster/node/loadAll")
    @Operation(
            summary = "Returns all nodes used in cluster",
            description = "Returns all nodes used in cluster"
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<List<NodeInstance>> loadNodes(
            @RequestParam(required = false, defaultValue = KUBE) final MachineType machineType) {
        return Result.success(clusterApiService.getNodes(machineType));
    }

    @PostMapping("/cluster/node/filter")
    @Operation(
            summary = "Returns all nodes used in cluster, filtered by runId or address",
            description = "Returns all nodes used in cluster, filtered by runId or address"
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)}
    )
    public Result<List<NodeInstance>> filterNodes(@RequestBody final FilterNodesVO filterNodesVO,
                                                  @RequestParam(required = false, defaultValue = KUBE)
                                                  final MachineType machineType) {
        return Result.success(clusterApiService.filterNodes(filterNodesVO, machineType));
    }

    @GetMapping(value = "/cluster/node/{name}/load")
    @Operation(
            summary = "Returns a node, specified by name.",
            description = "Returns a node, specified by name."
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> loadNode(@PathVariable(value = NAME) final String name,
                                         @RequestParam(required = false, defaultValue = KUBE)
                                         final MachineType machineType,
                                         @RequestParam(required = false) final Long regionId) {
        return Result.success(clusterApiService.getNode(name, machineType, regionId));
    }

    @RequestMapping(value = "/cluster/node/{name}/run", method = RequestMethod.GET)
    @Operation(
            summary = "Returns run id associated with nodename.",
            description = "Returns run id associated with nodename."
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<RunInfo> loadRunIdForNode(@PathVariable(value = NAME) final String name) {
        return Result.success(clusterApiService.loadRunIdForNode(name));
    }

    @RequestMapping(value = "/cluster/node/{name}/load", method = RequestMethod.POST)
    @Operation(
            summary = "Returns an ec2 node, specified by name. Filter pods by statuses",
            description = "Returns an ec2 node, specified by name. Filter pods by statuses"
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> loadNodeFiltered(@PathVariable(value = NAME) final String name,
            @RequestBody FilterPodsRequest request) {
        return Result.success(clusterApiService.getNode(name, request));
    }

    @DeleteMapping(value = "/cluster/node/{name}")
    @Operation(
            summary = "Terminates a node, specified by name.",
            description = "Terminates a node, specified by name."
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<NodeInstance> terminateNode(@PathVariable(value = NAME) final String name,
                                              @RequestParam(required = false, defaultValue = KUBE)
                                              final MachineType machineType,
                                              @RequestParam(required = false) final Long regionId) {
        return Result.success(clusterApiService.terminateNode(name, machineType, regionId));
    }

    @RequestMapping(value = "/cluster/instance/loadAll", method = RequestMethod.GET)
    @Operation(
            summary = "Returns all instance types.",
            description = "Returns all instance types in the specified or default region."
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<InstanceType>> loadAllInstanceTypes(
            @RequestParam(required = false) final Long regionId,
            @RequestParam(required = false, defaultValue = FALSE) final boolean toolInstances,
            @RequestParam(required = false) final Boolean spot) {
        return toolInstances
            ? Result.success(clusterApiService.getAllowedToolInstanceTypes(regionId, spot))
            : Result.success(clusterApiService.getAllowedInstanceTypes(regionId, spot));
    }

    @GetMapping(value = "/cluster/instance")
    @Operation(
            summary = "Returns description of an instance type",
            description = "Includes information on cpu, ram cpu resources")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<InstanceType> loadInstanceType(@RequestParam final String instanceType) {
        return Result.success(clusterApiService.loadInstanceType(instanceType));
    }

    @RequestMapping(value = "/cluster/instance/allowed", method = RequestMethod.GET)
    @Operation(
            summary = "Returns allowed instance types and allowed prices types for the authorized user.",
            description = "Returns allowed instance types and allowed prices types for the authorized user " +
                    "in the specified or default region."
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<AllowedInstanceAndPriceTypes> loadAllowedInstanceAndPriceTypes(
            @RequestParam(required = false) final Long toolId,
            @RequestParam(required = false) final Long regionId,
            @RequestParam(required = false) final Boolean spot) {
        return Result.success(clusterApiService.getAllowedInstanceAndPriceTypes(toolId, regionId, spot));
    }

    @RequestMapping(value = "/cluster/node/{name}/usage", method = RequestMethod.GET)
    @Operation(
            summary = "Returns stats from instance by given IP address",
            description = "Returns stats from instance by given IP address"
        )
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<MonitoringStats>> getNodeUsageStatistics(
            @PathVariable(value = NAME) final String name,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = FROM, required = false) final LocalDateTime from,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = TO, required = false) final LocalDateTime to,
            @RequestParam(required = false) final Long runId) {
        return Result.success(clusterApiService.getStatsForNode(name, from, to, runId));
    }

    @GetMapping("/cluster/node/{name}/usage/gpus")
    @Operation(
            summary = "Returns GPU stats from instance by given IP address",
            description = "Returns GPU stats from instance by given IP address")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GpuMonitoringStats> getNodeUsageGpuStatistics(
            @PathVariable(value = NAME) final String name,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = FROM, required = false) final LocalDateTime from,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = TO, required = false) final LocalDateTime to,
            @RequestParam final List<GpuMetricsGranularity> granularity,
            @RequestParam(required = false, defaultValue = FALSE)
            final boolean squashCharts,
            @RequestParam(required = false) final Long runId) {
        return Result.success(clusterApiService.getGpuStatsForNode(name, from, to, granularity, squashCharts, runId));
    }

    @GetMapping("/cluster/node/{name}/usage/report")
    @Operation(
        summary = "Download resource utilization report for given instance as a csv file.",
        description = "Download resource utilization report for given instance as a csv file.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public void downloadNodeUsageStatisticsReport(
        @PathVariable(value = NAME) final String name,
        @DateTimeFormat(pattern = DATE_TIME_FORMAT)
        @RequestParam(value = FROM, required = false) final LocalDateTime from,
        @DateTimeFormat(pattern = DATE_TIME_FORMAT)
        @RequestParam(value = TO, required = false) final LocalDateTime to,
        @RequestParam(required = false) final Long runId,
        @RequestParam(value = INTERVAL, required = false, defaultValue = "PT1M") final Duration interval,
        @RequestParam(value = REPORT_TYPE, required = false, defaultValue = "CSV") final MonitoringReportType type,
        final HttpServletResponse response) throws IOException {
        final InputStream inputStream = clusterApiService.getUsageStatisticsFile(name, from, to, interval, type, runId);
        final String reportName =
            String.format(REPORT_NAME_TEMPLATE, name, from, to, interval, type.name().toLowerCase())
                .replace(TIME_SEPARATION_CHAR, UNDERSCORE);
        writeStreamToResponse(response, inputStream, reportName);
    }

    @RequestMapping(value = "/cluster/node/{name}/disks", method = RequestMethod.GET)
    @Operation(
        summary = "Returns node disks.",
        description = "Returns node disks.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<List<NodeDisk>> loadNodeDisks(@PathVariable(value = NAME) final String name) {
        return Result.success(clusterApiService.loadNodeDisks(name));
    }

    @PostMapping("/cluster/dnsrecord")
    @Operation(
            summary = "Creates dns record.",
            description = "Creates dns record.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<InstanceDNSRecord> requestDnsRecord(@RequestParam final Long regionId,
                                                      @RequestBody final InstanceDNSRecord dnsRecord) {
        return Result.success(infrastructureApiService.createInstanceDNSRecord(regionId, dnsRecord));
    }

    @GetMapping("/cluster/pods/core")
    @Operation(
            summary = "Returns core pods.",
            description = "Returns core pods.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<List<PodInstance>> loadCorePods() {
        return Result.success(clusterApiService.getCorePods());
    }

    @PostMapping("/cluster/pods/filter")
    @Operation(
            summary = "Returns pods filtered by labels.",
            description = "Returns pods filtered by labels.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<List<PodInstance>> loadPodsByLabels(@RequestBody final Map<String, String> labels) {
        return Result.success(clusterApiService.getPodsByLabels(labels));
    }

    @GetMapping("/cluster/pods/info")
    @Operation(
            summary = "Returns pod description.",
            description = "Returns pod description.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<PodDescription> loadPodDescription(@RequestParam final String podId,
                                                     @RequestParam(required = false, defaultValue = FALSE)
                                                     final boolean detailed) {
        return Result.success(clusterApiService.getPodDescription(podId, detailed));
    }

    @GetMapping("/cluster/containers/logs")
    @Operation(
            summary = "Returns pod container logs.",
            description = "Returns pod container logs.")
    @ApiResponses(@ApiResponse(description = API_STATUS_DESCRIPTION))
    public Result<String> loadContainerLogs(@RequestParam final String podId,
                                            @RequestParam final String containerId,
                                            @RequestParam(required = false) final Integer limit) {
        return Result.success(clusterApiService.getContainerLogs(podId, containerId, limit));
    }

    @RequestMapping(value = "/cluster/network/usage", method = RequestMethod.GET)
    @Operation(
            summary = "Returns available filters to filter network usage of the platform.",
            description = "Returns available filters to filter network usage of the platform.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<NetworkEventFilter> getPlatformNetworkEventFilter() {
        return Result.success(clusterApiService.getPlatformNetworkEventFilter());
    }

    @RequestMapping(value = "/cluster/network/usage", method = RequestMethod.POST)
    @Operation(
            summary = "Returns histogram for platform network usage, filtered by specified filter object",
            description = "Returns histogram for platform network usage, filtered by specified filter object")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<HistogramBin>> filterPlatformNetworkEvents(
            @RequestParam final HistogramType histogramType,
            @RequestParam(required = false, defaultValue = "10") final Integer intervals,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = FROM) final LocalDateTime from,
            @DateTimeFormat(pattern = DATE_TIME_FORMAT)
            @RequestParam(value = TO) final LocalDateTime to,
            @RequestBody final NetworkEventFilter filter) {
        return Result.success(
                clusterApiService.filterPlatformNetworkEvents(histogramType, from, to, intervals, filter)
        );
    }

    @PostMapping("/cluster/node/resources")
    @Operation(
            summary = "Returns available resources info for node filtered by labels.",
            description = "Returns available resources info for node filtered by labels.")
    @ApiResponses(value = {@ApiResponse(description = API_STATUS_DESCRIPTION)})
    public Result<List<NodeResources>> loadNodeResources(final @RequestBody Map<String, String> labels) {
        return Result.success(clusterApiService.loadNodeAvailableResource(labels));
    }
}
