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
 *
 */

package com.epam.pipeline.vmmonitor.service.pipeline;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.filter.FilterExpression;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.utils.QueryUtils;
import com.epam.pipeline.vo.FilterNodesVO;
import com.epam.pipeline.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.vo.notification.NotificationMessageVO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CloudPipelineAPIClient {

    private static final APIVersion REGION_CHANGE_VERSION = new APIVersion("0.15");
    private static final APIVersion USER_CHANGE_VERSION = new APIVersion("0.14");
    public static final int SEARCH_PAGE_SIZE = 20;

    private final CloudPipelineAPI cloudPipelineAPI;
    private final APIVersion apiVersion;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") String cloudPipelineToken,
                                  @Value("${cloud.pipeline.api.version}") String apiVersion) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(3, 3, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
        this.apiVersion = new APIVersion(apiVersion);
    }

    public List<? extends AbstractCloudRegion> loadRegions() {
        if (apiVersion.compareTo(REGION_CHANGE_VERSION) < 0) {
            return QueryUtils.execute(cloudPipelineAPI.loadAwsRegions());
        }
        return QueryUtils.execute(cloudPipelineAPI.loadAllRegions());
    }

    public List<NodeInstance> findNodes(final String ip) {
        final FilterNodesVO filterNodesVO = new FilterNodesVO();
        filterNodesVO.setAddress(ip);
        return QueryUtils.execute((cloudPipelineAPI.findNodes(filterNodesVO)));
    }

    public NotificationMessage sendNotification(final NotificationMessageVO messageVO) {
        return QueryUtils.execute(cloudPipelineAPI.createNotification(messageVO));
    }

    public PipelineUser loadUserByName(final String userName) {
        if (apiVersion.compareTo(USER_CHANGE_VERSION) < 0) {
            final List<PipelineUser> users = QueryUtils.execute(cloudPipelineAPI.loadUsersByPrefix(userName));
            return ListUtils.emptyIfNull(users)
                    .stream()
                    .filter(user -> user.getUserName().equals(userName))
                    .findFirst()
                    .orElseThrow(() -> new PipelineResponseException("Failed to find user by name " + userName));
        }
        return QueryUtils.execute(cloudPipelineAPI.loadUserByName(userName));
    }

    public PipelineRun loadRun(final Long runId) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipelineRun(runId));
    }

    public List<PipelineRun> searchRunsByInstanceId(final String instanceId) {
        final List<PipelineRun> searchResults = new ArrayList<>();
        final PagedResult<List<PipelineRun>> initialSearchResult = addSearchResults(instanceId, 1, searchResults);
        final int totalPages = initialSearchResult.getTotalCount() % SEARCH_PAGE_SIZE + 1;
        for (int pageNumber = 2; pageNumber <= totalPages; pageNumber++) {
            addSearchResults(instanceId, pageNumber, searchResults);
        }
        return searchResults;
    }

    private PagedResult<List<PipelineRun>> addSearchResults(final String instanceId, final int pageNumber,
                                                            final List<PipelineRun> searchResults) {
        final PagedResult<List<PipelineRun>> initialSearchResult = QueryUtils.execute(
            cloudPipelineAPI.searchPipelineRuns(buildRunByInstanceSearchExpression(instanceId, pageNumber)));
        searchResults.addAll(CollectionUtils.emptyIfNull(initialSearchResult.getElements()));
        return initialSearchResult;
    }

    private PagingRunFilterExpressionVO buildRunByInstanceSearchExpression(final String instanceId, final int page) {
        final PagingRunFilterExpressionVO searchExpression = new PagingRunFilterExpressionVO();
        searchExpression.setPage(page);
        searchExpression.setPageSize(SEARCH_PAGE_SIZE);
        final FilterExpression filterExpression = new FilterExpression();
        filterExpression.setField("node.name");
        filterExpression.setFilterExpressionType("LOGICAL");
        filterExpression.setValue(instanceId);
        filterExpression.setOperand("=");
        searchExpression.setFilterExpression(filterExpression);
        searchExpression.setTimezoneOffsetInMinutes(
            ZonedDateTime.now().getOffset().getTotalSeconds() / DateTimeConstants.SECONDS_PER_MINUTE);
        return searchExpression;
    }

    public List<NodePool> loadNodePools() {
        return QueryUtils.execute(cloudPipelineAPI.loadNodePools());
    }

    public static class APIVersion implements Comparable<APIVersion> {
        private String version;

        public APIVersion(final String version) {
            if(StringUtils.isBlank(version)) {
                throw new IllegalArgumentException("Version cannot be empty");
            }
            if(!version.matches("[0-9]+(\\.[0-9]+)*")) {
                throw new IllegalArgumentException("Invalid version format");
            }
            this.version = version;
        }

        @Override
        public int compareTo(final APIVersion that) {
            if(that == null) {
                return 1;
            }
            final String[] thisParts = this.version.split("\\.");
            final String[] thatParts = that.version.split("\\.");
            final int length = Math.max(thisParts.length, thatParts.length);
            for(int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ?
                        Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ?
                        Integer.parseInt(thatParts[i]) : 0;
                if(thisPart < thatPart) {
                    return -1;
                }
                if(thisPart > thatPart) {
                    return 1;
                }
            }
            return 0;
        }
    }
}
