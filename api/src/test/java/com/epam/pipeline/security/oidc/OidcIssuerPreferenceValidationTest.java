/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.oidc;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.manager.preference.PreferenceValidators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preference as an administrator actually writes it: JSON through the real validator.
 * <p>
 * {@link OidcIssuerValidatorTest} covers the rules against objects built in code, which skips the
 * parsing — and parsing is where a configuration most often fails, because an unrecognised field
 * is refused rather than ignored.
 */
public class OidcIssuerPreferenceValidationTest {

    private static final String GITHUB_ACTIONS = "[{\n"
            + "  \"issuer\": \"https://token.actions.githubusercontent.com\",\n"
            + "  \"audiences\": [\"https://cloud-pipeline.local/pipeline/restapi\"],\n"
            + "  \"algorithms\": [\"RS256\"],\n"
            + "  \"maxTokenLifetime\": 3600,\n"
            + "  \"clients\": [{\n"
            + "    \"id\": \"github-actions\",\n"
            + "    \"rules\": [\n"
            + "      { \"claim\": \"repository_id\", \"operator\": \"EQUALS\", \"value\": \"456789\" },\n"
            + "      { \"claim\": \"environment\", \"operator\": \"EQUALS\", \"value\": \"prod\" }\n"
            + "    ],\n"
            + "    \"assumeStrategy\": {\n"
            + "      \"type\": \"SPECIFIC_USER\",\n"
            + "      \"user\": \"GITHUB_CI_DEPLOY\",\n"
            + "      \"rolesToKeep\": [\"ROLE_USER\"],\n"
            + "      \"maxTokenLifetime\": 3600,\n"
            + "      \"ignoreSubjectTokenLifetime\": true,\n"
            + "      \"external\": false\n"
            + "    }\n"
            + "  }]\n"
            + "}]";

    private static final String ENTRA = "[{\n"
            + "  \"issuer\": \"https://login.microsoftonline.com/tenant-id/v2.0\",\n"
            + "  \"jwksUri\": \"https://login.microsoftonline.com/tenant-id/discovery/v2.0/keys\",\n"
            + "  \"audiences\": [\"11111111-2222-3333-4444-555555555555\"],\n"
            + "  \"algorithms\": [\"RS256\"],\n"
            + "  \"clients\": [{\n"
            + "    \"id\": \"portal\",\n"
            + "    \"rules\": [\n"
            + "      { \"claim\": \"azp\", \"operator\": \"EQUALS\", \"value\": \"aaaaaaaa-bbbb\" }\n"
            + "    ],\n"
            + "    \"requiredScopes\": [\"access_as_user\"],\n"
            + "    \"assumeStrategy\": {\n"
            + "      \"type\": \"REQUESTED_USER\",\n"
            + "      \"userClaim\": \"preferred_username\",\n"
            + "      \"rolesToKeep\": [\"ROLE_USER\", \"READER\"],\n"
            + "      \"maxTokenLifetime\": 900\n"
            + "    }\n"
            + "  }]\n"
            + "}]";

    /**
     * The configuration the manual documents for MAPPED_USER, so that the example an
     * administrator copies is known to parse. userMapping is the first nested object in a
     * strategy, and an unrecognised field anywhere in it refuses the whole preference.
     */
    private static final String MAPPED = "[{\n"
            + "  \"issuer\": \"https://token.actions.githubusercontent.com\",\n"
            + "  \"audiences\": [\"https://cloud-pipeline.local/pipeline/restapi\"],\n"
            + "  \"algorithms\": [\"RS256\"],\n"
            + "  \"clients\": [{\n"
            + "    \"id\": \"github-mapped\",\n"
            + "    \"rules\": [\n"
            + "      { \"claim\": \"repository_owner\", \"operator\": \"EQUALS\", \"value\": \"octo-org\" }\n"
            + "    ],\n"
            + "    \"assumeStrategy\": {\n"
            + "      \"type\": \"MAPPED_USER\",\n"
            + "      \"userMapping\": {\n"
            + "        \"claim\": \"repository_owner\",\n"
            + "        \"metadataKey\": \"github_owner\"\n"
            + "      },\n"
            + "      \"rolesToKeep\": [\"ROLE_USER\"],\n"
            + "      \"ignoreSubjectTokenLifetime\": true\n"
            + "    }\n"
            + "  }]\n"
            + "}]";

    private static final String CLAIM_LINE = "\"claim\": \"repository_owner\",";

    @BeforeEach
    public void initMapper() {
        // the mapper is a Spring singleton published by afterPropertiesSet, and this test has no context
        new JsonMapper().afterPropertiesSet();
    }

    @Test
    public void shouldAcceptTheDocumentedGithubActionsConfiguration() {
        assertThat(isValid(GITHUB_ACTIONS)).isTrue();
    }

    @Test
    public void shouldAcceptTheDocumentedEntraConfiguration() {
        assertThat(isValid(ENTRA)).isTrue();
    }

    @Test
    public void shouldAcceptAnAbsentOrEmptyConfiguration() {
        assertThat(isValid(null)).isTrue();
        assertThat(isValid("[]")).isTrue();
    }

    /**
     * An unrecognised field is refused rather than dropped. That matters for a field that was
     * renamed: silently ignoring the old name would issue tokens without the narrowing the
     * administrator asked for, which is the opposite of failing closed.
     */
    @Test
    public void shouldRejectAnUnrecognisedField() {
        assertThat(isValid(GITHUB_ACTIONS.replace("rolesToKeep", "authoritiesToKeep"))).isFalse();
        assertThat(isValid(GITHUB_ACTIONS.replace("\"user\"", "\"userName\""))).isFalse();
    }

    @Test
    public void shouldRejectMalformedJson() {
        assertThat(isValid("[{\"issuer\": ")).isFalse();
        assertThat(isValid("not json at all")).isFalse();
    }

    private static boolean isValid(final String preference) {
        return PreferenceValidators.isValidOidcTokenExchangeIssuers.test(preference, null);
    }

    @Test
    public void shouldAcceptTheDocumentedMappedUserConfiguration() {
        assertThat(isValid(MAPPED)).isTrue();
    }

    @Test
    public void shouldRejectAnUnrecognisedFieldInsideAUserMapping() {
        assertThat(isValid(MAPPED.replace("metadataKey", "metadata_key"))).isFalse();
        assertThat(isValid(MAPPED.replace(CLAIM_LINE, "\"claimName\": \"repository_owner\","))).isFalse();
    }

    @Test
    public void shouldRejectAMappedUserConfigurationMissingHalfItsMapping() {
        assertThat(isValid(MAPPED.replace("\"github_owner\"", "\"\""))).isFalse();
        assertThat(isValid(MAPPED.replace(CLAIM_LINE, "\"claim\": \"\","))).isFalse();
    }
}
