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

import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(Enclosed.class)
public class NFSHelperTest {

    public static class NonParametrizedTests {
        private static final String TEST_PATH = "localhost";
        private static final String TEST_LUSTRE_PATH = "localhost@tcp:/lustre";
        private static final String EMPTY_STRING = "";
        private static final String RESOURCE_GROUP = "rg";

        @Test
        public void getNFSMountOption() {
            String protocol = MountType.NFS.getProtocol();
            String result = NFSHelper.getNFSMountOption(new AwsRegion(), null, EMPTY_STRING, protocol);
            Assert.assertEquals(EMPTY_STRING, result);

            protocol = MountType.SMB.getProtocol();
            AzureRegion azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, "account");
            AzureRegionCredentials credentials = ObjectCreatorUtils.getAzureCredentials("key");
            result = NFSHelper.getNFSMountOption(azureRegion, credentials, EMPTY_STRING, protocol);
            Assert.assertEquals("-o ,username=account,password=key", result);

            result = NFSHelper.getNFSMountOption(azureRegion, credentials, "options", protocol);
            Assert.assertEquals("-o options,username=account,password=key", result);

            azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, null);
            result = NFSHelper.getNFSMountOption(azureRegion, null, EMPTY_STRING, protocol);
            Assert.assertEquals(EMPTY_STRING, result);

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

    @RunWith(Parameterized.class)
    @RequiredArgsConstructor
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static class DetermineHostsTests {
        private static final String TEST_IP_1 = "1.1.1.1";
        private static final String TEST_IP_2 = "1.1.1.2";

        private final String mountRoot;
        private final MountType mountType;
        private final List<String> resultHosts;

        @Parameterized.Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                {
                    "fs-12345678:/bucket1",
                    MountType.NFS,
                    Collections.singletonList("fs-12345678")
                },
                {
                    "gcfs-12345678:/vol1/bucket1",
                    MountType.NFS,
                    Collections.singletonList("gcfs-12345678")
                },
                {
                    "azfs-12345678/vol1/bucket1",
                    MountType.NFS,
                    Collections.singletonList("azfs-12345678")
                },
                {
                    "fs-12345678:bucket1",
                    MountType.NFS,
                    Collections.singletonList("fs-12345678")
                },
                {
                    "1.1.1.1@tcp1:/demo",
                    MountType.LUSTRE,
                    Collections.singletonList(TEST_IP_1)
                },
                {
                    "1.1.1.1@tcp1:1.1.1.2@tcp1:/demo",
                    MountType.LUSTRE,
                    Arrays.asList(TEST_IP_1, TEST_IP_2)
                },
                {
                    "lustrefs-1@tcp1:lustrefs-2@tcp1:/demo",
                    MountType.LUSTRE,
                    Arrays.asList("lustrefs-1", "lustrefs-2")
                },
                {
                    TEST_IP_1,
                    MountType.NFS,
                    Collections.singletonList(TEST_IP_1)
                },
                {
                    "//smb-fs/vol1",
                    MountType.SMB,
                    Collections.singletonList("smb-fs")
                }
            });
        }

        @Test
        public void shouldDetermineHosts() {
            final FileShareMount fileShareMount = new FileShareMount();
            fileShareMount.setMountRoot(mountRoot);
            fileShareMount.setMountType(mountType);

            final List<String> hosts = NFSHelper.determineHosts(fileShareMount);
            Assertions.assertThat(hosts).hasSize(resultHosts.size());
            Assertions.assertThat(hosts).containsOnlyElementsOf(resultHosts);
        }
    }
}
