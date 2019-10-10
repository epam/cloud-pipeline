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

package com.epam.pipeline.manager.cluster.performancemonitoring;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.NodeInstanceAddress;
import com.epam.pipeline.entity.cluster.monitoring.MonitoringStats;
import com.epam.pipeline.entity.cluster.monitoring.RawMonitoringStats;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.cluster.NodesManager;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "monitoring.backend", havingValue = "cadvisor", matchIfMissing = true)
public class CAdvisorMonitoringManager implements UsageMonitoringManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CAdvisorMonitoringManager.class);

    private static final String CORE_MASK_DELIMETER = "-";
    private static final int KILO = 1000;

    private int cAdvisorPort;

    private NodesManager nodesManager;

    private JsonMapper objectMapper;

    private MessageHelper messageHelper;

    private KubernetesManager kubernetesManager;

    private OkHttpClient client;

    @Autowired
    public CAdvisorMonitoringManager(
            NodesManager nodesManager,
            MessageHelper messageHelper,
            JsonMapper objectMapper,
            KubernetesManager kubernetesManager,
            @Value("${cluster.cadvisor.timeout:5}") int timeout,
            @Value("${cluster.cadvisor.port:4194}") int cAdvisorPort,
            @Value("${cluster.cadvisor.disable.proxy:false}") boolean disableProxyForCadvisor) {
        this.nodesManager = nodesManager;
        this.messageHelper = messageHelper;
        this.objectMapper = objectMapper;
        this.cAdvisorPort = cAdvisorPort;
        this.kubernetesManager = kubernetesManager;

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder builder = new OkHttpClient().newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .addInterceptor(interceptor);
        if (disableProxyForCadvisor) {
            LOGGER.debug("Disabling proxy for CAdvisor requests.");
            builder.proxy(Proxy.NO_PROXY);
        }
        this.client = builder.build();
    }

    @Override
    public List<MonitoringStats> getStatsForNode(final String nodeName, final LocalDateTime from,
                                                 final LocalDateTime to) {
        final LocalDateTime start = Optional.ofNullable(from).orElse(LocalDateTime.MIN);
        final LocalDateTime end = Optional.ofNullable(to).orElse(LocalDateTime.MAX);
        return getStats(nodeName, start, end);
    }

    @Override
    public long getDiskAvailableForDocker(final String nodeName,
                                          final String podId,
                                          final String dockerImage) {
        final String dockerDiskName = getDockerDiskName(nodeName, podId, dockerImage);
        final MonitoringStats monitoringStats = getLastMonitoringStat(getStatsForNode(nodeName), nodeName);
        final MonitoringStats.DisksUsage.DiskStats diskStats = monitoringStats.getDisksUsage()
                .getStatsByDevices()
                .entrySet()
                .stream()
                .filter(e -> dockerDiskName.equals(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(messageHelper.getMessage(
                                MessageConstants.ERROR_GET_NODE_STAT, nodeName)));

        return diskStats.getCapacity() - diskStats.getUsableSpace();
    }

    public List<MonitoringStats> getStats(final String nodeName) {
        return getInternalip(nodeName)
                .map(this::executeStatsRequest)
                .orElse(Collections.emptyList());
    }

    private List<MonitoringStats> getStats(final String nodeName, final LocalDateTime start, final LocalDateTime end) {
        return getStats(nodeName)
                .stream()
                .filter(stats -> dateTime(stats.getStartTime()).compareTo(start) >= 0
                        && dateTime(stats.getEndTime()).compareTo(end) <= 0)
                .collect(Collectors.toList());
    }

    private static LocalDateTime dateTime(final String dateTime) {
        return LocalDateTime.parse(dateTime, MonitoringConstants.FORMATTER);
    }

    private List<MonitoringStats> getStatsForContainerDisk(final String nodeName,
                                                           final String podId,
                                                           final String dockerImage) {
        final String containerId = kubernetesManager.getContainerIdFromKubernetesPod(podId, dockerImage);
        return getInternalip(nodeName)
                .map(ip -> executeContainerRequest(ip, containerId))
                .orElse(Collections.emptyList());
    }

    private List<MonitoringStats> executeStatsRequest(final String nodeIP) {
        return executeStatsRequest(nodeIP, api -> api.getStatsForNode().execute());
    }

    private List<MonitoringStats> executeContainerRequest(final String nodeIP,
                                                          final String containerId) {
        return executeStatsRequest(nodeIP, api -> api.getStatsForContainer(containerId).execute());
    }

    private <T extends RawMonitoringStats> List<MonitoringStats> executeStatsRequest(
            final String nodeIP, final ClientCall<T> clientCall) {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_SEND_CADVISOR_REQUEST, nodeIP));
        CAdvisorMonitoringApi cAdvisorMonitoringApi = buildRetrofitReceiver(nodeIP);
        try {
            Response<T> response = clientCall.call(cAdvisorMonitoringApi);
            if (response.isSuccessful() && response.body() != null) {
                LOGGER.info("Successfully send request to node with IP :" + nodeIP);
                LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_RECEIVE_CADVISOR_RESPONSE, nodeIP));
                return convertFromRawToMonitoringStatsList(response.body());
            } else {
                throw new IOException("Receiving stats from node failed  with response code:" + response.code());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Receiving stats from node failed with error: " + e);
        }
    }

    private Optional<String> getInternalip(final String nodeName) {
        return nodesManager.getNode(nodeName)
                .getAddresses()
                .stream()
                .filter(a -> a.getType() != null && a.getType().equalsIgnoreCase("internalip"))
                .findFirst()
                .map(NodeInstanceAddress::getAddress);
    }

    private CAdvisorMonitoringApi buildRetrofitReceiver(String nodeIP) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(String.format("http://%s:%s/api/v1.3/containers/", nodeIP, String.valueOf(cAdvisorPort)))
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();
        return retrofit.create(CAdvisorMonitoringApi.class);
    }

    private static List<MonitoringStats> convertFromRawToMonitoringStatsList(RawMonitoringStats rawMonitoringStats) {
        ArrayList<MonitoringStats> monitoringStatsList = new ArrayList<>();
        RawMonitoringStats.RawSpec rawSpec = rawMonitoringStats.getSpec();
        List<RawMonitoringStats.RawStats> rawStats = rawMonitoringStats.getStats();

        Iterator<RawMonitoringStats.RawStats> iterator = rawStats.iterator();
        RawMonitoringStats.RawStats current = iterator.next();
        while (iterator.hasNext()) {
            RawMonitoringStats.RawStats next = iterator.next();
            MonitoringStats stats = matchToMonitoringStats(current, next, rawSpec);
            monitoringStatsList.add(stats);
            current = next;
        }
        return monitoringStatsList;
    }

    private static MonitoringStats matchToMonitoringStats(RawMonitoringStats.RawStats start,
                                                          RawMonitoringStats.RawStats end,
                                                          RawMonitoringStats.RawSpec rawSpec) {
        MonitoringStats monitoringStats = new MonitoringStats();
        monitoringStats.setStartTime(start.getTimestamp());
        monitoringStats.setEndTime(end.getTimestamp());

        long millsInPeriod = getMills(monitoringStats.getEndTime()) - getMills(monitoringStats.getStartTime());
        monitoringStats.setMillsInPeriod(millsInPeriod);

        long maxMemory = Optional.ofNullable(rawSpec.getMemory())
                .map(RawMonitoringStats.SpecMemory::getLimit)
                .orElse(0L);
        monitoringStats.setContainerSpec(new MonitoringStats.ContainerSpec());
        monitoringStats.getContainerSpec().setMaxMemory(maxMemory);
        monitoringStats.getContainerSpec().setNumberOfCores(Optional.ofNullable(rawSpec.getCpu())
                .map(cpu -> cpu.getMask().split(CORE_MASK_DELIMETER).length)
                .orElse(0));

        if (rawSpec.isHasCpu()) {
            monitoringStats.setCpuUsage(fetchCPUUsage(start, end, millsInPeriod));
        }
        if (rawSpec.isHasMemory()) {
            monitoringStats.setMemoryUsage(fetchMemoryUsage(start, maxMemory));
        }
        if (rawSpec.isHasFilesystem()) {
            monitoringStats.setDisksUsage(fetchDiskUsage(start));
        }
        if (rawSpec.isHasNetwork()) {
            monitoringStats.setNetworkUsage(fetchNetworkUsage(start, end, millsInPeriod));
        }
        return monitoringStats;
    }

    private static MonitoringStats.CPUUsage fetchCPUUsage(RawMonitoringStats.RawStats start,
                                                          RawMonitoringStats.RawStats end,
                                                          long millsInPeriod) {
        MonitoringStats.CPUUsage cpuUsage = new MonitoringStats.CPUUsage();
        long nanoInPeriod = millsInPeriod * KILO * KILO;
        long startCPUUsage = start.getCpu().getUsage().getTotal();
        long endCPUUsage = end.getCpu().getUsage().getTotal();
        cpuUsage.setLoad((double) (endCPUUsage - startCPUUsage) / nanoInPeriod);
        return cpuUsage;
    }

    private static MonitoringStats.MemoryUsage fetchMemoryUsage(RawMonitoringStats.RawStats stats, long maxMemory) {
        MonitoringStats.MemoryUsage memoryUsage = new MonitoringStats.MemoryUsage();
        RawMonitoringStats.StatsMemory memory = stats.getMemory();
        memoryUsage.setCapacity(maxMemory);
        memoryUsage.setUsage(memory.getUsage());
        return memoryUsage;
    }

    private static MonitoringStats.DisksUsage fetchDiskUsage(RawMonitoringStats.RawStats stats) {
        MonitoringStats.DisksUsage diskUsage = new MonitoringStats.DisksUsage();
        stats.getFilesystem().iterator().forEachRemaining(filesystem -> {
            MonitoringStats.DisksUsage.DiskStats diskStats = new MonitoringStats.DisksUsage.DiskStats();
            diskStats.setCapacity(filesystem.getCapacity());
            diskStats.setUsableSpace(filesystem.getUsage());
            diskUsage.getStatsByDevices().put(filesystem.getDevice(), diskStats);
        });
        return diskUsage;
    }

    private static MonitoringStats.NetworkUsage fetchNetworkUsage(RawMonitoringStats.RawStats start,
                                                                  RawMonitoringStats.RawStats end,
                                                                  long millsInPeriod) {
        MonitoringStats.NetworkUsage networkUsage = new MonitoringStats.NetworkUsage();
        long secsInPeriod = millsInPeriod / KILO;

        start.getNetwork().getInterfaces().iterator().forEachRemaining(interfaceStartValue -> {
            MonitoringStats.NetworkUsage.NetworkStats networkStats = new MonitoringStats.NetworkUsage.NetworkStats();
            String interfaceName = interfaceStartValue.getName();

            RawMonitoringStats.NetworkInterface interfaceEndValues = null;
            for (RawMonitoringStats.NetworkInterface next : end.getNetwork().getInterfaces()) {
                if (next.getName().equals(interfaceName)) {
                    interfaceEndValues = next;
                    break;
                }
            }

            long startRxBytes = interfaceStartValue.getRxBytes();
            Assert.notNull(interfaceEndValues, "Invalid monitoring statistics.");
            long endRxBytes = interfaceEndValues.getRxBytes();
            networkStats.setRxBytes((endRxBytes - startRxBytes) / secsInPeriod);

            long startTxBytes = interfaceStartValue.getTxBytes();
            long endTxBytes = interfaceEndValues.getTxBytes();
            networkStats.setTxBytes((endTxBytes - startTxBytes) / secsInPeriod);

            networkUsage.getStatsByInterface().put(interfaceName, networkStats);
        });
        return networkUsage;
    }


    private static long getMills(String dateTime) {
        return dateTime(dateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String getDockerDiskName(final String nodeName, final String podId, final String dockerImage) {
        final MonitoringStats monitoringStats = getLastMonitoringStat(
                getStatsForContainerDisk(nodeName, podId, dockerImage), nodeName);

        return monitoringStats.getDisksUsage()
                .getStatsByDevices().keySet()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_GET_NODE_STAT, nodeName)));
    }

    private MonitoringStats getLastMonitoringStat(final List<MonitoringStats> statsByPeriod,
                                                  final String nodeName) {
        Assert.isTrue(CollectionUtils.isNotEmpty(statsByPeriod),
                messageHelper.getMessage(MessageConstants.ERROR_GET_NODE_STAT, nodeName));

        final MonitoringStats monitoringStats = statsByPeriod.get(statsByPeriod.size() - 1);
        Assert.isTrue(Objects.nonNull(monitoringStats) && Objects.nonNull(monitoringStats.getDisksUsage())
                        && MapUtils.isNotEmpty(monitoringStats.getDisksUsage().getStatsByDevices()),
                messageHelper.getMessage(MessageConstants.ERROR_GET_NODE_STAT, nodeName));
        return monitoringStats;
    }

    @FunctionalInterface
    private interface ClientCall<T extends RawMonitoringStats> {
        Response<T> call(CAdvisorMonitoringApi api) throws IOException;
    }
}
