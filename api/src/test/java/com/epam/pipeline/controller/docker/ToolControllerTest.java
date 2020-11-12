/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.docker;

import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.epam.pipeline.manager.pipeline.ToolApiService;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = ToolController.class)
public class ToolControllerTest extends AbstractControllerTest {

    private static final String TOOL_URL = SERVLET_PATH + "/tool";
    private static final String REGISTER_TOOL_URL = TOOL_URL + "/register";
    private static final String UPDATE_TOOL_URL = TOOL_URL + "/update";
    private static final String UPDATE_WHITE_LIST_URL = TOOL_URL + "/updateWhiteList";
    private static final String LOAD_TOOL_URL = TOOL_URL + "/load";
    private static final String DELETE_TOOL_URL = TOOL_URL + "/delete";
    private static final String LOAD_IMAGE_TAGS_URL = TOOL_URL + "/%d/tags";
    private static final String LOAD_IMAGE_DESCRIPTIONS_URL = TOOL_URL + "/%d/description";
    private static final String LOAD_IMAGE_HISTORY_URL = TOOL_URL + "/%d/history";
    private static final String DEFAULT_CMD_URL = TOOL_URL + "/%d/defaultCmd";
    private static final String SCAN_TOOL_URL = TOOL_URL + "/scan";
    private static final String SCAN_POLICY_TOOL_URL = SCAN_TOOL_URL + "/policy";
    private static final String ENABLE_SCAN_TOOL_URL = SCAN_TOOL_URL + "/enabled";
    private static final String ICON_TOOL_URL = TOOL_URL + "/%d/icon";
    private static final String ATTRIBUTES_TOOL_URL = TOOL_URL + "/%d/attributes";
    private static final String SETTINGS_TOOL_URL = TOOL_URL + "/%d/settings";
    private static final String SYMLINK_TOOL_URL = TOOL_URL + "/symlink";

    private static final String ID_AS_STRING = String.valueOf(ID);
    private static final String TRUE_AS_STRING = String.valueOf(true);
    private static final String TOOL_ID = "toolId";
    private static final String VERSION = "version";
    private static final String REGISTRY = "registry";
    private static final String IMAGE = "image";
    private static final String HARD = "hard";
    private static final String TAG = "tag";
    private static final String TOOL = "tool";
    private static final String RESCAN = "rescan";
    private static final String FILE_NAME = "file.jpg";
    private static final String PATH = "path";
    private static final String MULTIPART_CONTENT_TYPE =
            "multipart/form-data; boundary=--------------------------boundary";
    private static final String MULTIPART_CONTENT =
            "----------------------------boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"file.jpg\"\r\n" +
            "Content-Type:  image/jpg\r\n" +
            "\r\n" +
            "file.jpg" +
            "\r\n" +
            "----------------------------boundary";

    private final Tool tool = DockerCreatorUtils.getTool();
    private final ToolVersion toolVersion = DockerCreatorUtils.getToolVersion();

    @Autowired
    private ToolApiService mockToolApiService;

    @Autowired
    private ToolManager mockToolManager;

