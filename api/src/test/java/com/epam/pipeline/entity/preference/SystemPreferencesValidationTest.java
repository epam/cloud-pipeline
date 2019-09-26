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

package com.epam.pipeline.entity.preference;

import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.git.GitlabClient;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.anyString;

public class SystemPreferencesValidationTest extends AbstractManagerTest {
    private static final int THOUSAND = 1000;
    private static final int FIVE_THOUSAND = 5000;
    private static final int THIRTY = 30;
    private static final int TEST_SERVER_PORT = 9000;

    @Autowired
    private SystemPreferences preferences;

    @MockBean
    private GitManager gitManager;

    @SpyBean
    private PreferenceManager preferenceManager;

    @Mock
    private GitlabClient mockGitlabClient;

    private GitlabVersion supportedVersion;
    private GitlabVersion unsupportedVersion;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Assert.assertNotNull(preferences);

        supportedVersion = new GitlabVersion();
        supportedVersion.setVersion("9.0");
        unsupportedVersion = new GitlabVersion();
        unsupportedVersion.setVersion("9.5");

        Mockito.when(gitManager.getGitlabRootClient(anyString(), anyString(), Mockito.anyLong(),
                anyString())).thenReturn(mockGitlabClient);
    }

    @Test
    public void testValidateNumericFields() {
        testGreater(SystemPreferences.COMMIT_TIMEOUT, 0);
        testGreater(SystemPreferences.DATA_STORAGE_MAX_DOWNLOAD_SIZE, 0);
        testGreater(SystemPreferences.DATA_STORAGE_TEMP_CREDENTIALS_DURATION, 0);
        testGreater(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_CONNECT_TIMEOUT, THIRTY);
        testGreater(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_READ_TIMEOUT, 0);
        testGreaterOrEquals(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES, 0);
        testGreaterOrEquals(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_CRITICAL_VULNERABILITIES, 0);
        testGreaterOrEquals(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_HIGH_VULNERABILITIES, 0);
        testGreater(SystemPreferences.CLUSTER_AUTOSCALE_RATE, THOUSAND);
        testGreater(SystemPreferences.CLUSTER_MAX_SIZE, 0);
        testGreaterOrEquals(SystemPreferences.CLUSTER_MIN_SIZE, 0);
        testGreater(SystemPreferences.CLUSTER_NODEUP_MAX_THREADS, 0);
        testGreater(SystemPreferences.CLUSTER_NODEUP_RETRY_COUNT, 0);
        testGreater(SystemPreferences.CLUSTER_INSTANCE_HDD, 0);
        testGreater(SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES, 0);
        testGreater(SystemPreferences.CLUSTER_SPOT_BID_PRICE, 0);
        testGreater(SystemPreferences.LAUNCH_JWT_TOKEN_EXPIRATION, 0);
        testGreater(SystemPreferences.LAUNCH_MAX_SCHEDULED_NUMBER, 0);
        testGreater(SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE, FIVE_THOUSAND);
    }

    @Test
    public void testValidateBooleanFields() {
        testBoolean(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ALL_REGISTRIES);
        testBoolean(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED);
        testBoolean(SystemPreferences.CLUSTER_KILL_NOT_MATCHING_NODES);
        testBoolean(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING);
        testBoolean(SystemPreferences.CLUSTER_RANDOM_SCHEDULING);
        testBoolean(SystemPreferences.CLUSTER_HIGH_NON_BATCH_PRIORITY);
        testBoolean(SystemPreferences.CLUSTER_SPOT);
    }

    @Test
    public void testNoValidationFields() {
        testNoValidation(SystemPreferences.UI_PROJECT_INDICATOR);
        testNoValidation(SystemPreferences.LAUNCH_CMD_TEMPLATE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateFail() {
        Preference preference = new Preference();
        preference.setName(SystemPreferences.CLUSTER_AUTOSCALE_RATE.getKey());
        preference.setValue("ggg");

        preferences.validate(Collections.singletonList(preference));
    }

    @Test
    public void testValidateGit() throws GitClientException {
        Mockito.when(mockGitlabClient.getVersion()).thenReturn(supportedVersion);
        validateGitPreferences();
    }

    @Test
    public void testValidateInstanceType() {
        Preference preference = new Preference(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey(), "d5.*");
        Preference pref2 = new Preference(SystemPreferences.CLUSTER_INSTANCE_TYPE.getKey(), "d5.small");
        preferences.validate(Arrays.asList(preference, pref2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateDockerSecurityScanGroupFail() {
        Preference preference = SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED.toPreference();
        preference.setValue("true");
        preferences.validate(Collections.singletonList(preference));
    }

    @Test
    public void testValidateDockerSecurityScanGroup() {
        WireMockServer wireMockServer = new WireMockServer(TEST_SERVER_PORT);
        wireMockServer.start();
        try {
            validateDockerSecurityScanGroup();
        } finally {
            wireMockServer.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateGitInvalidVersion() throws GitClientException {
        Mockito.when(mockGitlabClient.getVersion()).thenReturn(unsupportedVersion);
        validateGitPreferences();
    }


    @Test
    public void testDependentPreferencesMapAppropriateFilling() {
        Preference clusterAllowedInstanceTypes = new Preference(
                SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey(), "d5.*");
        Preference clusterInstanceType = new Preference(
                SystemPreferences.CLUSTER_INSTANCE_TYPE.getKey(), "d5.small");

        Mockito.when(preferenceManager.getSystemPreference(SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES))
                .thenReturn(clusterAllowedInstanceTypes);
        preferences.validate(Collections.singletonList(clusterInstanceType));
    }


    private void testNoValidation(AbstractSystemPreference preference) {
        Preference pref = new Preference();
        pref.setName(preference.getKey());
        pref.setValue("whatever");
        Assert.assertTrue(preferences.isValid(pref, null));
    }

    private void testBoolean(AbstractSystemPreference preference) {
        Preference pref = new Preference();
        pref.setName(preference.getKey());

        pref.setValue("true");
        Assert.assertTrue(preferences.isValid(pref, null));

        pref.setValue("false");
        Assert.assertTrue(preferences.isValid(pref, null));

        pref.setValue("whatever");
        Assert.assertFalse(preferences.isValid(pref, null));
    }

    private void testGreater(AbstractSystemPreference preference, int value) {
        Preference pref = new Preference();
        pref.setName(preference.getKey());

        pref.setValue(Integer.toString(value + 1));
        Assert.assertTrue(preferences.isValid(pref, null));

        pref.setValue(Integer.toString(value - 1));
        Assert.assertFalse(preferences.isValid(pref, null));
    }

    private void testGreaterOrEquals(AbstractSystemPreference<?> preference, int value) {
        Assert.assertTrue(preference.getValidator().test(Integer.toString(value + 1), null));
        Assert.assertTrue(preference.getValidator().test(Integer.toString(value), null));
        Assert.assertFalse(preference.getValidator().test(Integer.toString(value - 1), null));
    }

    private void validateDockerSecurityScanGroup() {
        Preference preference = SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED.toPreference();
        preference.setValue("true");
        Preference required = SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL.toPreference();
        required.setValue("http://localhost:9000/");

        preferences.validate(Arrays.asList(preference, required));
    }

    private void validateGitPreferences() {
        Preference host = new Preference(SystemPreferences.GIT_HOST.getKey(), "http://localhost");
        Preference user = new Preference(SystemPreferences.GIT_USER_NAME.getKey(), "admin");
        Preference token = new Preference(SystemPreferences.GIT_TOKEN.getKey(), "eefrfq21");
        Preference userId = new Preference(SystemPreferences.GIT_USER_ID.getKey(), "1");

        preferences.validate(Arrays.asList(host, user, token, userId));
    }


}