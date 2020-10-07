/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.cluster;

import com.epam.pipeline.controller.vo.FilterNodesVO;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.FilterPodsRequest;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.MasterNode;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.NodeInstance;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeSpec;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public final class NodeCreatorUtils {

    private static final List<String> TEST_LIST = Collections.singletonList("test");
    private static final List<InstanceType> INSTANCE_TYPES = Collections.singletonList(getDefaultInstanceType());

    private NodeCreatorUtils() {

    }

    public static NodeInstance getDefaultNodeInstance() {
        final NodeInstance nodeInstance = new NodeInstance();
        return nodeInstance;
    }

    public static InstanceType getDefaultInstanceType() {
        final InstanceType instanceType = InstanceType.builder().name("TEST").build();
        return instanceType;
    }

    public static Node getDefaultNode() {
        final ObjectMeta objectMeta = new ObjectMeta();
        final Node node = new Node(
                "v1", "test", objectMeta, new NodeSpec(), new NodeStatus());
        return node;
    }

    public static MasterNode getDefaultMasterNode() {
        final MasterNode masterNode = MasterNode.fromNode(getDefaultNode(), "7367");
        return masterNode;
    }

    public static FilterNodesVO getDefaultFilterNodesVO() {
        final FilterNodesVO filterNodesVO = new FilterNodesVO();
        filterNodesVO.setAddress("testAddress");
        return filterNodesVO;
    }

    public static FilterPodsRequest getDefaultFilterPodsRequest() {
        FilterPodsRequest filterPodsRequest = new FilterPodsRequest();
        filterPodsRequest.setPodStatuses(TEST_LIST);
        return filterPodsRequest;
    }

    public AllowedInstanceAndPriceTypes getDefaultAllowedInstanceAndPriceTypes() {
        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes = new AllowedInstanceAndPriceTypes(
                INSTANCE_TYPES, INSTANCE_TYPES, TEST_LIST, TEST_LIST
        );
        return allowedInstanceAndPriceTypes;
    }

    public static NodeDisk getDefaultNodeDisk() {
        final NodeDisk nodeDisk = new NodeDisk(1L, "NAME",
                LocalDateTime.parse("2019-04-01T09:08:07", DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return nodeDisk;
    }
}
