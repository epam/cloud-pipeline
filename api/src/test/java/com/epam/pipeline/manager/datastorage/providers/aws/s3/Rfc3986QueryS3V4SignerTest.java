/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.services.s3.internal.AWSS3V4Signer;
import com.amazonaws.util.SdkHttpUtils;
import org.junit.Test;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rfc3986QueryS3V4SignerTest {

    private static final String ENDPOINT = "https://filer.example.com:9021";
    private static final String BUCKET_PATH = "/bucket";
    private static final String FOLDER_WITH_SPACES = "data/rw/results/GMU MLD/";
    private static final String ENCODED_QUERY_STRING =
            "delimiter=%2F&list-type=2&prefix=data%2Frw%2Fresults%2FGMU%20MLD%2F";
    private static final String OBJECT_PATH = BUCKET_PATH + "/data/rw/results/GMU%20MLD/results.txt";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SUB_RESOURCE = "uploads";
    private static final AWSCredentials CREDENTIALS = new BasicAWSCredentials("access-key-id", "secret-access-key");
    private static final Date SIGNING_DATE = new Date(1600000000000L);

    private final Rfc3986QueryS3V4Signer signer = configure(new Rfc3986QueryS3V4Signer());
    private final AWSS3V4Signer defaultSigner = configure(new AWSS3V4Signer());

    @Test
    public void testQueryStringIsSentAsSigned() {
        final Request<?> request = listRequest();

        signer.sign(request, CREDENTIALS);

        assertEquals(BUCKET_PATH + "?" + ENCODED_QUERY_STRING, request.getResourcePath());
        assertTrue(request.getParameters().isEmpty());
        assertEquals(ENDPOINT + BUCKET_PATH + "?" + ENCODED_QUERY_STRING, url(request));
        assertFalse(url(request).contains("+"));
    }

    @Test
    public void testSignatureMatchesTheDefaultOne() {
        final Request<?> request = listRequest();
        final Request<?> defaultRequest = listRequest();

        signer.sign(request, CREDENTIALS);
        defaultSigner.sign(defaultRequest, CREDENTIALS);

        assertEquals(defaultRequest.getHeaders().get(AUTHORIZATION), request.getHeaders().get(AUTHORIZATION));
    }

    /**
     * A retry is performed with the original query parameters and headers restored by
     * {@code com.amazonaws.http.AmazonHttpClient}, while a resource path is kept as it was modified.
     */
    @Test
    public void testRetriedRequestIsSignedTheSameWay() {
        final Request<?> request = listRequest();
        final Map<String, List<String>> originalParameters = new LinkedHashMap<>(request.getParameters());
        final Map<String, String> originalHeaders = new HashMap<>(request.getHeaders());

        signer.sign(request, CREDENTIALS);
        final String resourcePath = request.getResourcePath();
        final String signature = request.getHeaders().get(AUTHORIZATION);
        request.setParameters(originalParameters);
        request.setHeaders(originalHeaders);
        signer.sign(request, CREDENTIALS);

        assertEquals(resourcePath, request.getResourcePath());
        assertTrue(request.getParameters().isEmpty());
        assertEquals(signature, request.getHeaders().get(AUTHORIZATION));
    }

    /**
     * A sub resource parameter is added by the AWS SDK with a {@code null} value, which is signed
     * as {@code uploads=} but is sent as {@code uploads} by default.
     */
    @Test
    public void testSubResourceParameterIsSentAsSigned() {
        final Request<?> request = request(BUCKET_PATH);
        request.addParameter(SUB_RESOURCE, null);

        signer.sign(request, CREDENTIALS);

        assertEquals(BUCKET_PATH + "?" + SUB_RESOURCE + "=", request.getResourcePath());
        assertEquals(ENDPOINT + BUCKET_PATH + "?" + SUB_RESOURCE + "=", url(request));
    }

    /**
     * The AWS SDK sends query parameters of a {@code POST} request without a content as a payload
     * ({@link SdkHttpUtils#usePayloadForQueryParameters}), therefore such requests shall be kept as they are.
     */
    @Test
    public void testRequestWithQueryParametersInPayloadIsSentAsIs() {
        final Request<?> request = request(BUCKET_PATH);
        request.setHttpMethod(HttpMethodName.POST);
        request.addParameter(SUB_RESOURCE, null);

        signer.sign(request, CREDENTIALS);

        assertEquals(BUCKET_PATH, request.getResourcePath());
        assertTrue(request.getParameters().containsKey(SUB_RESOURCE));
    }

    @Test
    public void testRequestWithoutQueryParametersIsSentAsIs() {
        final Request<?> request = objectRequest();
        final Request<?> defaultRequest = objectRequest();

        signer.sign(request, CREDENTIALS);
        defaultSigner.sign(defaultRequest, CREDENTIALS);

        assertEquals(OBJECT_PATH, request.getResourcePath());
        assertEquals(ENDPOINT + OBJECT_PATH, url(request));
        assertEquals(defaultRequest.getHeaders().get(AUTHORIZATION), request.getHeaders().get(AUTHORIZATION));
    }

    private Request<?> listRequest() {
        final Request<?> request = request(BUCKET_PATH);
        request.addParameter("list-type", "2");
        request.addParameter("delimiter", "/");
        request.addParameter("prefix", FOLDER_WITH_SPACES);
        return request;
    }

    private Request<?> objectRequest() {
        return request(OBJECT_PATH);
    }

    private Request<?> request(final String resourcePath) {
        final Request<?> request = new DefaultRequest<>("s3");
        request.setHttpMethod(HttpMethodName.GET);
        request.setEndpoint(URI.create(ENDPOINT));
        request.setResourcePath(resourcePath);
        return request;
    }

    /**
     * Builds a request url in the same way as it is done by
     * {@code com.amazonaws.http.apache.request.impl.ApacheHttpRequestFactory}.
     */
    private String url(final Request<?> request) {
        final String url = SdkHttpUtils.appendUri(request.getEndpoint().toString(), request.getResourcePath(), true);
        final String encodedParameters = SdkHttpUtils.encodeParameters(request);
        return Objects.isNull(encodedParameters) ? url : url + "?" + encodedParameters;
    }

    private <T extends AWS4Signer> T configure(final T signer) {
        signer.setServiceName("s3");
        signer.setRegionName("us-east-1");
        signer.setOverrideDate(SIGNING_DATE);
        return signer;
    }
}
