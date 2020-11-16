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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.docker.DockerRegistryApiService;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.controller.docker.DockerRegistryController.DOCKER_LOGIN_SCRIPT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = DockerRegistryController.class)
public class DockerRegistryControllerTest extends AbstractControllerTest {

    private static final String DOCKER_REGISTRY_URL = SERVLET_PATH + "/dockerRegistry";
    private static final String REGISTER_DOCKER_REGISTRY_URL = DOCKER_REGISTRY_URL + "/register";
    private static final String UPDATE_DOCKER_REGISTRY_URL = DOCKER_REGISTRY_URL + "/update";
    private static final String UPDATE_DOCKER_REGISTRY_CREDS_URL = DOCKER_REGISTRY_URL + "/updateCredentials";
    private static final String OAUTH_DOCKER_REGISTRY_URL = DOCKER_REGISTRY_URL + "/oauth";
    private static final String LOAD_TREE_REGISTRY_URL = DOCKER_REGISTRY_URL + "/loadTree";
    private static final String LOAD_CERTS_REGISTRY_URL = DOCKER_REGISTRY_URL + "/loadCerts";
    private static final String LOAD_REGISTRY_URL = DOCKER_REGISTRY_URL + "/%s/load";
    private static final String DELETE_REGISTRY_URL = DOCKER_REGISTRY_URL + "/%d/delete";
    private static final String CERT_REGISTRY_URL = DOCKER_REGISTRY_URL + "/%d/cert";
    private static final String LOGIN_REGISTRY_URL = DOCKER_REGISTRY_URL + "/%d/login";
    private static final String NOTIFY_REGISTRY_URL = DOCKER_REGISTRY_URL + "/notify";

    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private static final String FORCE = "force";
    private static final String TRUE_AS_STRING = String.valueOf(true);
    private static final String REGISTRY_PATH = "Registry-Path";
    private static final String SERVICE = "service";
    private static final String SCOPE = "scope";
    private final byte[] bytes = {1, 1, 1};

    private final DockerRegistry dockerRegistry = DockerCreatorUtils.getDockerRegistry();
    private final DockerRegistryVO dockerRegistryVO = DockerCreatorUtils.getDockerRegistryVO();
    private final JwtRawToken jwtRawToken = SecurityCreatorUtils.getJwtRawToken();
    private final DockerRegistryList dockerRegistryList = DockerCreatorUtils.getDockerRegistryList();
    private final DockerRegistryEventEnvelope eventEnvelope = DockerCreatorUtils.getDockerRegistryEventEnvelope();
    private final UserContext userContext = SecurityCreatorUtils.getUserContext();

    @Autowired
    private DockerRegistryApiService mockDockerRegistryApiService;

    @Autowired
    private UserAccessService mockAccessService;

