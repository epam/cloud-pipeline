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

package com.epam.pipeline.test.creator.cluster;

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

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;

public final class NodeCreatorUtils {

    private static final List<InstanceType> INSTANCE_TYPES = Collections.singletonList(getDefaultInstanceType());
    private static final String UUID = "1fb7aff6-03bf-11eb-adc1-0242ac120002";
    private static final LocalDateTime LDT =
            LocalDateTime.parse("2019-04-01T09:08:07", DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private NodeCreatorUtils() {

    }

    public static NodeInstance getDefaultNodeInstance() {
        return new NodeInstance();
    }

    public static InstanceType getDefaultInstanceType() {
        return InstanceType.builder().name(TEST_STRING).build();
    }

    public static Node getDefaultNode() {
        final ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setUid(UUID);
        return new Node(
                TEST_STRING, TEST_STRING, objectMeta, new NodeSpec(), new NodeStatus());
    }

    public static MasterNode getDefaultMasterNode() {
        return MasterNode.fromNode(getDefaultNode(), TEST_STRING);
    }

    public static FilterNodesVO getDefaultFilterNodesVO() {
        final FilterNodesVO filterNodesVO = new FilterNodesVO();
        filterNodesVO.setAddress(TEST_STRING);
        return filterNodesVO;
    }

    public static FilterPodsRequest getDefaultFilterPodsRequest() {
        final FilterPodsRequest filterPodsRequest = new FilterPodsRequest();
        filterPodsRequest.setPodStatuses(TEST_STRING_LIST);
        return filterPodsRequest;
    }

    public static AllowedInstanceAndPriceTypes getDefaultAllowedInstanceAndPriceTypes() {
        return new AllowedInstanceAndPriceTypes(
                INSTANCE_TYPES, INSTANCE_TYPES, TEST_STRING_LIST, TEST_STRING_LIST
        );
    }

    public static NodeDisk getDefaultNodeDisk() {
        return new NodeDisk(ID, TEST_STRING, LDT);
    }
}
