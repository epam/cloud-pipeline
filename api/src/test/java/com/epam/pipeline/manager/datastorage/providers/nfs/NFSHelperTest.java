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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NFSHelperTest {

    @Nested
    public class NonParametrizedTests {
        private static final String TEST_PATH = "localhost";
        private static final String TEST_LUSTRE_PATH = "localhost@tcp:/lustre";
        private static final String EMPTY_STRING = "";
        private static final String RESOURCE_GROUP = "rg";

        @Test
        public void getNFSMountOption() {
            String protocol = MountType.NFS.getProtocol();
            String result = NFSHelper.getNFSMountOption(new AwsRegion(), null, EMPTY_STRING, protocol);
            assertEquals(EMPTY_STRING, result);

            protocol = MountType.SMB.getProtocol();
            AzureRegion azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, "account");
            AzureRegionCredentials credentials = ObjectCreatorUtils.getAzureCredentials("key");
            result = NFSHelper.getNFSMountOption(azureRegion, credentials, EMPTY_STRING, protocol);
            assertEquals("-o ,username=account,password=key", result);

            result = NFSHelper.getNFSMountOption(azureRegion, credentials, "options", protocol);
            assertEquals("-o options,username=account,password=key", result);

            azureRegion = ObjectCreatorUtils.getDefaultAzureRegion(RESOURCE_GROUP, null);
            result = NFSHelper.getNFSMountOption(azureRegion, null, EMPTY_STRING, protocol);
            assertEquals(EMPTY_STRING, result);

        }

        @Test
        public void formatNfsPath() {
            final String rightPath = "//samba.share/path";
            String result = NFSHelper.formatNfsPath(rightPath, "cifs");
            assertEquals(rightPath, result);

            final String unformattedPath = "samba.share/path";
            result = NFSHelper.formatNfsPath(unformattedPath, "cifs");
            //smb protocol -> should format with //
            assertEquals("//" + unformattedPath, result);

            //lustre protocol -> remove path separator from the end
            result = NFSHelper.formatNfsPath(TEST_LUSTRE_PATH+ "/", "lustre");
            assertEquals(TEST_LUSTRE_PATH, result);

            //nfs protocol -> should add suffix
            result = NFSHelper.formatNfsPath(TEST_PATH, "nfs");
            assertEquals(TEST_PATH + ":/", result);
        }

        @Test
        public void getNfsRootPathTest() {
            String nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "directory");
            assertEquals(TEST_PATH + ":", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/directory");
            assertEquals(TEST_PATH + ":/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/mnt/");
            assertEquals(TEST_PATH + ":/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "mnt/");
            assertEquals(TEST_PATH + ":", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH + ":" + "/mnt/directory");
            assertEquals(TEST_PATH + ":/mnt/", nfsRootPath);
            nfsRootPath = NFSHelper.getNfsRootPath(TEST_PATH  + "/mnt/directory");
            assertEquals(TEST_PATH + "/mnt/", nfsRootPath);
            String lustreRootPath = NFSHelper.getNfsRootPath("host@tcp:/lustre/directory");
            assertEquals("host@tcp:/lustre", lustreRootPath);
        }

        @Test
        public void getNfsRootPathShouldFailIfPathInvalid() {
            assertThrows(IllegalArgumentException.class, () -> NFSHelper.getNfsRootPath(TEST_PATH + ":"));
        }
    }

    @Nested
    class InvalidLustrePathTests {
        @ParameterizedTest(name = "{0}")
        @MethodSource("generateData")
        void shouldValidateLustrePath(String caseName, boolean isValid, String lustrePath) {
            final boolean lustrePathValidationResult = NFSHelper.isValidLustrePath(lustrePath);
            assertEquals(isValid, lustrePathValidationResult);
        }

        private static Stream<Arguments> generateData() {
            return Stream.of(
                Arguments.of("empty lnd, host delimiter, filesystem name", false, "host"),
                Arguments.of("empty lnd, filesystem name", false, "host:/"),
                Arguments.of("empty lnd", false, "host:/lustre"),
                Arguments.of("invalid delimiter", false, "host@tcp/lustre"),
                Arguments.of("empty filesystem name", false, "host@tcp:/"),
                Arguments.of("valid path", true, "host@tcp:/lustre"),
                Arguments.of("valid multi-mgs path", true, "host1@tcp:host2@tcp:/lustre"),
                Arguments.of("valid path with port specification", true, "host:1234@tcp:/lustre"),
                Arguments.of("valid multi-nid path with port specification for one",
                    true, "host1@tcp:host2:1234@tcp:/lustre"),
                Arguments.of("valid multi-nid path with port specification for one",
                    true, "host1:1234@tcp:host2:1234@tcp:/lustre"));
        }
    }

    @Nested
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @DisplayName("Determine Hosts Tests")
    class DetermineHostsTests {
        private static final String TEST_IP_1 = "1.1.1.1";
        private static final String TEST_IP_2 = "1.1.1.2";

        @ParameterizedTest
        @MethodSource("generateData")
        void shouldDetermineHosts(String mountRoot, MountType mountType, List<String> resultHosts) {
            final FileShareMount fileShareMount = new FileShareMount();
            fileShareMount.setMountRoot(mountRoot);
            fileShareMount.setMountType(mountType);

            final List<String> hosts = NFSHelper.determineHosts(fileShareMount);
            Assertions.assertThat(hosts).hasSize(resultHosts.size());
            Assertions.assertThat(hosts).containsOnlyElementsOf(resultHosts);
        }

        private static Stream<Arguments> generateData() {
            return Stream.of(
                Arguments.of("fs-12345678:/bucket1", MountType.NFS, Collections.singletonList("fs-12345678")),
                Arguments.of("gcfs-12345678:/vol1/bucket1", MountType.NFS, Collections.singletonList("gcfs-12345678")),
                Arguments.of("azfs-12345678/vol1/bucket1", MountType.NFS, Collections.singletonList("azfs-12345678")),
                Arguments.of("fs-12345678:bucket1", MountType.NFS, Collections.singletonList("fs-12345678")),
                Arguments.of("1.1.1.1@tcp1:/demo", MountType.LUSTRE, Collections.singletonList(TEST_IP_1)),
                Arguments.of("1.1.1.1@tcp1:1.1.1.2@tcp1:/demo", MountType.LUSTRE, Arrays.asList(TEST_IP_1, TEST_IP_2)),
                Arguments.of("lustrefs-1@tcp1:lustrefs-2@tcp1:/demo", MountType.LUSTRE,
                    Arrays.asList("lustrefs-1", "lustrefs-2")),
                Arguments.of(TEST_IP_1, MountType.NFS, Collections.singletonList(TEST_IP_1)),
                Arguments.of("//smb-fs/vol1", MountType.SMB, Collections.singletonList("smb-fs"))
            );
        }
    }
}
