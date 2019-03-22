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

package com.epam.pipeline.manager.docker.scan.dockercompscan;

import com.epam.pipeline.entity.scan.ToolDependency;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DockerComponentLayerScanResult {

    public DockerComponentLayerScanResult(String layerId, Status status, String parentId) {
        this.layerId = layerId;
        this.status = status;
        this.parentId = parentId;
    }

    private String layerId;
    private Status status;
    private String parentId;

    private List<ToolDependency> dependencies = Collections.emptyList();

    public enum Status {
        RUNNING, SUCCESSFUL, FAILURE
    }

}
