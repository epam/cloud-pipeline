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

package com.epam.pipeline.entity.cluster.schedule;

import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class PersistentNode {

    private Long id;
    private String name;
    private LocalDateTime created;
    private long regionId;
    private String instanceType;
    private int instanceDisk;
    private PriceType priceType;
    private String dockerImage;
    private int count;
    private NodeSchedule schedule;

    public boolean isActive(final LocalDateTime timestamp) {
        return Optional.ofNullable(schedule)
                .map(s -> s.isActive(timestamp))
                .orElse(true);
    }
}
