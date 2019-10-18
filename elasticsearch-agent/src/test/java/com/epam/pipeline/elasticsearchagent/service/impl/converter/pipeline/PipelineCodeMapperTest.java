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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.pipeline;

import com.epam.pipeline.entity.pipeline.Pipeline;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineCode;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VALUE;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VALUE_BYTE;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VERSION;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class PipelineCodeMapperTest {

    @Test
    void shouldMapPipelineCode() throws IOException {
        PipelineCodeMapper mapper = new PipelineCodeMapper();

        Pipeline pipeline = new Pipeline();
        pipeline.setId(1L);
        pipeline.setName(TEST_NAME);

        XContentBuilder contentBuilder = mapper.pipelineCodeToDocument(pipeline, TEST_VERSION, TEST_PATH,
                TEST_VALUE_BYTE, PERMISSIONS_CONTAINER);

        verifyPipelineCode(pipeline, TEST_VERSION, TEST_PATH, TEST_VALUE, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
    }
}
