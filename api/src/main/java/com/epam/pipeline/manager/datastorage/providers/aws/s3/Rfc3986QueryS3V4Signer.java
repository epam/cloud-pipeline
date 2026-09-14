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

import com.amazonaws.Request;
import com.amazonaws.SignableRequest;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.internal.AWSS3V4Signer;
import com.amazonaws.util.SdkHttpUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * {@link AWSS3V4Signer} which sends a query string in exactly the same form as the one
 * a request signature is calculated over.
 *
 * The AWS SDK v1 encodes a query string twice and in two different ways. A signature is calculated
 * over a RFC 3986 encoded query string ({@code com.amazonaws.auth.AbstractAWSSigner#getCanonicalizedQueryString}
 * relies on {@link SdkHttpUtils#urlEncode}: a space becomes {@code %20}, an asterisk becomes {@code %2A},
 * a tilde is kept as is), while a request itself is sent with an {@code application/x-www-form-urlencoded}
 * query string ({@code com.amazonaws.http.apache.request.impl.ApacheHttpRequestFactory} relies on
 * {@code org.apache.http.client.utils.URLEncodedUtils#format}: a space becomes {@code +},
 * an asterisk is kept as is, a tilde becomes {@code %7E}).
 *
 * AWS S3 normalizes a received query string before a signature is validated, therefore the difference
 * doesn't matter there. Nevertheless some S3 compatible endpoints validate a signature against a query
 * string exactly as it was received. For such endpoints any request with a space (the most common case),
 * an asterisk or a tilde in a query parameter fails with {@code 403 SignatureDoesNotMatch}, f.e. a listing
 * of a folder which name contains a space.
 *
 * Signing a form encoded query string doesn't help, because such endpoints treat a received {@code +}
 * as a literal plus rather than as a space, so a query string shall be sent RFC 3986 encoded. The AWS SDK v1
 * doesn't provide any option for that, but a query string which is a part of an already encoded resource path
 * is sent as is. Therefore a canonical query string is moved to a resource path right before a signature
 * is calculated, while a canonical resource path and a canonical query string are restored from there.
 *
 * A resource path of any S3 request is url encoded by the AWS SDK
 * ({@code com.amazonaws.services.s3.internal.S3RequestEndpointResolver#resolveRequestEndpoint}),
 * hence the first {@code ?} of a resource path is always the one which is added here.
 *
 * Presigned urls are built with a RFC 3986 encoded query string by the AWS SDK itself
 * ({@code com.amazonaws.services.s3.internal.ServiceUtils#convertRequestToUrl}) and are not signed
 * via {@link #sign(SignableRequest, AWSCredentials)}, therefore they are left as is.
 */
public class Rfc3986QueryS3V4Signer extends AWSS3V4Signer {

    public static final String SIGNER_TYPE = "Rfc3986QueryS3V4SignerType";

    private static final String QUERY_STRING_SEPARATOR = "?";

    @Override
    public void sign(final SignableRequest<?> request, final AWSCredentials credentials) {
        moveQueryStringToResourcePath(request);
        super.sign(request, credentials);
    }

    @Override
    protected String getCanonicalizedResourcePath(final String resourcePath, final boolean urlEncode) {
        return super.getCanonicalizedResourcePath(trimQueryString(resourcePath), urlEncode);
    }

    @Override
    protected String getCanonicalizedQueryString(final SignableRequest<?> request) {
        final String queryString = extractQueryString(request.getResourcePath());
        return Objects.isNull(queryString)
                ? super.getCanonicalizedQueryString(request)
                : queryString;
    }

    /**
     * Appends a canonical query string to a resource path and removes all the query parameters,
     * so that the AWS SDK sends a query string as it is.
     *
     * A request is signed once per attempt. The AWS SDK restores the original query parameters
     * before a retry ({@code com.amazonaws.http.AmazonHttpClient}) but keeps a resource path as it is,
     * therefore an already appended query string is replaced rather than duplicated.
     */
    private void moveQueryStringToResourcePath(final SignableRequest<?> request) {
        if (!(request instanceof Request)
                || MapUtils.isEmpty(request.getParameters())
                || SdkHttpUtils.usePayloadForQueryParameters(request)) {
            return;
        }
        final String resourcePath = trimQueryString(StringUtils.defaultString(request.getResourcePath()));
        ((Request<?>) request).setResourcePath(resourcePath + QUERY_STRING_SEPARATOR
                + getCanonicalizedQueryString(request.getParameters()));
        request.getParameters().clear();
    }

    private String trimQueryString(final String resourcePath) {
        final int separatorIndex = queryStringIndex(resourcePath);
        return separatorIndex < 0 ? resourcePath : resourcePath.substring(0, separatorIndex);
    }

    private String extractQueryString(final String resourcePath) {
        final int separatorIndex = queryStringIndex(resourcePath);
        return separatorIndex < 0 ? null : resourcePath.substring(separatorIndex + 1);
    }

    private int queryStringIndex(final String resourcePath) {
        return Objects.isNull(resourcePath) ? -1 : resourcePath.indexOf(QUERY_STRING_SEPARATOR);
    }
}
