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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerInstance {

    private String name;
    private Map<String, String> requests;
    private Map<String, String> limits;
    private ContainerInstanceStatus status;

    public ContainerInstance() {
        this.requests = new HashMap<>();
        this.limits = new HashMap<>();
    }

    public ContainerInstance(Container container) {
        this();
        this.setName(container.getName());
        ResourceRequirements requirements = container.getResources();
        if (requirements != null) {
            this.setRequests(QuantitiesConverter.convertQuantityMap(requirements.getRequests()));
            this.setLimits(QuantitiesConverter.convertQuantityMap(requirements.getLimits()));
        }
    }

    public ContainerInstance(Container container, List<ContainerStatus> statuses) {
        this(container);
        if (statuses != null) {
            Predicate<? super ContainerStatus> filter = s -> s.getName() != null && s.getName().equals(this.name);
            Optional<ContainerStatus> optStatus = statuses.stream().filter(filter).findFirst();
            if (optStatus.isPresent()) {
                ContainerStatus status = optStatus.get();
                this.status = new ContainerInstanceStatus(status.getState());
            }
        }
    }

    public static List<ContainerInstance> convertToInstances(List<Container> containers) {
        if (containers != null) {
            return containers.stream().map(ContainerInstance::new).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public static List<ContainerInstance> convertToInstances(List<Container> containers,
                                                             List<ContainerStatus> statuses) {
        if (containers != null) {
            return containers.stream().map(c -> new ContainerInstance(c, statuses)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
