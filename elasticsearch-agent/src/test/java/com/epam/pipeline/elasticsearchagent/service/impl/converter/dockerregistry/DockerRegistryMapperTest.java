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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.dockerregistry;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyDockerRegistry;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyMetadata;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.TestConstants.EXPECTED_METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_DESCRIPTION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER_NAME;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class DockerRegistryMapperTest {

    @Test
    void shouldMapDockerRegistry() throws IOException {
        DockerRegistryMapper mapper = new DockerRegistryMapper();

        DockerRegistry registry = new DockerRegistry();
        registry.setId(1L);
        registry.setDescription(TEST_DESCRIPTION);
        registry.setName(TEST_NAME);
        registry.setPath(TEST_PATH);
        registry.setUserName(USER_NAME);

        EntityContainer<DockerRegistry> container = EntityContainer.<DockerRegistry>builder()
                .entity(registry)
                .owner(USER)
                .metadata(METADATA)
                .permissions(PERMISSIONS_CONTAINER)
                .build();
        XContentBuilder contentBuilder = mapper.map(container);

        verifyDockerRegistry(registry, contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
        verifyMetadata(EXPECTED_METADATA, contentBuilder);
    }
}