    @Test
    @WithMockUser
    public void shouldCreateDockerRegistry() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dockerRegistryVO);
        doReturn(dockerRegistry).when(mockDockerRegistryApiService).create(refEq(dockerRegistryVO));

        final MvcResult mvcResult = performRequest(post(REGISTER_DOCKER_REGISTRY_URL).content(content));

        verify(mockDockerRegistryApiService).create(refEq(dockerRegistryVO));
        assertResponse(mvcResult, dockerRegistry, DockerCreatorUtils.DOCKER_REGISTRY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailCreateDockerRegistry() {
        performUnauthorizedRequest(post(REGISTER_DOCKER_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateDockerRegistry() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dockerRegistry);
        doReturn(dockerRegistry).when(mockDockerRegistryApiService).updateDockerRegistry(dockerRegistry);

        final MvcResult mvcResult = performRequest(post(UPDATE_DOCKER_REGISTRY_URL).content(content));

        verify(mockDockerRegistryApiService).updateDockerRegistry(dockerRegistry);
        assertResponse(mvcResult, dockerRegistry, DockerCreatorUtils.DOCKER_REGISTRY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateDockerRegistry() {
        performUnauthorizedRequest(post(UPDATE_DOCKER_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateDockerRegistryCredentials() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dockerRegistryVO);
        doReturn(dockerRegistry).when(mockDockerRegistryApiService)
                .updateDockerRegistryCredentials(refEq(dockerRegistryVO));

        final MvcResult mvcResult = performRequest(post(UPDATE_DOCKER_REGISTRY_CREDS_URL).content(content));

        verify(mockDockerRegistryApiService).updateDockerRegistryCredentials(refEq(dockerRegistryVO));
        assertResponse(mvcResult, dockerRegistry, DockerCreatorUtils.DOCKER_REGISTRY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailUpdateDockerRegistryCredentials() {
        performUnauthorizedRequest(post(UPDATE_DOCKER_REGISTRY_CREDS_URL));
    }

    @Test
    @WithMockUser
    public void shouldOauthEndpoint() throws Exception {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(SERVICE, TEST_STRING);
        params.add(SCOPE, TEST_STRING);
        final String testNameAndPass = TEST_STRING + ":" + TEST_STRING;
        final String encodedNameAndPass = new String(Base64.getEncoder()
                .encode(testNameAndPass.getBytes(StandardCharsets.UTF_8)));
        doReturn(userContext).when(mockAccessService).getJwtUser(any(), any());
        doReturn(jwtRawToken).when(mockDockerRegistryApiService)
                .issueTokenForDockerRegistry(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(OAUTH_DOCKER_REGISTRY_URL)
                .header("Authorization", "Basic" + encodedNameAndPass).params(params));

        verify(mockDockerRegistryApiService)
                .issueTokenForDockerRegistry(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING);
        final JwtRawToken actualResult = JsonMapper.parseData(mvcResult.getResponse().getContentAsString(),
                SecurityCreatorUtils.JWT_RAW_TOKEN_INSTANCE_TYPE);
        Assert.assertEquals(jwtRawToken, actualResult);
    }

    @Test
    public void shouldFailOauthEndpoint() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(SERVICE, TEST_STRING);
        params.add(SCOPE, TEST_STRING);
        performUnauthorizedRequest(get(OAUTH_DOCKER_REGISTRY_URL).params(params));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegistryContent() {
        doReturn(dockerRegistryList).when(mockDockerRegistryApiService).loadAllRegistriesContent();

        final MvcResult mvcResult = performRequest(get(LOAD_TREE_REGISTRY_URL));

        verify(mockDockerRegistryApiService).loadAllRegistriesContent();
        assertResponse(mvcResult, dockerRegistryList, DockerCreatorUtils.DOCKER_REGISTRY_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadAllRegistryContent() {
        performUnauthorizedRequest(get(LOAD_TREE_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadRegistryCertificates() {
        final Map<String, String> loadedCerts = Collections.singletonMap(TEST_STRING, TEST_STRING);
        doReturn(dockerRegistryList).when(mockDockerRegistryApiService).listDockerRegistriesWithCerts();

        final MvcResult mvcResult = performRequest(get(LOAD_CERTS_REGISTRY_URL));

        verify(mockDockerRegistryApiService).listDockerRegistriesWithCerts();
        assertResponse(mvcResult, loadedCerts, CommonCreatorConstants.STRING_MAP_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadRegistryCertificates() {
        performUnauthorizedRequest(get(LOAD_CERTS_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadDockerRegistry() {
        doReturn(dockerRegistry).when(mockDockerRegistryApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOAD_REGISTRY_URL, ID)));

        verify(mockDockerRegistryApiService).load(ID);
        assertResponse(mvcResult, dockerRegistry, DockerCreatorUtils.DOCKER_REGISTRY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailLoadDockerRegistry() {
        performUnauthorizedRequest(get(LOAD_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDockerRegistry() {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(FORCE, TRUE_AS_STRING);
        doReturn(dockerRegistry).when(mockDockerRegistryApiService).delete(ID, true);

        final MvcResult mvcResult = performRequest(delete(String.format(DELETE_REGISTRY_URL, ID)).params(params));

        verify(mockDockerRegistryApiService).delete(ID, true);
        assertResponse(mvcResult, dockerRegistry, DockerCreatorUtils.DOCKER_REGISTRY_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteDockerRegistry() {
        performUnauthorizedRequest(delete(DELETE_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldNotifyRegistryEvents() throws Exception {
        final Tool tool = DockerCreatorUtils.getTool();
        final List<Tool> tools = Collections.singletonList(tool);
        final String content = getObjectMapper().writeValueAsString(eventEnvelope);
        doReturn(tools).when(mockDockerRegistryApiService)
                .notifyDockerRegistryEvents(eq(TEST_STRING), refEq(eventEnvelope));

        final MvcResult mvcResult = performRequest(
                post(NOTIFY_REGISTRY_URL).header(REGISTRY_PATH, TEST_STRING).content(content));

        verify(mockDockerRegistryApiService).notifyDockerRegistryEvents(eq(TEST_STRING), refEq(eventEnvelope));
        assertResponse(mvcResult, tools, DockerCreatorUtils.TOOL_LIST_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailNotifyRegistryEvents() {
        performUnauthorizedRequest(get(NOTIFY_REGISTRY_URL));
    }

    @Test
    @WithMockUser
    public void shouldDownloadRegistryCertificate() {
        doReturn(bytes).when(mockDockerRegistryApiService).getCertificateContent(ID);

        final MvcResult mvcResult = performRequest(get(String.format(CERT_REGISTRY_URL, ID)),
                OCTET_STREAM_CONTENT_TYPE);

        verify(mockDockerRegistryApiService).getCertificateContent(ID);
        assertFileResponse(mvcResult, CERTIFICATE_NAME, bytes);
    }

    @Test
    public void shouldFailDownloadRegistryCertificate() {
        performUnauthorizedRequest(get(String.format(CERT_REGISTRY_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDownloadConfigScript() {
        doReturn(bytes).when(mockDockerRegistryApiService).getConfigScript(ID);

        final MvcResult mvcResult = performRequest(get(String.format(LOGIN_REGISTRY_URL, ID)),
                OCTET_STREAM_CONTENT_TYPE);

        verify(mockDockerRegistryApiService).getConfigScript(ID);
        assertFileResponse(mvcResult, DOCKER_LOGIN_SCRIPT, bytes);
    }

    @Test
    public void shouldFailDownloadConfigScript() {
        performUnauthorizedRequest(get(String.format(LOGIN_REGISTRY_URL, ID)));
    }
}
