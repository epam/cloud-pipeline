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
package com.epam.pipeline.elasticsearchagent.service.impl.converter.configuration;

import com.epam.pipeline.elasticsearchagent.model.ConfigurationEntryDoc;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyFirecloudConfigurationEntry;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyMetadata;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPermissions;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyPipelineUser;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyRunConfiguration;
import static com.epam.pipeline.elasticsearchagent.MapperVerificationUtils.verifyRunConfigurationEntry;
import static com.epam.pipeline.elasticsearchagent.TestConstants.EXPECTED_METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.METADATA;
import static com.epam.pipeline.elasticsearchagent.TestConstants.PERMISSIONS_CONTAINER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_PATH;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_SNAPSHOT;
import static com.epam.pipeline.elasticsearchagent.TestConstants.TEST_VERSION;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;

@SuppressWarnings({"PMD.TooManyStaticImports"})
class ConfigurationMapperTest {

    @Test
    void shouldMapRunConfiguration() throws IOException {
        ConfigurationEntryMapper mapper = new ConfigurationEntryMapper();

        Pipeline pipeline = buildPipeline();
        RunConfiguration runConfiguration = buildRunConfiguration();

        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setDockerImage(TEST_PATH);

        RunConfigurationEntry entry = new RunConfigurationEntry();
        entry.setPipelineVersion(TEST_VERSION);
        entry.setName(TEST_NAME);
        entry.setConfiguration(pipelineConfiguration);

        ConfigurationEntryDoc configuration = buildDoc(pipeline, runConfiguration, entry);

        XContentBuilder contentBuilder = mapper.map(buildContainer(configuration));

        verifyRunConfiguration(runConfiguration, TEST_NAME + " ", contentBuilder);
        verifyRunConfigurationEntry(entry, pipeline, contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
        verifyMetadata(EXPECTED_METADATA, contentBuilder);
    }

    @Test
    void shouldMapFireCloudConfiguration() throws IOException {
        ConfigurationEntryMapper mapper = new ConfigurationEntryMapper();

        RunConfiguration runConfiguration = buildRunConfiguration();

        FirecloudRunConfigurationEntry entry = new FirecloudRunConfigurationEntry();
        entry.setName(TEST_NAME);
        entry.setMethodName(TEST_NAME);
        entry.setMethodSnapshot(TEST_SNAPSHOT);
        entry.setMethodConfigurationName(TEST_NAME);
        entry.setMethodConfigurationSnapshot(TEST_SNAPSHOT);

        ConfigurationEntryDoc configuration = buildDoc(null, runConfiguration, entry);

        XContentBuilder contentBuilder = mapper.map(buildContainer(configuration));

        verifyFirecloudConfigurationEntry(entry, contentBuilder);
        verifyRunConfiguration(runConfiguration, TEST_NAME + " ", contentBuilder);
        verifyPipelineUser(USER, contentBuilder);
        verifyPermissions(PERMISSIONS_CONTAINER, contentBuilder);
        verifyMetadata(EXPECTED_METADATA, contentBuilder);
    }

    private static RunConfiguration buildRunConfiguration() {
        RunConfiguration runConfiguration = new RunConfiguration();
        runConfiguration.setName(TEST_NAME);
        return runConfiguration;
    }

    private static EntityContainer<ConfigurationEntryDoc> buildContainer(final ConfigurationEntryDoc doc) {
        return EntityContainer.<ConfigurationEntryDoc>builder()
                    .entity(doc)
                    .owner(USER)
                    .permissions(PERMISSIONS_CONTAINER)
                    .metadata(METADATA)
                    .build();
    }

    private static ConfigurationEntryDoc buildDoc(final Pipeline pipeline,
                                                  final RunConfiguration runConfiguration,
                                                  final AbstractRunConfigurationEntry configurationEntry) {
        return ConfigurationEntryDoc
                    .builder()
                    .configuration(runConfiguration)
                    .id("id")
                    .entry(configurationEntry)
                    .pipeline(pipeline)
                    .build();
    }

    private static Pipeline buildPipeline() {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(TEST_NAME);
        return pipeline;
    }
}
