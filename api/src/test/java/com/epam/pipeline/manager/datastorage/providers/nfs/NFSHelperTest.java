/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.nfs;

import com.epam.pipeline.entity.datastorage.MountCommand;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Enclosed.class)
public class NFSHelperTest {

    public static class NonParametrizedTests {
        private static final String TEST_PATH = "localhost";
        private static final String TEST_LUSTRE_PATH = "localhost@tcp:/lustre";
        private static final String EMPTY_STRING = "";
        private static final String RESOURCE_GROUP = "rg";
        private static final String TEST_STORAGE_ACC = "account";
        private static final String TEST_STORAGE_KEY = "key";
        private static final String TEST_OPTIONS = "options";

        @Test
        public void getNFSMountOption() {
            String protocol = MountType.NFS.getProtocol();
            Pair<String, MountCommand> result = NFSHelper
                    .getNFSMountOption(new AwsRegion(), null, EMPTY_STRING, protocol);
            Assert.assertEquals(EMPTY_STRING, result.getKey());
            Assert.assertEquals(EMPTY_STRING, result.getValue().getCommandPattern());
            Assert.assertFalse(result.getValue().isCredentialsRequired());

            protocol = MountType.SMB.getProtocol();
            AzureRegion azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, TEST_STORAGE_ACC);
            AzureRegionCredentials credentials = ObjectCreatorUtils.getAzureCredentials(TEST_STORAGE_KEY);
            result = NFSHelper.getNFSMountOption(azureRegion, credentials, EMPTY_STRING, protocol);
            Assert.assertEquals("-o ,username=account,password=key", result.getKey());
            Assert.assertEquals("-o ,username=%s,password=%s", result.getValue().getCommandPattern());
            Assert.assertTrue(result.getValue().isCredentialsRequired());

            result = NFSHelper.getNFSMountOption(azureRegion, credentials, TEST_OPTIONS, protocol);
            Assert.assertEquals("-o options,username=account,password=key", result.getKey());
            Assert.assertEquals("-o options,username=%s,password=%s", result.getValue().getCommandPattern());
            Assert.assertTrue(result.getValue().isCredentialsRequired());

            azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, null);
            result = NFSHelper.getNFSMountOption(azureRegion, null, EMPTY_STRING, protocol);
            Assert.assertEquals(EMPTY_STRING, result.getKey());
            Assert.assertEquals(EMPTY_STRING, result.getValue().getCommandPattern());
            Assert.assertFalse(result.getValue().isCredentialsRequired());
        }

        @Test
        public void formatNfsPath() {
            final String rightPath = "//samba.share/path";
            String result = NFSHelper.formatNfsPath(rightPath, "cifs");
            Assert.assertEquals(rightPath, result);

            final String unformattedPath = "samba.share/path";
            result = NFSHelper.formatNfsPath(unformattedPath, "cifs");
            //smb protocol -> should format with //
            Assert.assertEquals("//" + unformattedPath, result);

            //lustre protocol -> remove path separator from the end
            result = NFSHelper.formatNfsPath(TEST_LUSTRE_PATH+ "/", "lustre");
            Assert.assertEquals(TEST_LUSTRE_PATH, result);

            //nfs protocol -> should add suffix
            result = NFSHelper.formatNfsPath(TEST_PATH, "nfs");
            Assert.assertEquals(TEST_PATH + ":/", result);
        }

        @Test
        public void getNfsRootPathTest() {
            String nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "directory");
            Assert.assertEquals(TEST_PATH + ":", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/directory");
            Assert.assertEquals(TEST_PATH + ":/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/mnt/");
            Assert.assertEquals(TEST_PATH + ":/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "mnt/");
            Assert.assertEquals(TEST_PATH + ":", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/mnt/directory");
            Assert.assertEquals(TEST_PATH + ":/mnt/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH  + "/mnt/directory");
            Assert.assertEquals(TEST_PATH + "/mnt/", nfsRootPath);
            String lustreRootPath = NFSHelper.getNfsRootPath("host@tcp:/lustre/directory");
            Assert.assertEquals("host@tcp:/lustre", lustreRootPath);
        }

        @Test(expected = IllegalArgumentException.class)
        public void getNfsRootPathShouldFailIfPathInvalid() {
            NFSHelper.getNfsRootPath(TEST_PATH + ":");
        }

        @Test
        public void shouldBuildCommandWithCredentialsForAzureSmb() {
            final String protocol = MountType.SMB.getProtocol();
            final AzureRegion azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, TEST_STORAGE_ACC);
            final AzureRegionCredentials credentials = ObjectCreatorUtils.getAzureCredentials(TEST_STORAGE_KEY);

            final Pair<String, MountCommand> result = NFSHelper.getNFSMountCommand(azureRegion, credentials,
                    TEST_OPTIONS, protocol, "/root", "/mnt");

            Assert.assertEquals("sudo mount -t cifs -o options,username=account,password=key /root /mnt",
                    result.getKey());
            Assert.assertEquals("sudo mount -t cifs -o options,username=%s,password=%s /root /mnt",
                    result.getValue().getCommandPattern());
            Assert.assertTrue(result.getValue().isCredentialsRequired());
        }
    }

    @RunWith(Parameterized.class)
    public static class InvalidLustrePathTests {

        private final String caseName;
        private final boolean isValid;
        private final String lustrePath;

        public InvalidLustrePathTests(final String caseName, final boolean isValid, final String path) {
            this.caseName = caseName;
            this.isValid = isValid;
            this.lustrePath = path;
        }

        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                {
                    "empty lnd, host delimiter, filesystem name",
                    false,
                    "host"
                },
                {
                    "empty lnd, filesystem name",
                    false,
                    "host:/"
                },
                {
                    "empty lnd",
                    false,
                    "host:/lustre"
                },
                {
                    "invalid delimiter",
                    false,
                    "host@tcp/lustre"
                },
                {
                    "empty filesystem name",
                    false,
                    "host@tcp:/"
                },
                {
                    "valid path",
                    true,
                    "host@tcp:/lustre"
                },
                {
                    "valid multi-mgs path",
                    true,
                    "host1@tcp:host2@tcp:/lustre"
                },
                {
                    "valid path with port specification",
                    true,
                    "host:1234@tcp:/lustre"
                },
                {
                    "valid multi-nid path with port specification for one",
                    true,
                    "host1@tcp:host2:1234@tcp:/lustre"
                },
                {
                    "valid multi-nid path with port specification for one",
                    true,
                    "host1:1234@tcp:host2:1234@tcp:/lustre"
                }
            });
        }

        @Test
        public void shouldThrowException() {
            final boolean lustrePathValidationResult = NFSHelper.isValidLustrePath(lustrePath);
            Assert.assertEquals(isValid, lustrePathValidationResult);
        }
    }
}
