/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPRegion;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbilling.Cloudbilling;
import com.google.api.services.compute.Compute;
import com.google.api.services.iamcredentials.v1.IAMCredentials;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Component
public class GCPClient {

    private static final List<String> COMPUTE_SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/compute");
    private static final List<String> BILLING_SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final List<String> IAM_SCOPES = Collections.singletonList("https://www.googleapis.com/auth/iam");

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public GCPClient() throws GeneralSecurityException, IOException {
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.jsonFactory = JacksonFactory.getDefaultInstance();
    }

    public Compute buildComputeClient(final GCPRegion region) throws IOException {
        final GoogleCredential credentials = buildCredentials(region);
        return new Compute.Builder(httpTransport,
                jsonFactory, credentials.createScoped(COMPUTE_SCOPES))
                .setApplicationName(region.getApplicationName())
                .build();
    }

    public Cloudbilling buildBillingClient(final GCPRegion region) throws IOException {
        final GoogleCredential credentials = buildCredentials(region);
        return new Cloudbilling.Builder(httpTransport, jsonFactory, credentials.createScoped(BILLING_SCOPES))
//                .setApplicationName(region.getApplicationName())
                .build();
    }

    public IAMCredentials buildIAMCredentialsClient(final GCPRegion region) throws IOException {
        final GoogleCredential credentials = buildCredentials(region).createScoped(IAM_SCOPES);
        return new IAMCredentials.Builder(httpTransport, jsonFactory, credentials)
                .setApplicationName(region.getApplicationName())
                .build();
    }

    private GoogleCredential buildCredentials(final GCPRegion region) throws IOException {
        if (StringUtils.isBlank(region.getAuthFile())) {
            return GoogleCredential.getApplicationDefault();
        }
        try (InputStream stream = new FileInputStream(region.getAuthFile())) {
            return GoogleCredential.fromStream(stream);
        }
    }
}
