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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GCPVMService {

    private static final String RUN_ID_LABEL_NAME = "name";
    private static final String LABEL_FILTER = "labels.%s=\"%s\"";
    private static final String STATUS_FILTER = "status=\"%s\"";
    private static final String OR = " OR ";
    private static final String AND = " AND ";
    private static final String OPENING_BRACKET = " ( ";
    private static final String CLOSING_BRACKET = " ) ";
    private static final String COMPUTE_OPERATIONS_FILTER = "targetLink eq .*%s";
    private static final String COMPUTE_INSTANCES_PREEMPTED = "compute.instances.preempted";
    private static final String COMPUTE_INSTANCE_DELETED = "delete";
    private static final String INSTANCE_WAS_PREEMPTED = "Instance was preempted.";
    private static final String INSTANCE_WAS_TERMINATED = "Instance was terminated.";


    private final GCPClient gcpClient;
    private final MessageHelper messageHelper;

    public CloudInstanceOperationResult startInstance(final GCPRegion region, final String instanceId) {
        try {
            gcpClient.buildComputeClient(region).instances()
            .start(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
        return CloudInstanceOperationResult.success(
                messageHelper.getMessage(MessageConstants.INFO_INSTANCE_STARTED, instanceId)
        );
    }

    public void stopInstance(final GCPRegion region, final String instanceId) {
        try {
            gcpClient.buildComputeClient(region).instances()
                    .stop(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    public Instance getRunningInstanceByRunId(final GCPRegion region, final String runId) {
        try {
            final Instance instance = findInstanceByTag(region, RUN_ID_LABEL_NAME, runId);

            final GCPInstanceStatus instanceStatus = GCPInstanceStatus.valueOf(instance.getStatus());
            if (GCPInstanceStatus.getWorkingStatuses().contains(instanceStatus)) {
                return instance;
            } else {
                throw new GCPException(
                        messageHelper.getMessage(MessageConstants.ERROR_GCP_INSTANCE_NOT_RUNNING,
                                runId, instanceStatus));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    public Optional<InstanceTerminationState> getTerminationState(final GCPRegion region, final String instanceId) {
        try {
            final List<Operation> operations = gcpClient.buildComputeClient(region)
                    .zoneOperations()
                    .list(region.getProject(), region.getRegionCode())
                    .setFilter(String.format(COMPUTE_OPERATIONS_FILTER, instanceId))
                    .execute().getItems();

            return CollectionUtils.emptyIfNull(operations)
                    .stream()
                    .map(Operation::getOperationType)
                    .filter(op -> op.equals(COMPUTE_INSTANCE_DELETED) || op.equals(COMPUTE_INSTANCES_PREEMPTED))
                    .distinct()
                    // alphabetic comparator is enough because we lust need to put
                    // compute.instances.preempted before delete
                    .sorted()
                    .findFirst()
                    .map(op -> InstanceTerminationState.builder()
                            .instanceId(instanceId)
                            .stateCode(op.equals(COMPUTE_INSTANCES_PREEMPTED)
                                    ? GCPInstanceStatus.PREEMPTED.name()
                                    : GCPInstanceStatus.TERMINATED.name()
                            )
                            .stateMessage(op.equals(COMPUTE_INSTANCES_PREEMPTED)
                                    ? INSTANCE_WAS_PREEMPTED
                                    : INSTANCE_WAS_TERMINATED
                            ).build());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Instance findInstanceByTag(final GCPRegion region,
                                       final String key,
                                       final String value) throws IOException {
        return ListUtils.emptyIfNull(
                gcpClient.buildComputeClient(region).instances()
                        .list(region.getProject(), region.getRegionCode())
                        .setFilter(String.format(LABEL_FILTER, key, value))
                        .execute()
                        .getItems())
                .stream().findFirst()
                .orElseThrow(() -> new GCPException(messageHelper.getMessage(
                        MessageConstants.ERROR_GCP_INSTANCE_NOT_FOUND, key + ":" + value)));
    }

    public void terminateInstance(final GCPRegion region, final String instanceId) {
        try {
            gcpClient.buildComputeClient(region)
                    .instances()
                    .delete(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    public boolean instanceExists(final GCPRegion region, final String instanceId) {
        try {
            return  null != gcpClient.buildComputeClient(region)
                    .instances().get(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    public Instance getAliveInstance(final GCPRegion region, final String runId) {
        try {
            return  ListUtils.emptyIfNull(
                    gcpClient.buildComputeClient(region).instances()
                            .list(region.getProject(), region.getRegionCode())
                            .setFilter(String.format(LABEL_FILTER, RUN_ID_LABEL_NAME, runId) +
                                    AND +
                                    OPENING_BRACKET +
                                    String.format(STATUS_FILTER, "RUNNING") +
                                    OR + String.format(STATUS_FILTER, "STOPPING") +
                                    OR + String.format(STATUS_FILTER, "STOPPED") +
                                    OR + String.format(STATUS_FILTER, "PROVISIONING") +
                                    OR + String.format(STATUS_FILTER, "STAGING") +
                                    CLOSING_BRACKET)
                            .execute()
                            .getItems())
                    .stream().findFirst()
                    .orElseThrow(() -> new GCPException(messageHelper.getMessage(
                            MessageConstants.ERROR_GCP_INSTANCE_NOT_FOUND)));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }
}
