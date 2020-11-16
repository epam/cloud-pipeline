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

import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.manager.pipeline.ToolGroupApiService;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = ToolGroupController.class)
public class ToolGroupControllerTest extends AbstractControllerTest {

    private static final String TOOL_GROUP_URL = SERVLET_PATH + "/toolGroup";
    private static final String TOOL_GROUP_LIST_URL = TOOL_GROUP_URL + "/list";
    private static final String PRIVATE_TOOL_GROUP_URL = TOOL_GROUP_URL + "/private";
    private static final String ISSUES_COUNT_TOOL_GROUP_URL = TOOL_GROUP_URL + "/%d/issuesCount";

    private static final String REGISTRY = "registry";
    private static final String REGISTRY_ID = "registryId";
    private static final String STRING_ID = "id";
    private static final String FORCE = "force";
    private static final String ID_AS_STRING = String.valueOf(ID);
    private static final String TRUE_AS_STRING = String.valueOf(true);
    private static final String FALSE_AS_STRING = String.valueOf(false);

    private final ToolGroup toolGroup = DockerCreatorUtils.getToolGroup();

    @Autowired
    private ToolGroupApiService mockToolGroupApiService;

    @Test
    @WithMockUser
    public void shouldListToolGroups() {
        final List<ToolGroup> toolGroups = Collections.singletonList(toolGroup);
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY, TEST_STRING);
        doReturn(toolGroups).when(mockToolGroupApiService).loadByRegistryNameOrId(TEST_STRING);

        final MvcResult mvcResult = performRequest(get(TOOL_GROUP_LIST_URL).params(params));

        Mockito.verify(mockToolGroupApiService).loadByRegistryNameOrId(TEST_STRING);
        assertResponse(mvcResult, toolGroups, DockerCreatorUtils.TOOL_GROUP_LIST_TYPE);
    }

    @Test
    public void shouldFailListToolGroups() {
        performUnauthorizedRequest(get(TOOL_GROUP_LIST_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreatePrivateToolGroup() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(REGISTRY_ID, ID_AS_STRING);
        doReturn(toolGroup).when(mockToolGroupApiService).createPrivate(ID);

        final MvcResult mvcResult = performRequest(post(PRIVATE_TOOL_GROUP_URL).params(params));

        Mockito.verify(mockToolGroupApiService).createPrivate(ID);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    public void shouldFailCreatePrivateToolGroup() {
        performUnauthorizedRequest(post(PRIVATE_TOOL_GROUP_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadToolGroup() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(STRING_ID, ID_AS_STRING);
        doReturn(toolGroup).when(mockToolGroupApiService).loadByNameOrId(ID_AS_STRING);

        final MvcResult mvcResult = performRequest(get(TOOL_GROUP_URL).params(params));

        Mockito.verify(mockToolGroupApiService).loadByNameOrId(ID_AS_STRING);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    public void shouldFailLoadToolGroup() {
        performUnauthorizedRequest(get(TOOL_GROUP_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadToolGroupWithIssuesCount() {
        final ToolGroupWithIssues toolGroupWithIssues = DockerCreatorUtils.getToolGroupWithIssues();
        doReturn(toolGroupWithIssues).when(mockToolGroupApiService).loadToolsWithIssuesCount(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ISSUES_COUNT_TOOL_GROUP_URL, ID)));

        Mockito.verify(mockToolGroupApiService).loadToolsWithIssuesCount(ID);
        assertResponse(mvcResult, toolGroupWithIssues, DockerCreatorUtils.TOOL_GROUP_WITH_ISSUES_TYPE);
    }

    @Test
    public void shouldFailLoadToolGroupWithIssuesCount() {
        performUnauthorizedRequest(get(String.format(ISSUES_COUNT_TOOL_GROUP_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateToolGroup() throws Exception {
        final String content = getObjectMapper().writeValueAsString(toolGroup);
        doReturn(toolGroup).when(mockToolGroupApiService).create(toolGroup);

        final MvcResult mvcResult = performRequest(post(TOOL_GROUP_URL).content(content));

        Mockito.verify(mockToolGroupApiService).create(toolGroup);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    public void shouldFailCreateToolGroup() {
        performUnauthorizedRequest(post(TOOL_GROUP_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateToolGroup() throws Exception {
        final String content = getObjectMapper().writeValueAsString(toolGroup);
        doReturn(toolGroup).when(mockToolGroupApiService).update(toolGroup);

        final MvcResult mvcResult = performRequest(put(TOOL_GROUP_URL).content(content));

        Mockito.verify(mockToolGroupApiService).update(toolGroup);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    public void shouldFailUpdateToolGroup() {
        performUnauthorizedRequest(put(TOOL_GROUP_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteForceToolGroup() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(STRING_ID, ID_AS_STRING);
        params.add(FORCE, TRUE_AS_STRING);
        doReturn(toolGroup).when(mockToolGroupApiService).deleteForce(ID_AS_STRING);

        final MvcResult mvcResult = performRequest(delete(TOOL_GROUP_URL).params(params));

        Mockito.verify(mockToolGroupApiService).deleteForce(ID_AS_STRING);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteToolGroup() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(STRING_ID, ID_AS_STRING);
        params.add(FORCE, FALSE_AS_STRING);
        doReturn(toolGroup).when(mockToolGroupApiService).delete(ID_AS_STRING);

        final MvcResult mvcResult = performRequest(delete(TOOL_GROUP_URL).params(params));

        Mockito.verify(mockToolGroupApiService).delete(ID_AS_STRING);
        assertResponse(mvcResult, toolGroup, DockerCreatorUtils.TOOL_GROUP_TYPE);
    }

    @Test
    public void shouldFailDeleteToolGroup() {
        performUnauthorizedRequest(delete(TOOL_GROUP_URL));
    }
}
