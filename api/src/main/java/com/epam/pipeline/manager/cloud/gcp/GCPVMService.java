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

import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.cloud.gcp.GCPException;
import com.google.api.services.compute.model.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service
public class GCPVMService {

    private static final String RUN_ID_LABEL_NAME = "name";


    private final GCPClient gcpClient;

    public GCPVMService(GCPClient gcpClient) {
        this.gcpClient = gcpClient;
    }

    public void startInstance(final GCPRegion region, final String instanceId) {
        try {
            gcpClient.buildComputeClient(region).instances()
            .start(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
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

    Instance getRunningInstanceByRunId(GCPRegion region, String runId) {
        try {
            Instance instance = findInstanceByTag(region, RUN_ID_LABEL_NAME, runId);

            GCPInstanceStatus instanceStatus = GCPInstanceStatus.valueOf(instance.getStatus());
            if (GCPInstanceStatus.getWorkingStatuses().contains(instanceStatus)) {
                return instance;
            } else {
                throw new GCPException("VM with ID: " + runId +
                        " not in a running state, current state: " + instance.getStatus());
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    Optional<InstanceTerminationState> getTerminationState(GCPRegion region, String instanceId) {
        Instance instance = getInstanceById(region, instanceId);
        if (instance != null && instance.getStatus().equals(GCPInstanceStatus.TERMINATED.name())) {
            return Optional.of(
                    InstanceTerminationState.builder()
                            .instanceId(instanceId)
                            .stateCode(GCPInstanceStatus.TERMINATED.name())
                            .stateMessage(instance.getStatusMessage()).build()
            );
        }
        return Optional.empty();
    }

    private Instance getInstanceById(GCPRegion region, String instanceId) {
        try {
            return gcpClient.buildComputeClient(region).instances()
                    .get(region.getProject(), region.getRegionCode(), instanceId).execute();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPException(e);
        }
    }

    private Instance findInstanceByTag(GCPRegion region, String key, String value) throws IOException {
        return gcpClient.buildComputeClient(region).instances()
                .list(region.getProject(), region.getRegionCode()).setFilter("labels." + key + "=\"" + value + "\"")
                .execute().getItems().stream().findFirst()
                .orElseThrow(() -> new GCPException("Instance with label: '" + key + " : " + value + "' not found!"));
    }
}
