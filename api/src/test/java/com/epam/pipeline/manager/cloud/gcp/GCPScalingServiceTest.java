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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.region.GCPRegion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static com.epam.pipeline.manager.execution.SystemParams.*;

public class GCPScalingServiceTest {

    private static final String CRED_TEMPLATE = "{\n" +
            "  \"type\": \"service_account\",\n" +
            "  \"project_id\": \"\",\n" +
            "  \"private_key_id\": \"\",\n" +
            "  \"private_key\": \"\",\n" +
            "  \"client_email\": \"\",\n" +
            "  \"client_id\": \"\",\n" +
            "  \"auth_uri\": \"\",\n" +
            "  \"token_uri\": \"\",\n" +
            "  \"auth_provider_x509_cert_url\": \"\",\n" +
            "  \"client_x509_cert_url\": \"\"\n" +
            "}";

    private GCPRegion region;
    private GCPRegion regionWithoutAuthFile;
    private GCPScalingService service = new GCPScalingService(null, null, null, null, null, null, null);

    @Before
    public void setup() throws IOException {
        String auth = writeAuthFile("test_region_auth", CRED_TEMPLATE);
        region = new GCPRegion();
        region.setAuthFile(auth);
        region.setId(1L);
        region.setRegionCode("region_code");

        regionWithoutAuthFile = new GCPRegion();
        regionWithoutAuthFile.setId(2L);
        regionWithoutAuthFile.setRegionCode("region_code_2");
    }

    private String writeAuthFile(String filename, String content) throws IOException {
        Path testRegionAuth = Files.createTempFile(filename, ".json");
        Files.write(testRegionAuth, Arrays.asList(content.split("\n")));
        return testRegionAuth.toString();
    }

    @Test
    public void credFileExistsInEnvVars() throws IOException {
        Map<String, String> envVars = service.buildContainerCloudEnvVars(region);
        Assert.assertTrue(envVars.containsKey(CLOUD_REGION_PREFIX + region.getId()));
        Assert.assertTrue(envVars.containsKey(CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX + region.getId()));
        String expected = String.join("", Files.readAllLines(Paths.get(region.getAuthFile())));
        String actual = envVars.get(CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX + region.getId());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void noCredFileInEnvVarsIfNoRegionAuthFile() {
        Map<String, String> envVars = service.buildContainerCloudEnvVars(regionWithoutAuthFile);
        if (System.getenv(GCPScalingService.GOOGLE_APPLICATION_CREDENTIALS) == null) {
            Assert.assertFalse(envVars.containsKey(CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX
                    + regionWithoutAuthFile.getId()));
        } else {
            Assert.assertTrue(envVars.containsKey(CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX
                    + regionWithoutAuthFile.getId()));
        }
        Assert.assertTrue(envVars.containsKey(CLOUD_REGION_PREFIX + regionWithoutAuthFile.getId()));
    }

}