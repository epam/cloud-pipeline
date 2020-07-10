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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class PipelineConfigurationPostProcessorTest {

    private static final String OLD_REGISTRY = "old-registry:443";
    private static final String NEW_REGISTRY = "new-registry:443";

    private static final String OLD_BUCKET = "s3://old-bucket";
    private static final String NEW_BUCKET = "s3://new-bucket";

    private static final String TOOL = "library/centos:7";
    private static final String TEMPLATES_TEST_ALIASES_JSON = "templates/test_aliases.json";
    private static final String DELIMITER = "/";
    private static final String PARAM_NAME = "test";
    private static final String FILE_TXT = "file.txt";

    private PipelineConfigurationPostProcessor processor;

    @Before
    public void setUp() throws URISyntaxException {
        final JsonMapper jsonMapper = new JsonMapper();
        jsonMapper.init();
        final URL fileUrl = Thread.currentThread().getContextClassLoader()
                .getResource(TEMPLATES_TEST_ALIASES_JSON);
        final Path path = Paths.get(fileUrl.toURI()).toAbsolutePath();
        processor = new PipelineConfigurationPostProcessor(path.toString());
    }

    @Test
    public void shouldChangeOldRegistry() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(OLD_REGISTRY + DELIMITER + TOOL);
        processor.postProcessPipelineConfig(configuration);
        Assert.assertEquals(configuration.getDockerImage(), NEW_REGISTRY + DELIMITER + TOOL);
    }

    @Test
    public void shouldSkipNewRegistry() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setDockerImage(NEW_REGISTRY + DELIMITER + TOOL);
        processor.postProcessPipelineConfig(configuration);
        Assert.assertEquals(configuration.getDockerImage(), NEW_REGISTRY + DELIMITER + TOOL);
    }

    @Test
    public void shouldChangeOldBucket() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        final HashMap<String, PipeConfValueVO> params = new HashMap<>();
        params.put(PARAM_NAME, new PipeConfValueVO(OLD_BUCKET + DELIMITER + FILE_TXT, "input"));
        configuration.setParameters(params);
        processor.postProcessPipelineConfig(configuration);
        Assert.assertEquals(configuration.getParameters().get(PARAM_NAME).getValue(),
                NEW_BUCKET + DELIMITER + FILE_TXT);
    }

    @Test
    public void shouldSkipNewBucket() {
        final PipelineConfiguration configuration = new PipelineConfiguration();
        final HashMap<String, PipeConfValueVO> params = new HashMap<>();
        params.put(PARAM_NAME, new PipeConfValueVO(NEW_BUCKET + DELIMITER + FILE_TXT, "input"));
        configuration.setParameters(params);
        processor.postProcessPipelineConfig(configuration);
        Assert.assertEquals(configuration.getParameters().get(PARAM_NAME).getValue(),
                NEW_BUCKET + DELIMITER + FILE_TXT);
    }

}