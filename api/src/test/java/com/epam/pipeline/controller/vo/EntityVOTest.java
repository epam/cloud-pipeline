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

package com.epam.pipeline.controller.vo;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityVOTest {

    private static final Long ENTITY_ID = 1L;

    @Test
    public void shouldDeserializeTheEntitiesOfAMetadataRequest() throws Exception {
        final List<EntityVO> entities = new JsonMapper().readValue(
                "[{\"entityId\": 1, \"entityClass\": \"PIPELINE_USER\"}]", new TypeReference<List<EntityVO>>() {});

        assertThat(entities).containsExactly(new EntityVO(ENTITY_ID, AclClass.PIPELINE_USER));
    }
}
