/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.cluster;

import com.epam.pipeline.entity.cluster.EventEntity;
import io.fabric8.kubernetes.api.model.Event;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static com.epam.pipeline.test.creator.cluster.KubernetesCreatorUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesMapperTest {

    private final KubernetesMapper mapper = Mappers.getMapper(KubernetesMapper.class);

    @Test
    public void shouldMapEvents() {
        final Event kubeEvent = event();
        final EventEntity eventEntity = mapper.mapEvent(kubeEvent);

        assertThat(eventEntity).isEqualTo(eventEntity());
    }
}
