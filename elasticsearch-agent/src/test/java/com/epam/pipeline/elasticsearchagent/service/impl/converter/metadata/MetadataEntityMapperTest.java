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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.metadata;

import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyMetadataEntity;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.TestConstants.EXPECTED_METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_KEY;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VALUE;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class MetadataEntityMapperTest {

    @Test
    void shouldMapMetadataEntity() throws IOException {
        MetadataEntityMapper mapper = new MetadataEntityMapper();

        MetadataClass metadataClass = new MetadataClass(1L, "Sample");
        MetadataEntity metadataEntity = new MetadataEntity();
        metadataEntity.setClassEntity(metadataClass);
        metadataEntity.setId(1L);
        metadataEntity.setExternalId("external");
        metadataEntity.setParent(new Folder(1L));
        metadataEntity.setName(TEST_NAME);
        metadataEntity.setData(Collections.singletonMap(TEST_KEY, new PipeConfValue("string", TEST_VALUE)));

        EntityContainer<MetadataEntity> container = EntityContainer.<MetadataEntity>builder()
                .entity(metadataEntity)
                .permissions(PERMISSIONS_CONTAINER)
                .build();
        XContentBuilder contentBuilder = mapper.map(container);

        verifyMetadataEntity(metadataEntity, EXPECTED_METADATA, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
    }
}