    @Test
    @WithMockUser
    public void shouldCreateTool() throws Exception {
        final String content = getObjectMapper().writeValueAsString(tool);
        doReturn(tool).when(mockToolApiService).create(tool);

        final MvcResult mvcResult = performRequest(post(REGISTER_TOOL_URL).content(content));

        verify(mockToolApiService).create(tool);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCreateTool() throws Exception {
        performUnauthorizedRequest(post(REGISTER_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateTool() throws Exception {
        final String content = getObjectMapper().writeValueAsString(tool);
        doReturn(tool).when(mockToolApiService).updateTool(tool);

        final MvcResult mvcResult = performRequest(post(UPDATE_TOOL_URL).content(content));

        verify(mockToolApiService).updateTool(tool);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateTool() throws Exception {
        performUnauthorizedRequest(post(UPDATE_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateWhiteListWithToolVersion() throws Exception {
        final ToolVersionScanResult toolVersionScanResult = DockerCreatorUtils.getToolVersionScanResult();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(TOOL_ID, ID_AS_STRING);
        params.add(VERSION, TEST_STRING);
        doReturn(toolVersionScanResult).when(mockToolApiService)
                .updateWhiteListWithToolVersion(ID, TEST_STRING, true);

        final MvcResult mvcResult = performRequest(post(UPDATE_WHITE_LIST_URL).params(params));

        verify(mockToolApiService).updateWhiteListWithToolVersion(ID, TEST_STRING, true);
        assertResponse(mvcResult, toolVersionScanResult, DockerCreatorUtils.TOOL_VERSION_SCAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateWhiteListWithToolVersion() throws Exception {
        performUnauthorizedRequest(post(UPDATE_WHITE_LIST_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadTool() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(IMAGE, TEST_STRING);
        doReturn(tool).when(mockToolApiService).loadTool(TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(LOAD_TOOL_URL).params(params));

        verify(mockToolApiService).loadTool(TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadTool() throws Exception {
        performUnauthorizedRequest(get(LOAD_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteTool() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(IMAGE, TEST_STRING);
        params.add(HARD, TRUE_AS_STRING);
        doReturn(tool).when(mockToolApiService).delete(TEST_STRING, TEST_STRING, true);

        final MvcResult mvcResult = performRequest(delete(DELETE_TOOL_URL).params(params));

        verify(mockToolApiService).delete(TEST_STRING, TEST_STRING, true);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteToolVersion() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(IMAGE, TEST_STRING);
        params.add(VERSION, TEST_STRING);
        params.add(HARD, TRUE_AS_STRING);
        doReturn(tool).when(mockToolApiService).deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(DELETE_TOOL_URL).params(params));

        verify(mockToolApiService).deleteToolVersion(TEST_STRING, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteToolVersion() throws Exception {
        performUnauthorizedRequest(delete(DELETE_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadImageTags() throws Exception {
        doReturn(TEST_STRING_LIST).when(mockToolApiService).loadImageTags(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_IMAGE_TAGS_URL, ID)));

        verify(mockToolApiService).loadImageTags(ID);
        assertResponse(mvcResult, TEST_STRING_LIST, DockerCreatorUtils.LIST_STRING_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadImageTags() throws Exception {
        performUnauthorizedRequest(get(String.format(LOAD_IMAGE_TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadImageDescription() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(TAG, TEST_STRING);
        final ImageDescription imageDescription = DockerCreatorUtils.getImageDescription();
        doReturn(imageDescription).when(mockToolApiService).getImageDescription(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_IMAGE_DESCRIPTIONS_URL, ID)).params(params));

        verify(mockToolApiService).getImageDescription(ID, TEST_STRING);
        assertResponse(mvcResult, imageDescription, DockerCreatorUtils.IMAGE_DESCRIPTION_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadImageDescription() throws Exception {
        performUnauthorizedRequest(get(String.format(LOAD_IMAGE_DESCRIPTIONS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadImageHistory() throws Exception {
        final ImageHistoryLayer imageHistoryLayer = DockerCreatorUtils.getImageHistoryLayer();
        final List<ImageHistoryLayer> imageHistoryLayers = Collections.singletonList(imageHistoryLayer);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(VERSION, TEST_STRING);
        doReturn(imageHistoryLayers).when(mockToolApiService).getImageHistory(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_IMAGE_HISTORY_URL, ID)).params(params));

        verify(mockToolApiService).getImageHistory(ID, TEST_STRING);
        assertResponse(mvcResult, imageHistoryLayers, DockerCreatorUtils.IMAGE_HISTORY_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadImageHistory() throws Exception {
        performUnauthorizedRequest(get(String.format(LOAD_IMAGE_HISTORY_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadDefaultImageCmd() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(VERSION, TEST_STRING);
        doReturn(TEST_STRING).when(mockToolApiService).getImageDefaultCommand(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(DEFAULT_CMD_URL, ID)).params(params));

        verify(mockToolApiService).getImageDefaultCommand(ID, TEST_STRING);
        assertResponse(mvcResult, TEST_STRING, CommonCreatorConstants.STRING_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadDefaultImageCmd() throws Exception {
        performUnauthorizedRequest(get(String.format(DEFAULT_CMD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldScanTool() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(TOOL, TEST_STRING);
        params.add(VERSION, TEST_STRING);
        params.add(RESCAN, TRUE_AS_STRING);
        doNothing().when(mockToolApiService).forceScanTool(TEST_STRING, TEST_STRING, TEST_STRING, true);

        final MvcResult mvcResult = performRequest(post(SCAN_TOOL_URL).params(params));

        verify(mockToolApiService).forceScanTool(TEST_STRING, TEST_STRING, TEST_STRING, true);
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailScanTool() throws Exception {
        performUnauthorizedRequest(post(SCAN_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldClearToolScan() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(TOOL, TEST_STRING);
        params.add(VERSION, TEST_STRING);
        doNothing().when(mockToolApiService).clearToolScan(TEST_STRING, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(delete(SCAN_TOOL_URL).params(params));

        verify(mockToolApiService).clearToolScan(TEST_STRING, TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailClearToolScan() throws Exception {
        performUnauthorizedRequest(delete(SCAN_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadVulnerabilities() throws Exception {
        final ToolScanResultView toolScanResultView = DockerCreatorUtils.getToolScanResultView();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        params.add(TOOL, TEST_STRING);
        doReturn(toolScanResultView).when(mockToolApiService).loadToolScanResult(TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(SCAN_TOOL_URL).params(params));

        verify(mockToolApiService).loadToolScanResult(TEST_STRING, TEST_STRING);
        assertResponse(mvcResult, toolScanResultView, DockerCreatorUtils.SCAN_RESULT_VIEW_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadVulnerabilities() throws Exception {
        performUnauthorizedRequest(get(SCAN_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadSecurityPolicy() throws Exception {
        final ToolScanPolicy toolScanPolicy = DockerCreatorUtils.getToolScanPolicy();
        doReturn(toolScanPolicy).when(mockToolApiService).loadSecurityPolicy();

        final MvcResult mvcResult = performRequest(get(SCAN_POLICY_TOOL_URL));

        verify(mockToolApiService).loadSecurityPolicy();
        assertResponse(mvcResult, toolScanPolicy, DockerCreatorUtils.TOOL_SCAN_POLICY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadSecurityPolicy() throws Exception {
        performUnauthorizedRequest(get(SCAN_POLICY_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldCheckIfToolScanningEnabled() throws Exception {
        doReturn(true).when(mockToolManager).isToolScanningEnabled();

        final MvcResult mvcResult = performRequest(get(ENABLE_SCAN_TOOL_URL));

        verify(mockToolManager).isToolScanningEnabled();
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCheckToolScanning() throws Exception {
        performUnauthorizedRequest(get(ENABLE_SCAN_TOOL_URL));
    }

    @Test
    @WithMockUser
    public void shouldUploadToolIcon() throws Exception {
        doReturn(ID).when(mockToolApiService).updateToolIcon(ID, FILE_NAME, FILE_NAME.getBytes());

        final MvcResult mvcResult = performRequest(
                post(String.format(ICON_TOOL_URL, ID)).content(MULTIPART_CONTENT).param(PATH, FILE_NAME),
                MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);

        verify(mockToolApiService).updateToolIcon(ID, FILE_NAME, FILE_NAME.getBytes());
        assertResponse(mvcResult, ID, CommonCreatorConstants.LONG_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUploadToolIcon() throws Exception {
        performUnauthorizedRequest(post(String.format(ICON_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDownloadToolIcon() throws Exception {
        final InputStream inputStream = new ByteArrayInputStream(FILE_NAME.getBytes());
        Pair<String, InputStream> pair = new ImmutablePair<>(FILE_NAME, inputStream);
        doReturn(pair).when(mockToolApiService).loadToolIcon(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ICON_TOOL_URL, ID)),
                MediaType.IMAGE_PNG_VALUE);

        verify(mockToolApiService).loadToolIcon(ID);
        final String actualResult = mvcResult.getResponse().getContentAsString();
        Assert.assertEquals(FILE_NAME, actualResult);
        assertResponseHeader(mvcResult, FILE_NAME);
    }

    @Test
    public void shouldFailDownloadToolIcon() throws Exception {
        performUnauthorizedRequest(get(String.format(ICON_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteToolIcon() throws Exception {
        doNothing().when(mockToolApiService).deleteToolIcon(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(ICON_TOOL_URL, ID)));

        verify(mockToolApiService).deleteToolIcon(ID);
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteToolIcon() throws Exception {
        performUnauthorizedRequest(delete(String.format(ICON_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadToolAttributes() throws Exception {
        final ToolDescription toolDescription = DockerCreatorUtils.getToolDescription();
        doReturn(toolDescription).when(mockToolApiService).loadToolAttributes(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ATTRIBUTES_TOOL_URL, ID)));

        verify(mockToolApiService).loadToolAttributes(ID);
        assertResponse(mvcResult, toolDescription, DockerCreatorUtils.TOOL_DESCRIPTION_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadToolAttributes() throws Exception {
        performUnauthorizedRequest(get(String.format(ATTRIBUTES_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateToolVersionSettings() throws Exception {
        final List<ConfigurationEntry> settings = Collections.singletonList(new ConfigurationEntry());
        final String content = getObjectMapper().writeValueAsString(settings);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(VERSION, TEST_STRING);
        doReturn(toolVersion).when(mockToolApiService).createToolVersionSettings(ID, TEST_STRING, settings);

        final MvcResult mvcResult = performRequest(post(String.format(SETTINGS_TOOL_URL, ID))
                .content(content).params(params));

        verify(mockToolApiService).createToolVersionSettings(ID, TEST_STRING, settings);
        assertResponse(mvcResult, toolVersion, DockerCreatorUtils.TOOL_VERSION_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCreateToolVersionSettings() throws Exception {
        performUnauthorizedRequest(post(String.format(SETTINGS_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadToolVersionSettings() throws Exception {
        final List<ToolVersion> toolVersions = Collections.singletonList(toolVersion);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(VERSION, TEST_STRING);
        doReturn(toolVersions).when(mockToolApiService).loadToolVersionSettings(ID, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(SETTINGS_TOOL_URL, ID))
                .params(params));

        verify(mockToolApiService).loadToolVersionSettings(ID, TEST_STRING);
        assertResponse(mvcResult, toolVersions, DockerCreatorUtils.TOOL_VERSION_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadToolVersionSettings() throws Exception {
        performUnauthorizedRequest(get(String.format(SETTINGS_TOOL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldSymlinkTool() throws Exception {
        final ToolSymlinkRequest request = DockerCreatorUtils.getToolSymlinkRequest();
        final String content = getObjectMapper().writeValueAsString(request);
        doReturn(tool).when(mockToolApiService).symlink(request);

        final MvcResult mvcResult = performRequest(post(SYMLINK_TOOL_URL).content(content));

        verify(mockToolApiService).symlink(request);
        assertResponse(mvcResult, tool, DockerCreatorUtils.TOOL_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailSymlinkTool() throws Exception {
        performUnauthorizedRequest(post(SYMLINK_TOOL_URL));
    }
}
