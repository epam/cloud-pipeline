/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cluster.nat;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@ToString
public class NatRoute {

    private final Long routeId;
    private final String externalName;
    private final String externalIp;
    private final Integer externalPort;
    private final String protocol;
    private final String internalName;
    private final String internalIp;
    private final Integer internalPort;
    private final NatRouteStatus status;
    private final LocalDateTime lastUpdateTime;
    private final LocalDateTime lastErrorTime;
    private final String lastErrorMessage;
    private final String description;
}
