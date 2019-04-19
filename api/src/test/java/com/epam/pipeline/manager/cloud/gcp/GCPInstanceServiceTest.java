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

public class GCPInstanceServiceTest {

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
    private GCPInstanceService service = new GCPInstanceService(null, null, null, null, null, null, null);

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
        Assert.assertTrue(envVars.containsKey("CP_ACCOUNT_REGION_" + region.getId()));
        Assert.assertTrue(envVars.containsKey("CP_CREDENTIALS_FILE_CONTENT_" + region.getId()));
        String expected = String.join("", Files.readAllLines(Paths.get(region.getAuthFile())));
        String actual = envVars.get("CP_CREDENTIALS_FILE_CONTENT_" + region.getId());
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void noCredFileInEnvVarsIfNoRegionAuthFile() {
        Map<String, String> envVars = service.buildContainerCloudEnvVars(regionWithoutAuthFile);
        if (System.getenv(GCPInstanceService.GOOGLE_APPLICATION_CREDENTIALS) == null) {
            Assert.assertFalse(envVars.containsKey("CP_CREDENTIALS_FILE_CONTENT_" + regionWithoutAuthFile.getId()));
        } else {
            Assert.assertTrue(envVars.containsKey("CP_CREDENTIALS_FILE_CONTENT_" + region.getId()));
        }
        Assert.assertTrue(envVars.containsKey("CP_ACCOUNT_REGION_" + regionWithoutAuthFile.getId()));
    }

}