/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.vo.cluster.pool;

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.filter.PoolFilter;
import lombok.Data;

import java.util.Set;


@Data
public class NodePoolVO {

    private Long id;
    private String name;
    private Long regionId;
    private String instanceType;
    private int instanceDisk;
    private PriceType priceType;
    private Set<String> dockerImages;
    private String instanceImage;
    private int count;
    private Long scheduleId;
    private PoolFilter filter;
    private boolean autoscaled;
    private Integer minSize;
    private Integer maxSize;
    private Double scaleUpThreshold;
    private Double scaleDownThreshold;
    private Integer scaleStep;
}
