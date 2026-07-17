/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.rest;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MachineType;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.vo.platformusage.PlatformUsageCreditsEventFilterVO;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUpdateRule;
import com.epam.pipeline.entity.platformusage.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.vo.FilterNodesVO;
import com.epam.pipeline.vo.PagingRunFilterVO;
import com.epam.pipeline.vo.cluster.pool.NodePoolUsage;
import com.epam.pipeline.vo.user.OnlineUsers;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CloudPipelineAPIClient {
    private final CloudPipelineAPI cloudPipelineAPI;
    private final CloudPipelineApiExecutor executor;
    private final JsonMapper jsonMapper;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") final String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") final String cloudPipelineToken,
                                  final CloudPipelineApiExecutor cloudPipelineApiExecutor) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
        this.executor = cloudPipelineApiExecutor;
        this.jsonMapper = new JsonMapper();
        this.jsonMapper.init();
    }

    public OnlineUsers saveOnlineUsers() {
        return executor.execute(cloudPipelineAPI.saveOnlineUsers());
    }

    public boolean deleteExpiredOnlineUsers(final String date) {
        return executor.execute(cloudPipelineAPI.deleteExpiredOnlineUsers(date));
    }

    public Integer getIntPreference(final String preferenceName) {
        final Preference preference = executor.execute(cloudPipelineAPI.loadPreference(preferenceName));
        if (Objects.isNull(preference) || StringUtils.isBlank(preference.getValue())) {
            return null;
        }
        return Integer.parseInt(preference.getValue());
    }

    public boolean getBooleanPreference(final String preferenceName) {
        final Preference preference = executor.execute(cloudPipelineAPI.loadPreference(preferenceName));
        if (Objects.isNull(preference) || StringUtils.isBlank(preference.getValue())) {
            return false;
        }
        return Boolean.parseBoolean(preference.getValue());
    }

    public <T> T getObjectPreference(final String preferenceName) {
        final Preference preference = executor.execute(cloudPipelineAPI.loadPreference(preferenceName));
        if (Objects.isNull(preference) || StringUtils.isBlank(preference.getValue())) {
            return null;
        }
        return jsonMapper.parseData(preference.getValue(), new TypeReference<T>() {});
    }

    public List<Preference> getAllPreferences() {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadAllPreference()));
    }

    public PipelineUser loadUserByName(final String username) {
        return executor.execute(cloudPipelineAPI.loadUserByName(username));
    }

    public List<NodePool> loadAllNodePools() {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadNodePools()));
    }

    public List<PipelineRun> loadRunsByPool(final Long poolId) {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadRunsByPool(poolId)));
    }

    public List<NodePoolUsage> saveNodePoolUsage(final List<NodePoolUsage> records) {
        return executor.execute(cloudPipelineAPI.saveNodePoolUsage(records));
    }

    public boolean deleteExpiredNodePoolUsage(final LocalDate date) {
        return executor.execute(cloudPipelineAPI.deleteExpiredNodePoolUsage(date));
    }

    public List<InstanceType> loadAllInstanceTypes() {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadAllInstanceTypes()));
    }

    public List<? extends AbstractCloudRegion> loadAllRegions() {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadAllRegions()));
    }

    public AllowedInstanceAndPriceTypes loadAllowedInstanceAndPriceTypesForRegion(final Long regionId) {
        return executor.execute(cloudPipelineAPI.loadAllowedInstanceAndPriceTypesForRegion(regionId));
    }

    public void archiveRuns() {
        executor.execute(cloudPipelineAPI.archiveRuns());
    }

    public List<PodInstance> filterPods(final Map<String, String> monitoredLabels) {
        return executor.execute(cloudPipelineAPI.filterPods(monitoredLabels));
    }

    public List<NodeInstance> filterNodes(final FilterNodesVO filter, final MachineType machineType) {
        return executor.execute(cloudPipelineAPI.filterNodes(filter, machineType));
    }

    private static final int RUN_FILTER_PAGE_SIZE = 1000;

    public List<PlatformUsageCreditsUpdateRule> loadAllPlatformUsageCreditsRules() {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.loadAllPlatformUsageCreditsRules()));
    }

    public List<PipelineRun> filterRuns(final PagingRunFilterVO filter) {
        final List<PipelineRun> result = new ArrayList<>();
        int page = 1;
        List<PipelineRun> chunk;
        do {
            filter.setPage(page++);
            filter.setPageSize(RUN_FILTER_PAGE_SIZE);
            final PagedResult<List<PipelineRun>> pagedResult = executor.execute(
                    cloudPipelineAPI.filterRuns(filter));
            chunk = pagedResult != null ? ListUtils.emptyIfNull(pagedResult.getElements())
                                        : Collections.emptyList();
            result.addAll(chunk);
        } while (chunk.size() == RUN_FILTER_PAGE_SIZE);
        return result;
    }

    public List<PlatformUsageCreditsUpdateEvent> filterPlatformUsageCreditsEvents(
            final PlatformUsageCreditsEventFilterVO filter) {
        final PagedResult<List<PlatformUsageCreditsUpdateEvent>> result =
                executor.execute(cloudPipelineAPI.filterPlatformUsageCreditsEvents(filter));
        return result != null ? ListUtils.emptyIfNull(result.getElements()) : Collections.emptyList();
    }

    public List<PlatformUsageCreditsUpdateEvent> savePlatformUsageCreditsEvents(
            final List<PlatformUsageCreditsUpdateEvent> events) {
        return ListUtils.emptyIfNull(executor.execute(cloudPipelineAPI.savePlatformUsageCreditsEvents(events)));
    }

    public PlatformUsageCreditsUserBalance loadPlatformUsageCreditsUserBalance(final Long userId) {
        return executor.execute(cloudPipelineAPI.loadPlatformUsageCreditsUserBalance(userId));
    }
}
