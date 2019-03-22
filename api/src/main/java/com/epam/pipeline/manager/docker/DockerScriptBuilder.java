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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@link DockerScriptBuilder} provides method to fill in docker config script with {@link DockerRegistry}
 * parameters. Currently supported parameters: API_HOST, JWT_TOKEN, REGISTRY_ID, USER_NAME, REGISTRY_URL.
 * Expected parameter reference is "[PARAMETER_NAME]";
 */
@Service
public class DockerScriptBuilder {

    //3 months
    private static final Long LOGIN_TOKEN_EXPIRATION = 60L * 60 * 24 * 30 * 3;
    private static final String PARAMETER_TEMPLATE = "\\[%s\\]";

    private static final String API_HOST = "API_HOST";
    private static final String JWT_TOKEN = "JWT_TOKEN";
    private static final String REGISTRY_ID = "REGISTRY_ID";
    private static final String USER_NAME = "USER_NAME";
    private static final String REGISTRY_URL = "REGISTRY_URL";

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private AuthManager authManager;

    public String replaceTemplateParameters(DockerRegistry registry, String template) {
        ScriptTemplate result = new ScriptTemplate(template)
                .replaceParameter(API_HOST, preferenceManager.getPreference(SystemPreferences.BASE_API_HOST))
                .replaceParameter(JWT_TOKEN,
                        authManager.issueTokenForCurrentUser(LOGIN_TOKEN_EXPIRATION).getToken())
                .replaceParameter(USER_NAME, authManager.getAuthorizedUser())
                .replaceParameter(REGISTRY_ID, String.valueOf(registry.getId()))
                .replaceParameter(REGISTRY_URL, registry.getExternalUrl());
        return result.getValue();
    }

    @Getter
    @AllArgsConstructor
    private static class ScriptTemplate {
        private String value;

        private ScriptTemplate replaceParameter(String parameterName, String parameterValue) {
            value = value.replaceAll(String.format(PARAMETER_TEMPLATE, parameterName), parameterValue);
            return this;
        }
    }
}
