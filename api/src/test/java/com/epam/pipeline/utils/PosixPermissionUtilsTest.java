/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

@RunWith(Parameterized.class)
public class PosixPermissionUtilsTest {

    private final String inputMask;
    private final List<PosixFilePermission> expectedPermissions;
    private final boolean shouldThrow;

    public PosixPermissionUtilsTest(String inputMask, List<PosixFilePermission> expectedPermissions,
                                    boolean shouldThrow) {
        this.inputMask = inputMask;
        this.expectedPermissions = expectedPermissions;
        this.shouldThrow = shouldThrow;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {
                "0002",
                Arrays.asList(
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                false
            },
            {
                "1002",
                Arrays.asList(
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                false
            },
            {
                "002",
                Arrays.asList(
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                false
            },
            {
                "0022",
                Arrays.asList(
                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                false
            },
            {
                "0722",
                Arrays.asList(
                        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                ),
                false
            },
            {
                "badMask", Collections.emptyList(), true
            }
        });
    }

    @Test
    public void getAllowedPermissionsFromUMask() {
        if (shouldThrow) {
            try {
                PosixPermissionUtils.getAllowedPermissionsFromUMask(inputMask);
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(true);
            }
        } else {
            final Set<PosixFilePermission> result = PosixPermissionUtils.getAllowedPermissionsFromUMask(inputMask);
            Assert.assertTrue(result.containsAll(expectedPermissions));
        }
    }
}
