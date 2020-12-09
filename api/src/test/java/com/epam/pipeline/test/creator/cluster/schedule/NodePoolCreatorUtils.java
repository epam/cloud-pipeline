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
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.ConfigurationPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.DockerPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.ParameterPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PipelinePoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerGroupPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerPoolInstanceFilter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NodePoolCreatorUtils {

    public static final String POOL_NAME = "m5.large 100Gb";
    public static final int INSTANCE_COUNT = 5;
    public static final String DOCKER_IMAGE = "library/centos";
    public static final String INSTANCE_TYPE = "m5.large";
    public static final long REGION_ID = 1L;
    public static final int INSTANCE_DISK = 100;

    private NodePoolCreatorUtils() {
        //no op
    }

    public static NodePool getPoolWithoutSchedule() {
        final NodePool pool = new NodePool();
        pool.setName(POOL_NAME);
        pool.setCreated(LocalDateTime.now());
        pool.setCount(INSTANCE_COUNT);
        pool.setDockerImages(Collections.singleton(DOCKER_IMAGE));
        pool.setPriceType(PriceType.ON_DEMAND);
        pool.setInstanceType(INSTANCE_TYPE);
        pool.setRegionId(REGION_ID);
        pool.setInstanceDisk(INSTANCE_DISK);
        return pool;
    }

    public static PoolFilter getAllFilters() {
        return new PoolFilter(PoolFilterOperator.AND, buildAllFilters());
    }

    private static List<PoolInstanceFilter> buildAllFilters() {
        final List<PoolInstanceFilter> filters = new ArrayList<>();

        final RunOwnerPoolInstanceFilter ownerFilter = new RunOwnerPoolInstanceFilter();
        ownerFilter.setOperator(PoolInstanceFilterOperator.EQUAL);
        ownerFilter.setValue("user");
        filters.add(ownerFilter);

        final RunOwnerGroupPoolInstanceFilter groupFilter = new RunOwnerGroupPoolInstanceFilter();
        groupFilter.setOperator(PoolInstanceFilterOperator.NOT_EQUAL);
        groupFilter.setValue("group");
        filters.add(groupFilter);

        final ConfigurationPoolInstanceFilter configurationFilter = new ConfigurationPoolInstanceFilter();
        configurationFilter.setOperator(PoolInstanceFilterOperator.EMPTY);
        filters.add(configurationFilter);

        final PipelinePoolInstanceFilter pipelineFilter = new PipelinePoolInstanceFilter();
        pipelineFilter.setOperator(PoolInstanceFilterOperator.NOT_EMPTY);
        pipelineFilter.setValue(1L);
        filters.add(pipelineFilter);

        final DockerPoolInstanceFilter dockerFilter = new DockerPoolInstanceFilter();
        dockerFilter.setOperator(PoolInstanceFilterOperator.EQUAL);
        dockerFilter.setValue("centos");
        filters.add(dockerFilter);

        final ParameterPoolInstanceFilter parameterFilter = new ParameterPoolInstanceFilter();
        parameterFilter.setOperator(PoolInstanceFilterOperator.EQUAL);
        parameterFilter.setValue(Collections.singletonMap("key", "val"));
        filters.add(parameterFilter);

        return filters;
    }
}
