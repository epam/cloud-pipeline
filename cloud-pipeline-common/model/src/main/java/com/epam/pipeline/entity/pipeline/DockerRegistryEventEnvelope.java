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

package com.epam.pipeline.entity.pipeline;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents envelope for docker registry notification events. Envelope is just a list of {@link DockerRegistryEvent}
 * For more information see: https://docs.docker.com/registry/notifications/#envelope
 * */
@Getter
@Setter
public class DockerRegistryEventEnvelope {
    private List<DockerRegistryEvent> events;
}
