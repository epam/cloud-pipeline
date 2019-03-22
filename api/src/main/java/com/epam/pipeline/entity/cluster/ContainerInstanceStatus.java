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

package com.epam.pipeline.entity.cluster;

import io.fabric8.kubernetes.api.model.ContainerState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerInstanceStatus {

    private String status;
    private String reason;
    private String message;
    private String timestamp;

    public ContainerInstanceStatus() {}

    public ContainerInstanceStatus(ContainerState state) {
        this();
        if (state.getRunning() != null) {
            this.status = "Running";
            this.timestamp = state.getRunning().getStartedAt();
        } else if (state.getTerminated() != null) {
            this.status = "Terminated";
            this.timestamp = state.getTerminated().getFinishedAt();
            this.message = state.getTerminated().getMessage();
            this.reason = state.getTerminated().getReason();
        } else if (state.getWaiting() != null) {
            this.status = "Waiting";
            this.message = state.getWaiting().getMessage();
            this.reason = state.getWaiting().getReason();
        } else {
            this.status = "Unknown";
        }
    }

}
