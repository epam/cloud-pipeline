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

package com.epam.pipeline.dts.transfer.model.google;

import com.epam.pipeline.dts.transfer.exception.CredentialsParsingException;
import com.epam.pipeline.dts.transfer.model.Credentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@NoArgsConstructor
@Slf4j
public class GoogleCredentials implements Credentials {
    private String refreshToken;
    private String clientId;
    private String clientSecret;

    public static GoogleCredentials from(final String credentials) {
        try {
            return new ObjectMapper().readValue(credentials, GoogleCredentials.class);
        } catch (IOException e) {
            throw new CredentialsParsingException("Google credentials parsing error", e);
        }
    }
}

