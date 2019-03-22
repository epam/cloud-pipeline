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

import io.fabric8.kubernetes.api.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PodInstance {

    private UUID uid;
    private String name;
    private String namespace;
    private String nodeName;
    private String phase;

    private List<ContainerInstance> containers;

    public PodInstance() {
        containers = new ArrayList<>();
    }

    public PodInstance(Pod pod) {
        this();
        ObjectMeta metadata = pod.getMetadata();
        if (metadata != null) {
            this.setUid(UUID.fromString(metadata.getUid()));
            this.setName(metadata.getName());
            this.setNamespace(metadata.getNamespace());
        }
        PodStatus podStatus = pod.getStatus();
        if (podStatus != null) {
            this.setPhase(podStatus.getPhase());
        }
        PodSpec podSpec = pod.getSpec();
        if (podSpec != null) {
            this.setNodeName(podSpec.getNodeName());
            if (podStatus != null) {
                this.containers = ContainerInstance.convertToInstances(
                        podSpec.getContainers(),
                        podStatus.getContainerStatuses()
                    );
            } else {
                this.containers = ContainerInstance.convertToInstances(
                        podSpec.getContainers()
                    );
            }
        }
    }

    public static List<PodInstance> convertToInstances(PodList podList) {
        return podList.getItems().stream().map(PodInstance::new).collect(Collectors.toList());
    }

    public static List<PodInstance> convertToInstances(PodList podList, Predicate<? super Pod> predicate) {
        return podList.getItems().stream().filter(predicate).map(PodInstance::new).collect(Collectors.toList());
    }
}
