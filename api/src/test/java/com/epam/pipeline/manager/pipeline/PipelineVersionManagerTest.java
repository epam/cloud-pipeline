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

package com.epam.pipeline.manager.pipeline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

public class PipelineVersionManagerTest extends AbstractManagerTest {

    private static final String TEST_HTTP_REPOSITORY = "http://ECSE001007DB.epam.com:8090/root/test-pipeline.git";
    private static final String TEST_REVISION_1 = "config";
    private static final int EXPECTED_DEFAULT_PARAMS = 4;
    private static final String TEST_CONFIG = "config.json";
    private static final String WITH_IMAGE_CONFIG = "with_image.json";
    private static final String WITHOUT_IMAGE_CONFIG = "without_image.json";
    private static final String TEST_REPOSITORY = "testRepository";
    private static final String IMAGE_FROM_PROPERTIES = "imageFromProperties";
    private static final String IMAGE_FROM_CONFIG = "imageFromConfig";
    private static final String DOCKER_IMAGE = "dockerImage";

    @Mock
    private PipelineManager pipelineManagerMock;

    @Mock
    private GitManager gitManager;

    @Mock
    private ToolManager toolManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private PreferenceManager preferenceManager;

    @InjectMocks
    private PipelineVersionManager pipelineVersionManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this.getClass());
        mockToolManager();
        Mockito.when(messageHelper.getMessage(Mockito.anyString(), Mockito.anyObject())).thenReturn("");

        Whitebox.setInternalState(pipelineVersionManager, "preferenceManager", preferenceManager);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE)).thenReturn(DOCKER_IMAGE);
    }

    private void mockToolManager() {
        Mockito.when(toolManager.loadTool(Mockito.anyString(), Mockito.anyString())).then(invocation -> {
            Tool tool = new Tool();
            tool.setRegistry(TEST_REPOSITORY);
            tool.setImage((String) invocation.getArguments()[0]);
            return tool;
        });
    }

    @Test
    @Ignore
    public void getPipelineConfig() throws GitClientException, IOException {
        Pipeline mockPipeline = new Pipeline();
        mockPipeline.setRepository(TEST_HTTP_REPOSITORY);
        mockPipeline.setId(1L);
        Mockito.when(pipelineManagerMock.load(mockPipeline.getId())).thenReturn(mockPipeline);
        Mockito.when(gitManager.getConfigFileContent(mockPipeline, TEST_REVISION_1))
                .thenReturn(getFileContent(TEST_CONFIG));
        PipelineConfiguration configuration =
                pipelineVersionManager.loadParametersFromScript(1L, TEST_REVISION_1);
        Assert.assertNotNull(configuration);
        Assert.assertEquals(EXPECTED_DEFAULT_PARAMS, configuration.getParameters().size());
    }

    @Test
    public void testDockerImageSetFromProperties() throws GitClientException, IOException {
        Mockito.when(preferenceManager
                .getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE)).thenReturn(IMAGE_FROM_PROPERTIES);

        Tool mockTool = getMockTool(TEST_REPOSITORY, IMAGE_FROM_PROPERTIES);
        Mockito.when(gitManager.getConfigFileContent(Mockito.any(Pipeline.class), Mockito.anyString()))
            .thenReturn(getFileContent(WITHOUT_IMAGE_CONFIG));
        Mockito.when(toolManager.loadByNameOrId(IMAGE_FROM_PROPERTIES)).thenReturn(mockTool);
        PipelineConfiguration configuration = pipelineVersionManager.loadParametersFromScript(1L, "");
        Assert.assertEquals(TEST_REPOSITORY  + "/" + IMAGE_FROM_PROPERTIES, configuration.getDockerImage());
    }

    @Test
    public void testDockerImageSetFromConfig() throws GitClientException, IOException {
        Tool mockTool = getMockTool(TEST_REPOSITORY, IMAGE_FROM_CONFIG);
        Mockito.when(gitManager.getConfigFileContent(Mockito.any(Pipeline.class), Mockito.anyString()))
            .thenReturn(getFileContent(WITH_IMAGE_CONFIG));
        Mockito.when(toolManager.loadByNameOrId(IMAGE_FROM_CONFIG)).thenReturn(mockTool);
        PipelineConfiguration configuration = pipelineVersionManager.loadParametersFromScript(1L, "");
        Assert.assertEquals(TEST_REPOSITORY  + "/" + IMAGE_FROM_CONFIG, configuration.getDockerImage());
    }

    @Test
    public void testImageWithValidRepository() throws GitClientException, IOException {
        Mockito.when(preferenceManager.getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE))
                .thenReturn(TEST_REPOSITORY + "/" + IMAGE_FROM_PROPERTIES);
        Tool mockTool = getMockTool(TEST_REPOSITORY, IMAGE_FROM_PROPERTIES);
        Mockito.when(gitManager.getConfigFileContent(Mockito.any(Pipeline.class), Mockito.anyString()))
            .thenReturn(getFileContent(WITHOUT_IMAGE_CONFIG));
        Mockito.when(toolManager.loadByNameOrId(TEST_REPOSITORY  + "/" + IMAGE_FROM_PROPERTIES)).thenReturn(mockTool);
        PipelineConfiguration configuration = pipelineVersionManager.loadParametersFromScript(1L, "");
        Assert.assertEquals(TEST_REPOSITORY  + "/" + IMAGE_FROM_PROPERTIES, configuration.getDockerImage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testImageWithInvalidRepository() throws GitClientException, IOException {
        Mockito.when(preferenceManager.getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE))
                .thenReturn("wrongRepository" + "/" + IMAGE_FROM_PROPERTIES);
        Mockito.when(gitManager.getConfigFileContent(Mockito.any(Pipeline.class), Mockito.anyString()))
            .thenReturn(getFileContent(WITHOUT_IMAGE_CONFIG));
        Mockito.when(toolManager.loadByNameOrId("wrongRepository" + "/" + IMAGE_FROM_PROPERTIES))
                .thenThrow(new IllegalArgumentException());
        pipelineVersionManager.loadParametersFromScript(1L, "");
    }

    private String getFileContent(String fileName) throws IOException {
        File configFile = getTestFile(fileName);
        StringBuilder configContent = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                configContent.append(line);
            }
        }
        return configContent.toString();
    }

    private Tool getMockTool(String repo, String image) {
        Tool tool = new Tool();
        tool.setRegistry(repo);
        tool.setImage(image);
        return tool;
    }
}
