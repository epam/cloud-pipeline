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
import io.fabric8.kubernetes.api.model.NodeAddress;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
public final class MasterNode {

    public static final String K8S_MASTER_PORT_LABEL = "cloud-pipeline/k8s_master_port";
    public static final String INTERNAL_IP = "InternalIP";

    private UUID uid;
    private String name;
    private String creationTimestamp;
    private String internalIP;
    private String port;
    private Map<String, String> labels;

    private MasterNode(final Node node, final String defaultPort) {

        Optional.ofNullable(node.getMetadata()).ifPresent(
                metadata -> {
                    this.uid = UUID.fromString(metadata.getUid());
                    this.name = metadata.getName();
                    this.labels = metadata.getLabels();
                    final String port = MapUtils.emptyIfNull(this.labels).get(K8S_MASTER_PORT_LABEL);
                    this.port = StringUtils.isNotBlank(port) ? port : defaultPort;
                    this.creationTimestamp = metadata.getCreationTimestamp();
        });

        Optional.ofNullable(node.getStatus()).ifPresent(
            status ->
                this.internalIP = status.getAddresses()
                        .stream()
                        .filter(address -> address.getType().equalsIgnoreCase(INTERNAL_IP))
                        .map(NodeAddress::getAddress)
                        .findFirst()
                        .orElse(null)
        );
    }

    public static MasterNode fromNode(final Node node, final String port) {
        return new MasterNode(node, port);
    }

}
