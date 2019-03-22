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

import io.fabric8.kubernetes.api.model.NodeAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeInstanceAddress {

    private String address;
    private String type;

    public NodeInstanceAddress() {
    }

    public NodeInstanceAddress(NodeAddress address) {
        this();
        this.setAddress(address.getAddress());
        this.setType(address.getType());
    }

    public static List<NodeInstanceAddress> convertToInstances(List<NodeAddress> addresses) {
        if (addresses != null) {
            return addresses.stream().map(NodeInstanceAddress::new).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
