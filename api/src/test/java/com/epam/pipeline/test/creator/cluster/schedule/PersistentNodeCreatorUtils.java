/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.test.creator.cluster.schedule;

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;

import java.time.LocalDateTime;

public final class PersistentNodeCreatorUtils {

    public static final String NODE_NAME = "m5.large 100Gb";
    public static final int INSTANCE_COUNT = 5;
    public static final String DOCKER_IMAGE = "library/centos";
    public static final String INSTANCE_TYPE = "m5.large";
    public static final long REGION_ID = 1L;
    public static final int INSTANCE_DISK = 100;

    private PersistentNodeCreatorUtils() {
        //no op
    }

    public static PersistentNode getNodeWithoutSchedule() {
        final PersistentNode node = new PersistentNode();
        node.setName(NODE_NAME);
        node.setCreated(LocalDateTime.now());
        node.setCount(INSTANCE_COUNT);
        node.setDockerImage(DOCKER_IMAGE);
        node.setPriceType(PriceType.ON_DEMAND);
        node.setInstanceType(INSTANCE_TYPE);
        node.setRegionId(REGION_ID);
        node.setInstanceDisk(INSTANCE_DISK);
        return node;
    }
}
