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

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.NodeSystemInfo;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class MasterNode {

    public static final int DEFAULT_API_PORT = 9999;
    public static final String API_PORT_LABEL = "api_port";
    private UUID uid;
    private String name;
    private String creationTimestamp;
    private List<NodeInstanceAddress> addresses;
    private String clusterName;
    private Map<String, String> labels;
    private NodeInstanceSystemInfo systemInfo;
    private String port;

    public MasterNode(Node node) {
        ObjectMeta metadata = node.getMetadata();
        if (metadata != null) {
            this.setUid(UUID.fromString(metadata.getUid()));
            this.setName(metadata.getName());
            Map<String, String> labels = metadata.getLabels();
            this.setLabels(labels);
            if (labels != null) {
                String port = labels.get(API_PORT_LABEL);
                if (StringUtils.isNotBlank(port)) {
                    this.setPort(port);
                } else {
                    this.setPort(String.valueOf(DEFAULT_API_PORT));
                }
            }
            this.setCreationTimestamp(metadata.getCreationTimestamp());
            this.setClusterName(metadata.getClusterName());
        }
        NodeStatus status = node.getStatus();
        if (status != null) {
            this.setAddresses(NodeInstanceAddress.convertToInstances(status.getAddresses()));
            NodeSystemInfo info = status.getNodeInfo();
            if (info != null) {
                this.setSystemInfo(new NodeInstanceSystemInfo(info));
            }
        }
    }

}
