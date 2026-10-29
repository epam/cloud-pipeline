/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.cluster;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;

import java.util.Arrays;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class NodeCreatorUtils {

    private static final String UUID = "1fb7aff6-03bf-11eb-adc1-0242ac120002";

    private NodeCreatorUtils() {

    }

    public static Node getEmptyNode() {
        return new Node();
    }

    public static Node getNodeWithConditions(final Map<String, String> labels, final NodeCondition... conditions) {
        final ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setUid(UUID);
        objectMeta.setName(TEST_STRING);
        objectMeta.setLabels(labels);
        final NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setConditions(Arrays.asList(conditions));
        return new Node(TEST_STRING, TEST_STRING, objectMeta, new NodeSpec(), nodeStatus);
    }

    public static NodeCondition getNodeCondition(final String type, final String status, final String reason,
                                                 final String lastHeartbeatTime, final String lastTransitionTime) {
        final NodeCondition condition = new NodeCondition();
        condition.setType(type);
        condition.setStatus(status);
        condition.setReason(reason);
        condition.setLastHeartbeatTime(lastHeartbeatTime);
        condition.setLastTransitionTime(lastTransitionTime);
        return condition;
    }
}
