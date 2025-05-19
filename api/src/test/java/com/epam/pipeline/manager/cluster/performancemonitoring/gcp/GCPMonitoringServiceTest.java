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

package com.epam.pipeline.manager.cluster.performancemonitoring.gcp;

import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMonitoringStats;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPInstancesClientWrapper;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPMachineTypesClientWrapper;
import com.epam.pipeline.manager.cloud.gcp.wrappers.GCPMetricServiceClientWrapper;
import com.epam.pipeline.manager.cluster.NodesManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.google.api.Metric;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.MachineType;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity.ALL;
import static com.epam.pipeline.entity.cluster.monitoring.gpu.GpuMetricsGranularity.GLOBAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GCPMonitoringServiceTest {
    private static final String NODE_NAME = "test-node";
    private static final String PROJECT_ID = "test-project";
    private static final String ZONE = "us-central1-a";
    private static final String MACHINE_TYPE_URL = "https://www.google.com/machinetypes/e2-micro";
    private static final Long NODE_ID = 874912437120482L;
    private static final int CPU_NUMBER = 4;
    private static final int MEMORY_MB = 2048;
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final LocalDateTime FROM = LocalDateTime.of(2025, Month.APRIL, 14, 12, 43, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2025, Month.APRIL, 14, 13, 43, 0);
    public static final String SDA1 = "/dev/sda1";
    public static final String SDA15 = "/dev/sda15";
    public static final String FREE = "free";
    public static final String USED = "used";
    public static final String SUMMARY = "summary";
    private static final String GPU_NUMBER_0 = "0";
    private static final String GPU_MODEL = "NVIDIA A10G";
    private static final String MEMORY_STATE_USED = "used";
    private static final String MEMORY_STATE_FREE = "free";
    public static final double PERCENT_MULTIPLIER = 100.0;
    public static final String MAX = "MAX";
    public static final String MIN = "MIN";
    public static final String AVG = "AVG";
    public static final String MEAN = "MEAN";
    public static final String GPU_NUMBER = "gpu_number";
    public static final String MEMORY_STATE = "memory_state";

    enum MetricPoint {
        TIME_1(1744634580L),
        TIME_2(1744638180L);

        final long timestamp;

        MetricPoint(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    enum CpuMetric {
        MEAN_1(MetricPoint.TIME_1, 0.38),
        MEAN_2(MetricPoint.TIME_2, 0.455),
        MAX_1(MetricPoint.TIME_1, 0.43),
        MAX_2(MetricPoint.TIME_2, 0.75);

        final MetricPoint point;
        final double value;

        CpuMetric(MetricPoint point, double value) {
            this.point = point;
            this.value = value;
        }
    }

    enum MemoryMetric {
        MEAN_1(MetricPoint.TIME_1, 123124d),
        MEAN_2(MetricPoint.TIME_2, 425532523d),
        MAX_1(MetricPoint.TIME_1, 223124d),
        MAX_2(MetricPoint.TIME_2, 525562523d);

        final MetricPoint point;
        final double value;

        MemoryMetric(MetricPoint point, double value) {
            this.point = point;
            this.value = value;
        }
    }

    enum DiskMetric {
        FREE_SDA1_1(MetricPoint.TIME_1, 36298d, FREE, SDA1),
        FREE_SDA1_2(MetricPoint.TIME_2, 47320943d, FREE, SDA1),
        USED_SDA1_1(MetricPoint.TIME_1, 36298d, USED, SDA1),
        USED_SDA1_2(MetricPoint.TIME_2, 47320943d, USED, SDA1),
        FREE_SDA15_1(MetricPoint.TIME_1, 7668990d, FREE, SDA15),
        FREE_SDA15_2(MetricPoint.TIME_2, 47572352d, FREE, SDA15),
        USED_SDA15_1(MetricPoint.TIME_1, 7668990d, USED, SDA15),
        USED_SDA15_2(MetricPoint.TIME_2, 47572352d, USED, SDA15);

        final MetricPoint point;
        final double value;
        final String state;
        final String device;

        DiskMetric(MetricPoint point, double value, String state, String device) {
            this.point = point;
            this.value = value;
            this.state = state;
            this.device = device;
        }
    }

    enum NetworkMetric {
        RX_1(MetricPoint.TIME_1, 1048576L),
        RX_2(MetricPoint.TIME_2, 2097152L),
        TX_1(MetricPoint.TIME_1, 524288L),
        TX_2(MetricPoint.TIME_2, 1572864L);

        final MetricPoint point;
        final long value;

        NetworkMetric(MetricPoint point, long value) {
            this.point = point;
            this.value = value;
        }
    }

    enum GpuMetric {
        AVG_1(MetricPoint.TIME_1, 60.0), // GPU utilization in percent
        AVG_2(MetricPoint.TIME_2, 80.0),
        MIN_1(MetricPoint.TIME_1, 50.0),
        MIN_2(MetricPoint.TIME_2, 70.0),
        MAX_1(MetricPoint.TIME_1, 65.0),
        MAX_2(MetricPoint.TIME_2, 85.0);

        final MetricPoint point;
        final double value;

        GpuMetric(MetricPoint point, double value) {
            this.point = point;
            this.value = value;
        }
    }

    enum GpuMemoryMetric {
        AVG_USED_1(MetricPoint.TIME_1, 8000000000L), // 8GB used
        AVG_USED_2(MetricPoint.TIME_2, 10000000000L), // 10GB used
        AVG_FREE_1(MetricPoint.TIME_1, 8000000000L), // 8GB free
        AVG_FREE_2(MetricPoint.TIME_2, 6000000000L), // 6GB free
        MIN_USED_1(MetricPoint.TIME_1, 7000000000L), // 7GB used
        MIN_USED_2(MetricPoint.TIME_2, 9000000000L), // 9GB used
        MIN_FREE_1(MetricPoint.TIME_1, 9000000000L), // 9GB free
        MIN_FREE_2(MetricPoint.TIME_2, 7000000000L), // 7GB free
        MAX_USED_1(MetricPoint.TIME_1, 9000000000L), // 9GB used
        MAX_USED_2(MetricPoint.TIME_2, 11000000000L), // 11GB used
        MAX_FREE_1(MetricPoint.TIME_1, 7000000000L), // 7GB free
        MAX_FREE_2(MetricPoint.TIME_2, 5000000000L); // 5GB free

        final MetricPoint point;
        final double value;
        final String memoryState;

        GpuMemoryMetric(MetricPoint point, double value) {
            this.point = point;
            this.value = value;
            this.memoryState = name().contains("USED") ? MEMORY_STATE_USED : MEMORY_STATE_FREE;
        }
    }

    enum GpuActiveMetric {
        AVG_1(MetricPoint.TIME_1, 1.0), // 1 active process
        AVG_2(MetricPoint.TIME_2, 2.0), // 2 active processes
        MIN_1(MetricPoint.TIME_1, 1.0),
        MIN_2(MetricPoint.TIME_2, 1.0),
        MAX_1(MetricPoint.TIME_1, 1.0),
        MAX_2(MetricPoint.TIME_2, 3.0);

        final MetricPoint point;
        final double value;

        GpuActiveMetric(MetricPoint point, double value) {
            this.point = point;
            this.value = value;
        }
    }

    @Mock private GCPClient gcpClient;
    @Mock private NodesManager nodesManager;
    @Mock private CloudRegionDao cloudRegionDao;
    @Mock private PreferenceManager preferenceManager;
    @Mock private GCPInstancesClientWrapper instancesClient;
    @Mock private GCPMetricServiceClientWrapper metricsClient;
    @Mock private GCPMachineTypesClientWrapper machineTypesClient;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageCpuMax;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageCpuMean;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageMemoryMax;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageMemoryMean;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageDiskMean;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageNetworkRx;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageNetworkTx;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuAvg;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuMax;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuMin;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuMemoryAvg;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuMemoryMax;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuMemoryMin;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuActiveAvg;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuActiveMax;
    @Mock private MetricServiceClient.ListTimeSeriesPage timeSeriesPageGpuActiveMin;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse cpuMeanResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse cpuMaxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse memoryMeanResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse memoryMaxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse diskMeanResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse networkRxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse networkTxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuAvgResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuMaxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuMinResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuMemoryAvgResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuMemoryMaxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuMemoryMinResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuActiveAvgResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuActiveMaxResponse;
    @Mock private MetricServiceClient.ListTimeSeriesPagedResponse gpuActiveMinResponse;

    @InjectMocks
    private GCPMonitoringService gcpMonitoringService;

    private final GCPRegion gcpRegion = new GCPRegion();
    private final NodeInstance nodeInstance = new NodeInstance();

    @Before
    public void setUp() throws IOException {
        setupGcpRegion();
        setupVmDetails();
        when(gcpClient.buildMetricsClient(gcpRegion)).thenReturn(metricsClient);
        when(preferenceManager.getPreference(any())).thenReturn(null);
    }

    @Test
    public void shouldCollectAllStatsSuccess()  {
        when(metricsClient.listTimeSeries(any(ListTimeSeriesRequest.class)))
                .thenReturn(cpuMeanResponse).thenReturn(cpuMaxResponse)
                .thenReturn(memoryMeanResponse).thenReturn(memoryMaxResponse)
                .thenReturn(diskMeanResponse)
                .thenReturn(networkRxResponse)
                .thenReturn(networkTxResponse);

        setupCpuMetrics();
        setupMemoryMetrics();
        setupDiskMetrics();
        setupNetworkMetrics();

        List<MonitoringStats> stats = gcpMonitoringService.getStatsForNode(NODE_NAME, FROM, TO);
        assertThat(stats).hasSize(2);

        MonitoringStats stat1 = stats.get(0);
        assertThat(stat1.getCpuUsage().getLoad()).isEqualTo(CpuMetric.MEAN_1.value);
        assertThat(stat1.getCpuUsage().getMax()).isEqualTo(CpuMetric.MAX_1.value);

        assertThat(stat1.getMemoryUsage().getUsage()).isEqualTo((long) MemoryMetric.MEAN_1.value);
        assertThat(stat1.getMemoryUsage().getMax()).isEqualTo((long) MemoryMetric.MAX_1.value);
        assertThat(stat1.getMemoryUsage().getCapacity()).isEqualTo(MEMORY_MB * BYTES_PER_MB);

        Map<String, MonitoringStats.DisksUsage.DiskStats> devices1 = stat1.getDisksUsage().getStatsByDevices();
        assertThat(devices1.get("/dev/sda1").getCapacity())
                .isEqualTo((long) (DiskMetric.FREE_SDA1_1.value + DiskMetric.USED_SDA1_1.value));
        assertThat(devices1.get("/dev/sda1").getUsableSpace()).isEqualTo((long) DiskMetric.FREE_SDA1_1.value);
        assertThat(devices1.get(SDA15).getCapacity())
                .isEqualTo((long) (DiskMetric.FREE_SDA15_1.value + DiskMetric.USED_SDA15_1.value));
        assertThat(devices1.get(SDA15).getUsableSpace()).isEqualTo((long) DiskMetric.FREE_SDA15_1.value);

        Map<String, MonitoringStats.NetworkUsage.NetworkStats> interfaces1 = stat1.getNetworkUsage()
                .getStatsByInterface();
        assertThat(interfaces1.get(SUMMARY).getRxBytes()).isEqualTo(NetworkMetric.RX_1.value);
        assertThat(interfaces1.get(SUMMARY).getTxBytes()).isEqualTo(NetworkMetric.TX_1.value);

        MonitoringStats stat2 = stats.get(1);
        assertThat(stat2.getCpuUsage().getLoad()).isEqualTo(CpuMetric.MEAN_2.value);
        assertThat(stat2.getCpuUsage().getMax()).isEqualTo(CpuMetric.MAX_2.value);

        assertThat(stat2.getMemoryUsage().getUsage()).isEqualTo((long) MemoryMetric.MEAN_2.value);
        assertThat(stat2.getMemoryUsage().getMax()).isEqualTo((long) MemoryMetric.MAX_2.value);
        assertThat(stat2.getMemoryUsage().getCapacity()).isEqualTo(MEMORY_MB * BYTES_PER_MB);

        Map<String, MonitoringStats.DisksUsage.DiskStats> devices2 = stat2.getDisksUsage().getStatsByDevices();
        assertThat(devices2.get(SDA1).getCapacity())
                .isEqualTo((long) (DiskMetric.FREE_SDA1_2.value + DiskMetric.USED_SDA1_2.value));
        assertThat(devices2.get(SDA1).getUsableSpace()).isEqualTo((long) DiskMetric.FREE_SDA1_2.value);
        assertThat(devices2.get(SDA15).getCapacity())
                .isEqualTo((long) (DiskMetric.FREE_SDA15_2.value + DiskMetric.USED_SDA15_2.value));
        assertThat(devices2.get(SDA15).getUsableSpace()).isEqualTo((long) DiskMetric.FREE_SDA15_2.value);

        Map<String, MonitoringStats.NetworkUsage.NetworkStats> interfaces2 = stat2.getNetworkUsage()
                .getStatsByInterface();
        assertThat(interfaces2.get(SUMMARY).getRxBytes()).isEqualTo(NetworkMetric.RX_2.value);
        assertThat(interfaces2.get(SUMMARY).getTxBytes()).isEqualTo(NetworkMetric.TX_2.value);
    }

    @Test(expected = PipelineException.class)
    public void shouldThrowExceptionOnBuildingMetricsClient() throws IOException {
        when(gcpClient.buildMetricsClient(gcpRegion)).thenThrow(new IOException());
        gcpMonitoringService.getStatsForNode(NODE_NAME, FROM, TO);
    }

    @Test
    public void shouldCollectContainerSpec()  {
        when(metricsClient.listTimeSeries(any(ListTimeSeriesRequest.class))).thenReturn(cpuMeanResponse);
        setupCpuMetrics();

        List<MonitoringStats> stats = gcpMonitoringService.getStatsForNode(NODE_NAME, FROM, TO);
        assertThat(stats).hasSize(2);

        stats.forEach(stat -> {
            assertThat(stat.getContainerSpec().getMaxMemory()).isEqualTo(MEMORY_MB * BYTES_PER_MB);
            assertThat(stat.getContainerSpec().getNumberOfCores()).isEqualTo(CPU_NUMBER);
        });
    }

    @Test
    public void shouldSortStatsInAscendingOrder() {
        when(metricsClient.listTimeSeries(any(ListTimeSeriesRequest.class))).thenReturn(cpuMeanResponse);
        setupCpuMetricsReverseOrder();

        List<MonitoringStats> stats = gcpMonitoringService.getStatsForNode(NODE_NAME, FROM, TO);
        assertThat(stats).hasSize(2);

        for (int i = 0; i < stats.size() - 1; i++) {
            Instant currentStartTime = Instant.parse(stats.get(i).getStartTime());
            Instant nextStartTime = Instant.parse(stats.get(i + 1).getStartTime());
            assertThat(currentStartTime).isLessThanOrEqualTo(nextStartTime);
        }

        assertThat(stats.get(0).getCpuUsage().getLoad()).isEqualTo(CpuMetric.MEAN_1.value);
        assertThat(stats.get(1).getCpuUsage().getLoad()).isEqualTo(CpuMetric.MEAN_2.value);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForNodeNameGetGpuStatsForNode() {
        gcpMonitoringService.getGpuStatsForNode(null, null, null, null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForSquashChartsGetGpuStatsForNode() {
        gcpMonitoringService.getGpuStatsForNode(NODE_NAME, null, null, null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForGranularityGetGpuStatsForNode() {
        gcpMonitoringService.getGpuStatsForNode(NODE_NAME, null, null, Collections.singletonList(GLOBAL), false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForDatesGetGpuStatsForNode() {
        gcpMonitoringService.getGpuStatsForNode(NODE_NAME, TO, FROM, Collections.singletonList(ALL), false);
    }

    @Test
    public void shouldCollectGpuStatsSuccess() {
        when(metricsClient.listTimeSeries(any(ListTimeSeriesRequest.class)))
                .thenReturn(gpuAvgResponse)
                .thenReturn(gpuMaxResponse)
                .thenReturn(gpuMinResponse)
                .thenReturn(gpuMemoryAvgResponse)
                .thenReturn(gpuMemoryMaxResponse)
                .thenReturn(gpuMemoryMinResponse)
                .thenReturn(gpuActiveAvgResponse)
                .thenReturn(gpuActiveMaxResponse)
                .thenReturn(gpuActiveMinResponse);

        setupGpuMetrics();
        setupGpuMemoryMetrics();
        setupGpuActiveMetrics();

        GpuMonitoringStats stats = gcpMonitoringService.getGpuStatsForNode(NODE_NAME, FROM, TO,
                Collections.singletonList(ALL), false);

        assertThat(stats).isNotNull();
        assertThat(stats.getCharts()).hasSize(2);

        MonitoringStats stat1 = stats.getCharts().get(0);
        MonitoringStats stat2 = stats.getCharts().get(1);

        //1
        assertThat(stat1.getGpuDeviceName()).isEqualTo(GPU_MODEL);
        Map<String, MonitoringStats.GPUUsage> details1 = stat1.getGpuDetails();
        assertThat(details1).hasSize(1);
        MonitoringStats.GPUUsage gpu1 = details1.get(GPU_NUMBER_0);
        assertThat(gpu1.getGpuUtilization().getAverage()).isEqualTo(GpuMetric.AVG_1.value);
        assertThat(gpu1.getGpuUtilization().getMin()).isEqualTo(GpuMetric.MIN_1.value);
        assertThat(gpu1.getGpuUtilization().getMax()).isEqualTo(GpuMetric.MAX_1.value);
        assertThat(gpu1.getGpuMemoryUsed().getAverage()).isEqualTo(GpuMemoryMetric.AVG_USED_1.value);
        assertThat(gpu1.getGpuMemoryUsed().getMin()).isEqualTo(GpuMemoryMetric.MIN_USED_1.value);
        assertThat(gpu1.getGpuMemoryUsed().getMax()).isEqualTo(GpuMemoryMetric.MAX_USED_1.value);
        assertThat(gpu1.getGpuMemoryFree().getAverage()).isEqualTo(GpuMemoryMetric.AVG_FREE_1.value);
        assertThat(gpu1.getGpuMemoryFree().getMin()).isEqualTo(GpuMemoryMetric.MIN_FREE_1.value);
        assertThat(gpu1.getGpuMemoryFree().getMax()).isEqualTo(GpuMemoryMetric.MAX_FREE_1.value);
        assertThat(gpu1.getActiveGpus().getAverage()).isEqualTo(GpuActiveMetric.AVG_1.value);
        assertThat(gpu1.getActiveGpus().getMin()).isEqualTo(GpuActiveMetric.MIN_1.value);
        assertThat(gpu1.getActiveGpus().getMax()).isEqualTo(GpuActiveMetric.MAX_1.value);
        // Verify GPU Memory Utilization
        double totalAvg1 = GpuMemoryMetric.AVG_USED_1.value + GpuMemoryMetric.AVG_FREE_1.value;
        double totalMin1 = GpuMemoryMetric.MIN_USED_1.value + GpuMemoryMetric.MIN_FREE_1.value;
        double totalMax1 = GpuMemoryMetric.MAX_USED_1.value + GpuMemoryMetric.MAX_FREE_1.value;
        assertThat(gpu1.getGpuMemoryUtilization().getAverage())
                .isEqualTo((GpuMemoryMetric.AVG_USED_1.value / totalAvg1) * PERCENT_MULTIPLIER);
        assertThat(gpu1.getGpuMemoryUtilization().getMin())
                .isEqualTo((GpuMemoryMetric.MIN_USED_1.value / totalMin1) * PERCENT_MULTIPLIER);
        assertThat(gpu1.getGpuMemoryUtilization().getMax())
                .isEqualTo((GpuMemoryMetric.MAX_USED_1.value / totalMax1) * PERCENT_MULTIPLIER);

        //2
        assertThat(stat2.getGpuDeviceName()).isEqualTo(GPU_MODEL);
        Map<String, MonitoringStats.GPUUsage> details2 = stat2.getGpuDetails();
        assertThat(details2).hasSize(1);
        MonitoringStats.GPUUsage gpu2 = details2.get(GPU_NUMBER_0);
        assertThat(gpu2.getGpuUtilization().getAverage()).isEqualTo(GpuMetric.AVG_2.value);
        assertThat(gpu2.getGpuUtilization().getMin()).isEqualTo(GpuMetric.MIN_2.value);
        assertThat(gpu2.getGpuUtilization().getMax()).isEqualTo(GpuMetric.MAX_2.value);
        assertThat(gpu2.getGpuMemoryUsed().getAverage()).isEqualTo(GpuMemoryMetric.AVG_USED_2.value);
        assertThat(gpu2.getGpuMemoryUsed().getMin()).isEqualTo(GpuMemoryMetric.MIN_USED_2.value);
        assertThat(gpu2.getGpuMemoryUsed().getMax()).isEqualTo(GpuMemoryMetric.MAX_USED_2.value);
        assertThat(gpu2.getGpuMemoryFree().getAverage()).isEqualTo(GpuMemoryMetric.AVG_FREE_2.value);
        assertThat(gpu2.getGpuMemoryFree().getMin()).isEqualTo(GpuMemoryMetric.MIN_FREE_2.value);
        assertThat(gpu2.getGpuMemoryFree().getMax()).isEqualTo(GpuMemoryMetric.MAX_FREE_2.value);
        assertThat(gpu2.getActiveGpus().getAverage()).isEqualTo(GpuActiveMetric.AVG_2.value);
        assertThat(gpu2.getActiveGpus().getMin()).isEqualTo(GpuActiveMetric.MIN_2.value);
        assertThat(gpu2.getActiveGpus().getMax()).isEqualTo(GpuActiveMetric.MAX_2.value);
        // Verify GPU Memory Utilization
        double totalAvg2 = GpuMemoryMetric.AVG_USED_2.value + GpuMemoryMetric.AVG_FREE_2.value;
        double totalMin2 = GpuMemoryMetric.MIN_USED_2.value + GpuMemoryMetric.MIN_FREE_2.value;
        double totalMax2 = GpuMemoryMetric.MAX_USED_2.value + GpuMemoryMetric.MAX_FREE_2.value;
        assertThat(gpu2.getGpuMemoryUtilization().getAverage())
                .isEqualTo((GpuMemoryMetric.AVG_USED_2.value / totalAvg2) * PERCENT_MULTIPLIER);
        assertThat(gpu2.getGpuMemoryUtilization().getMin())
                .isEqualTo((GpuMemoryMetric.MIN_USED_2.value / totalMin2) * PERCENT_MULTIPLIER);
        assertThat(gpu2.getGpuMemoryUtilization().getMax())
                .isEqualTo((GpuMemoryMetric.MAX_USED_2.value / totalMax2) * PERCENT_MULTIPLIER);

        // Verify GpuUsage (Cross-GPU)
        //1
        MonitoringStats.GPUUsage usage1 = stat1.getGpuUsage();
        assertThat(usage1.getGpuUtilization().getAverage()).isEqualTo(GpuMetric.AVG_1.value);
        assertThat(usage1.getGpuUtilization().getMin()).isEqualTo(GpuMetric.MIN_1.value);
        assertThat(usage1.getGpuUtilization().getMax()).isEqualTo(GpuMetric.MAX_1.value);
        assertThat(usage1.getGpuMemoryUtilization().getAverage())
                .isEqualTo((GpuMemoryMetric.AVG_USED_1.value / totalAvg1) * PERCENT_MULTIPLIER);
        assertThat(usage1.getGpuMemoryUtilization().getMin())
                .isEqualTo((GpuMemoryMetric.MIN_USED_1.value / totalMin1) * PERCENT_MULTIPLIER);
        assertThat(usage1.getGpuMemoryUtilization().getMax())
                .isEqualTo((GpuMemoryMetric.MAX_USED_1.value / totalMax1) * PERCENT_MULTIPLIER);
        assertThat(usage1.getActiveGpus().getAverage()).isEqualTo(GpuActiveMetric.AVG_1.value);
        assertThat(usage1.getActiveGpus().getMin()).isEqualTo(GpuActiveMetric.MIN_1.value);
        assertThat(usage1.getActiveGpus().getMax()).isEqualTo(GpuActiveMetric.MAX_1.value);
        //2
        MonitoringStats.GPUUsage usage2 = stat2.getGpuUsage();
        assertThat(usage2.getGpuUtilization().getAverage()).isEqualTo(GpuMetric.AVG_2.value);
        assertThat(usage2.getGpuUtilization().getMin()).isEqualTo(GpuMetric.MIN_2.value);
        assertThat(usage2.getGpuUtilization().getMax()).isEqualTo(GpuMetric.MAX_2.value);
        assertThat(usage2.getGpuMemoryUtilization().getAverage())
                .isEqualTo((GpuMemoryMetric.AVG_USED_2.value / totalAvg2) * PERCENT_MULTIPLIER);
        assertThat(usage2.getGpuMemoryUtilization().getMin())
                .isEqualTo((GpuMemoryMetric.MIN_USED_2.value / totalMin2) * PERCENT_MULTIPLIER);
        assertThat(usage2.getGpuMemoryUtilization().getMax())
                .isEqualTo((GpuMemoryMetric.MAX_USED_2.value / totalMax2) * PERCENT_MULTIPLIER);
        assertThat(usage2.getActiveGpus().getAverage()).isEqualTo(GpuActiveMetric.AVG_2.value);
        assertThat(usage2.getActiveGpus().getMin()).isEqualTo(GpuActiveMetric.MIN_2.value);
        assertThat(usage2.getActiveGpus().getMax()).isEqualTo(GpuActiveMetric.MAX_2.value);

        // Verify Global
        MonitoringStats global = stats.getGlobal();
        assertThat(global.getGpuDeviceName()).isEqualTo(GPU_MODEL);
        MonitoringStats.GPUUsage globalUsage = global.getGpuUsage();
        assertThat(globalUsage.getGpuUtilization().getAverage())
                .isEqualTo((GpuMetric.AVG_1.value + GpuMetric.AVG_2.value) / 2.0);
        assertThat(globalUsage.getGpuUtilization().getMin())
                .isEqualTo(Math.min(GpuMetric.MIN_1.value, GpuMetric.MIN_2.value));
        assertThat(globalUsage.getGpuUtilization().getMax())
                .isEqualTo(Math.max(GpuMetric.MAX_1.value, GpuMetric.MAX_2.value));
        assertThat(globalUsage.getGpuMemoryUtilization().getAverage())
                .isEqualTo(((GpuMemoryMetric.AVG_USED_1.value / totalAvg1) * PERCENT_MULTIPLIER +
                        (GpuMemoryMetric.AVG_USED_2.value / totalAvg2) * PERCENT_MULTIPLIER) / 2.0);
        assertThat(globalUsage.getGpuMemoryUtilization().getMin())
                .isEqualTo(Math.min((GpuMemoryMetric.MIN_USED_1.value / totalMin1) * PERCENT_MULTIPLIER,
                        (GpuMemoryMetric.MIN_USED_2.value / totalMin2) * PERCENT_MULTIPLIER));
        assertThat(globalUsage.getGpuMemoryUtilization().getMax())
                .isEqualTo(Math.max((GpuMemoryMetric.MAX_USED_1.value / totalMax1) * PERCENT_MULTIPLIER,
                        (GpuMemoryMetric.MAX_USED_2.value / totalMax2) * PERCENT_MULTIPLIER));
        assertThat(globalUsage.getActiveGpus().getAverage())
                .isEqualTo((GpuActiveMetric.AVG_1.value + GpuActiveMetric.AVG_2.value) / 2.0);
        assertThat(globalUsage.getActiveGpus().getMin())
                .isEqualTo(Math.min(GpuActiveMetric.MIN_1.value, GpuActiveMetric.MIN_2.value));
        assertThat(globalUsage.getActiveGpus().getMax())
                .isEqualTo(Math.max(GpuActiveMetric.MAX_1.value, GpuActiveMetric.MAX_2.value));
    }

    private void setupCpuMetricsReverseOrder() {
        List<Point> meanPoints = Arrays.stream(CpuMetric.values())
                .filter(m -> m.name().startsWith(MEAN))
                .sorted((m1, m2) -> Long.compare(m2.point.timestamp, m1.point.timestamp)) // Reverse order
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries cpuMeanSeries = TimeSeries.newBuilder().addAllPoints(meanPoints).build();
        when(cpuMeanResponse.getPage()).thenReturn(timeSeriesPageCpuMean);
        when(timeSeriesPageCpuMean.iterateAll()).thenReturn(Collections.singleton(cpuMeanSeries));
    }

    private void setupGcpRegion() {
        gcpRegion.setProject(PROJECT_ID);
        gcpRegion.setProvider(CloudProvider.GCP);
        when(cloudRegionDao.loadDefaultRegion()).thenReturn(Optional.of(gcpRegion));
    }

    private void setupVmDetails() throws IOException {
        nodeInstance.setRegion(ZONE);
        when(nodesManager.getNode(NODE_NAME)).thenReturn(nodeInstance);

        Instance instance = Instance.newBuilder().setMachineType(MACHINE_TYPE_URL).setId(NODE_ID).build();
        when(gcpClient.buildInstancesClient(gcpRegion)).thenReturn(instancesClient);
        when(instancesClient.get(eq(PROJECT_ID), eq(ZONE), eq(NODE_NAME))).thenReturn(instance);

        MachineType machineType = MachineType.newBuilder().setMemoryMb(MEMORY_MB).setGuestCpus(CPU_NUMBER).build();
        when(gcpClient.buildMachineTypesClient(gcpRegion)).thenReturn(machineTypesClient);
        when(machineTypesClient.get(PROJECT_ID, ZONE, "e2-micro")).thenReturn(machineType);
    }

    private void setupCpuMetrics() {
        List<Point> meanPoints = Arrays.stream(CpuMetric.values())
                .filter(m -> m.name().startsWith(MEAN))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries cpuMeanSeries = TimeSeries.newBuilder().addAllPoints(meanPoints).build();
        when(cpuMeanResponse.getPage()).thenReturn(timeSeriesPageCpuMean);
        when(timeSeriesPageCpuMean.iterateAll()).thenReturn(Collections.singleton(cpuMeanSeries));

        List<Point> maxPoints = Arrays.stream(CpuMetric.values())
                .filter(m -> m.name().startsWith(MAX))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries cpuMaxSeries = TimeSeries.newBuilder().addAllPoints(maxPoints).build();
        when(cpuMaxResponse.getPage()).thenReturn(timeSeriesPageCpuMax);
        when(timeSeriesPageCpuMax.iterateAll()).thenReturn(Collections.singleton(cpuMaxSeries));
    }

    private void setupMemoryMetrics() {
        List<Point> meanPoints = Arrays.stream(MemoryMetric.values())
                .filter(m -> m.name().startsWith(MEAN))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries memoryMeanSeries = TimeSeries.newBuilder()
                .addAllPoints(meanPoints)
                .setMetric(Metric.newBuilder().putLabels("state", USED).build())
                .build();
        when(memoryMeanResponse.getPage()).thenReturn(timeSeriesPageMemoryMean);
        when(timeSeriesPageMemoryMean.iterateAll()).thenReturn(Collections.singleton(memoryMeanSeries));

        List<Point> maxPoints = Arrays.stream(MemoryMetric.values())
                .filter(m -> m.name().startsWith(MAX))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries memoryMaxSeries = TimeSeries.newBuilder()
                .addAllPoints(maxPoints)
                .setMetric(Metric.newBuilder().putLabels("state", USED).build())
                .build();
        when(memoryMaxResponse.getPage()).thenReturn(timeSeriesPageMemoryMax);
        when(timeSeriesPageMemoryMax.iterateAll()).thenReturn(Collections.singleton(memoryMaxSeries));
    }

    private void setupDiskMetrics() {
        Map<String, List<Point>> points = new HashMap<>();
        for (DiskMetric m : DiskMetric.values()) {
            String key = m.device + ":" + m.state;
            points.computeIfAbsent(key, k -> new ArrayList<>()).add(createPoint(m.point.timestamp, m.value));
        }

        List<TimeSeries> series = new ArrayList<>();
        for (Map.Entry<String, List<Point>> e : points.entrySet()) {
            String[] parts = e.getKey().split(":");
            TimeSeries ts = TimeSeries.newBuilder()
                    .addAllPoints(e.getValue())
                    .setMetric(Metric.newBuilder()
                            .putLabels("device", parts[0])
                            .putLabels("state", parts[1])
                            .build())
                    .build();
            series.add(ts);
        }
        when(diskMeanResponse.getPage()).thenReturn(timeSeriesPageDiskMean);
        when(timeSeriesPageDiskMean.iterateAll()).thenReturn(series);
    }

    private void setupNetworkMetrics() {
        List<Point> rxPoints = Arrays.stream(NetworkMetric.values())
                .filter(m -> m.name().startsWith("RX"))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries networkRxSeries = TimeSeries.newBuilder().addAllPoints(rxPoints).build();
        when(networkRxResponse.getPage()).thenReturn(timeSeriesPageNetworkRx);
        when(timeSeriesPageNetworkRx.iterateAll()).thenReturn(Collections.singleton(networkRxSeries));

        List<Point> txPoints = Arrays.stream(NetworkMetric.values())
                .filter(m -> m.name().startsWith("TX"))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries networkTxSeries = TimeSeries.newBuilder().addAllPoints(txPoints).build();
        when(networkTxResponse.getPage()).thenReturn(timeSeriesPageNetworkTx);
        when(timeSeriesPageNetworkTx.iterateAll()).thenReturn(Collections.singleton(networkTxSeries));
    }

    private Point createPoint(long timestamp, double value) {
        return Point.newBuilder()
                .setInterval(TimeInterval.newBuilder()
                        .setEndTime(Timestamp.newBuilder().setSeconds(timestamp).build())
                        .build())
                .setValue(TypedValue.newBuilder().setDoubleValue(value).build())
                .build();
    }

    private Point createPoint(long timestamp, long value) {
        return Point.newBuilder()
                .setInterval(TimeInterval.newBuilder()
                        .setEndTime(Timestamp.newBuilder().setSeconds(timestamp).build())
                        .build())
                .setValue(TypedValue.newBuilder().setInt64Value(value).build())
                .build();
    }

    private void setupGpuMetrics() {
        List<Point> avgPoints = Arrays.stream(GpuMetric.values())
            .filter(m -> m.name().startsWith(AVG))
            .map(m -> createPoint(m.point.timestamp, m.value))
            .collect(Collectors.toList());
        TimeSeries gpuAvgSeries = TimeSeries.newBuilder()
            .addAllPoints(avgPoints)
            .setMetric(Metric.newBuilder()
                .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                .putLabels("model", GPU_MODEL)
                .build())
            .build();
        when(gpuAvgResponse.getPage()).thenReturn(timeSeriesPageGpuAvg);
        when(timeSeriesPageGpuAvg.iterateAll()).thenReturn(Collections.singleton(gpuAvgSeries));

        List<Point> maxPoints = Arrays.stream(GpuMetric.values())
                .filter(m -> m.name().startsWith(MAX))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMaxSeries = TimeSeries.newBuilder()
            .addAllPoints(maxPoints)
            .setMetric(Metric.newBuilder()
                .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                .putLabels("model", GPU_MODEL)
                .build())
            .build();
        when(gpuMaxResponse.getPage()).thenReturn(timeSeriesPageGpuMax);
        when(timeSeriesPageGpuMax.iterateAll()).thenReturn(Collections.singleton(gpuMaxSeries));

        List<Point> minPoints = Arrays.stream(GpuMetric.values())
                .filter(m -> m.name().startsWith(MIN))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMinSeries = TimeSeries.newBuilder()
            .addAllPoints(minPoints)
            .setMetric(Metric.newBuilder()
                .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                .putLabels("model", GPU_MODEL)
                .build())
            .build();
        when(gpuMinResponse.getPage()).thenReturn(timeSeriesPageGpuMin);
        when(timeSeriesPageGpuMin.iterateAll()).thenReturn(Collections.singleton(gpuMinSeries));
    }

    private void setupGpuMemoryMetrics() {
        // Average points
        List<Point> avgPointsUsed = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(AVG) && MEMORY_STATE_USED.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryAvgUsedSeries = TimeSeries.newBuilder()
                .addAllPoints(avgPointsUsed)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_USED)
                        .build())
                .build();

        List<Point> avgPointsFree = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(AVG) && MEMORY_STATE_FREE.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryAvgFreeSeries = TimeSeries.newBuilder()
                .addAllPoints(avgPointsFree)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_FREE)
                        .build())
                .build();

        // Max points
        List<Point> maxPointsUsed = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(MAX) && MEMORY_STATE_USED.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryMaxUsedSeries = TimeSeries.newBuilder()
                .addAllPoints(maxPointsUsed)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_USED)
                        .build())
                .build();

        List<Point> maxPointsFree = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(MAX) && MEMORY_STATE_FREE.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryMaxFreeSeries = TimeSeries.newBuilder()
                .addAllPoints(maxPointsFree)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_FREE)
                        .build())
                .build();

        // Min points
        List<Point> minPointsUsed = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(MIN) && MEMORY_STATE_USED.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryMinUsedSeries = TimeSeries.newBuilder()
                .addAllPoints(minPointsUsed)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_USED)
                        .build())
                .build();

        List<Point> minPointsFree = Arrays.stream(GpuMemoryMetric.values())
                .filter(m -> m.name().startsWith(MIN) && MEMORY_STATE_FREE.equals(m.memoryState))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuMemoryMinFreeSeries = TimeSeries.newBuilder()
                .addAllPoints(minPointsFree)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .putLabels(MEMORY_STATE, MEMORY_STATE_FREE)
                        .build())
                .build();

        // Configure mocks
        when(gpuMemoryAvgResponse.getPage()).thenReturn(timeSeriesPageGpuMemoryAvg);
        when(timeSeriesPageGpuMemoryAvg.iterateAll())
                .thenReturn(Arrays.asList(gpuMemoryAvgUsedSeries, gpuMemoryAvgFreeSeries));
        when(gpuMemoryMaxResponse.getPage()).thenReturn(timeSeriesPageGpuMemoryMax);
        when(timeSeriesPageGpuMemoryMax.iterateAll())
                .thenReturn(Arrays.asList(gpuMemoryMaxUsedSeries, gpuMemoryMaxFreeSeries));
        when(gpuMemoryMinResponse.getPage()).thenReturn(timeSeriesPageGpuMemoryMin);
        when(timeSeriesPageGpuMemoryMin.iterateAll())
                .thenReturn(Arrays.asList(gpuMemoryMinUsedSeries, gpuMemoryMinFreeSeries));
    }

    private void setupGpuActiveMetrics() {
        List<Point> avgPoints = Arrays.stream(GpuActiveMetric.values())
                .filter(m -> m.name().startsWith(AVG))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuActiveAvgSeries = TimeSeries.newBuilder()
                .addAllPoints(avgPoints)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .build())
                .build();
        when(gpuActiveAvgResponse.getPage()).thenReturn(timeSeriesPageGpuActiveAvg);
        when(timeSeriesPageGpuActiveAvg.iterateAll()).thenReturn(Collections.singleton(gpuActiveAvgSeries));

        List<Point> maxPoints = Arrays.stream(GpuActiveMetric.values())
                .filter(m -> m.name().startsWith(MAX))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuActiveMaxSeries = TimeSeries.newBuilder()
                .addAllPoints(maxPoints)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .build())
                .build();
        when(gpuActiveMaxResponse.getPage()).thenReturn(timeSeriesPageGpuActiveMax);
        when(timeSeriesPageGpuActiveMax.iterateAll()).thenReturn(Collections.singleton(gpuActiveMaxSeries));

        List<Point> minPoints = Arrays.stream(GpuActiveMetric.values())
                .filter(m -> m.name().startsWith(MIN))
                .map(m -> createPoint(m.point.timestamp, m.value))
                .collect(Collectors.toList());
        TimeSeries gpuActiveMinSeries = TimeSeries.newBuilder()
                .addAllPoints(minPoints)
                .setMetric(Metric.newBuilder()
                        .putLabels(GPU_NUMBER, GPU_NUMBER_0)
                        .build())
                .build();
        when(gpuActiveMinResponse.getPage()).thenReturn(timeSeriesPageGpuActiveMin);
        when(timeSeriesPageGpuActiveMin.iterateAll()).thenReturn(Collections.singleton(gpuActiveMinSeries));
    }

}
