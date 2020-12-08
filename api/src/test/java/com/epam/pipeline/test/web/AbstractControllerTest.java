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

package com.epam.pipeline.test.web;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebTestConfiguration
public abstract class AbstractControllerTest {

    protected static final String SERVLET_PATH = "/restapi";
    protected static final String CERTIFICATE_NAME = "ca.crt";
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    protected static final String EXPECTED_CONTENT_TYPE = "application/json;charset=UTF-8";
    protected static final String MULTIPART_CONTENT_FILE_NAME = "file.txt";
    protected static final String  MULTIPART_CONTENT_FILE_CONTENT = "content of file.txt";
    protected static final String MULTIPART_CONTENT_TYPE =
            "multipart/form-data; boundary=--------------------------boundary";
    protected static final String MULTIPART_CONTENT =
            "----------------------------boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\" " + MULTIPART_CONTENT_FILE_NAME + " \"\r\n" +
            "Content-Type:  application/octet-stream\r\n" +
            "\r\n" +
            MULTIPART_CONTENT_FILE_CONTENT +
            "\r\n" +
            "----------------------------boundary";

    private MockMvc mockMvc;
    private ObjectMapper deserializationMapper;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    protected WebApplicationContext wac;

    @Before
    public void setup() {
        // checks that all required dependencies are provided.
        assertNotNull("WebApplicationContext isn't provided.", wac);
        assertNotNull("ObjectMapper isn't provided.", objectMapper);

        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity()).build();
        deserializationMapper = JsonMapper.newInstance().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    protected final MockMvc mvc() {
        return mockMvc;
    }

    protected final JsonMapper getObjectMapper() {
        return objectMapper;
    }

    public <T> ResponseResult<T> buildExpectedResult(final T payload) {
        final ResponseResult<T> expectedResult = new ResponseResult<>();
        expectedResult.setStatus("OK");
        expectedResult.setPayload(payload);
        return expectedResult;
    }

    @SneakyThrows
    public <T> void assertResponse(final MvcResult mvcResult,
                                   final T payload,
                                   final TypeReference<Result<T>> typeReference) {
        final ResponseResult<T> expectedResult = buildExpectedResult(payload);

        final String actual = mvcResult.getResponse().getContentAsString();
        assertTrue(StringUtils.isNotBlank(actual));
        assertThat(actual).isEqualToIgnoringWhitespace(objectMapper.writeValueAsString(expectedResult));

        final Result<T> actualResult = JsonMapper.parseData(actual, typeReference, deserializationMapper);
        assertNotNull(actualResult);
        assertEquals(expectedResult.getPayload(), actualResult.getPayload());
    }

    public void assertFileResponse(final MvcResult mvcResult, final String fileName, final byte[] fileContent) {
        assertResponseHeader(mvcResult, fileName);
        assertContent(mvcResult, fileContent);
    }

    public void assertResponseHeader(final MvcResult mvcResult, final String fileName) {
        assertThat(mvcResult.getResponse().getHeader(CONTENT_DISPOSITION_HEADER)).contains(fileName);
    }

    public void assertContent(final MvcResult mvcResult, final byte[] fileContent) {
        assertThat(mvcResult.getResponse().getContentAsByteArray()).isEqualTo(fileContent);
    }

    @SneakyThrows
    public void assertRequestFile(final MultipartFile capturedValue,
                                      final String expectedFileName,
                                      final byte[] expectedContentAsBytes) {
        assertThat(capturedValue.getOriginalFilename()).isEqualTo(expectedFileName);
        assertThat(capturedValue.getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(capturedValue.getBytes()).isEqualTo(expectedContentAsBytes);
    }

    @SneakyThrows
    public void performUnauthorizedRequest(final MockHttpServletRequestBuilder requestBuilder) {
        mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @SneakyThrows
    public MvcResult performRequest(final MockHttpServletRequestBuilder requestBuilder) {
        return performRequest(requestBuilder, EXPECTED_CONTENT_TYPE);
    }


    @SneakyThrows
    public MvcResult performRequest(final MockHttpServletRequestBuilder requestBuilder, final String contentType) {
        return mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH)
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(contentType))
                .andReturn();
    }

    @SneakyThrows
    public MvcResult performRequest(final MockHttpServletRequestBuilder requestBuilder,
                                    final String requestContentType,
                                    final String responseContentType) {
        return mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH)
                .contentType(requestContentType))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(responseContentType))
                .andReturn();
    }

    @SneakyThrows
    public MvcResult performRedirectedRequest(final MockHttpServletRequestBuilder requestBuilder,
                                              final String redirectUrl) {
        return mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(redirectUrl))
                .andReturn();
    }

    @SneakyThrows
    public void performRequestWithoutResponse(final MockHttpServletRequestBuilder requestBuilder) {
        mockMvc.perform(requestBuilder
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk());
    }

    public MultiValueMap<String, String> multiValueMapOf(Object... objects) {
        final MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < objects.length; i += 2) {
            map.add(String.valueOf(objects[i]).replaceAll("[\\[\\]]", ""),
                    String.valueOf(objects[i + 1]).replaceAll("[\\[\\]]", ""));
        }
        return map;
    }
}
