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

import org.springframework.data.util.Pair;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PosixPermissionUtils {

    public static final List<Pair<Integer, PosixFilePermission>> OWNER_PERMISSIONS = Arrays.asList(
            Pair.of(0b1, PosixFilePermission.OWNER_READ),
            Pair.of(0b10, PosixFilePermission.OWNER_WRITE),
            Pair.of(0b100, PosixFilePermission.OWNER_EXECUTE)
    );
    public static final List<Pair<Integer, PosixFilePermission>> GROUP_PERMISSIONS = Arrays.asList(
            Pair.of(0b1, PosixFilePermission.GROUP_READ),
            Pair.of(0b10, PosixFilePermission.GROUP_WRITE),
            Pair.of(0b100, PosixFilePermission.GROUP_EXECUTE)
    );
    public static final List<Pair<Integer, PosixFilePermission>> OTHER_PERMISSIONS = Arrays.asList(
            Pair.of(0b1, PosixFilePermission.OTHERS_READ),
            Pair.of(0b10, PosixFilePermission.OTHERS_WRITE),
            Pair.of(0b100, PosixFilePermission.OTHERS_EXECUTE)
    );
    private static final List<List<Pair<Integer, PosixFilePermission>>> ALL_PERMISSIONS = Arrays.asList(
            OWNER_PERMISSIONS,
            GROUP_PERMISSIONS,
            OTHER_PERMISSIONS
    );

    private PosixPermissionUtils() {
    }

    public static Set<PosixFilePermission> getAllowedPermissionsFromUMask(final String fileShareUMask) {
        final Set<PosixFilePermission> allowedPermissions = new LinkedHashSet<>();
        final String umask = getValidatedUMask(fileShareUMask);
        for (int i = 0; i < umask.length(); i++) {
            int mask = Integer.parseInt(umask.substring(i, i + 1));
            for (Pair<Integer, PosixFilePermission> permission : ALL_PERMISSIONS.get(i)) {
                // if umask matches with permissions -> permission is prohibited
                // e.g. for mask 0002 write permission for OTHERS should be prohibited
                if ((mask & permission.getFirst()) == 0) {
                    allowedPermissions.add(permission.getSecond());
                }
            }
        }
        return allowedPermissions;
    }

    private static String getValidatedUMask(final String fileShareUMask) {
        if (!fileShareUMask.matches("\\d?\\d\\d\\d")) {
            throw new IllegalArgumentException(
                    String.format("Wrong umask pattern: %s, should be: \\d\\d\\d\\d or \\d\\d\\d", fileShareUMask));
        }
        if (fileShareUMask.length() == 4) {
            return fileShareUMask.substring(1);
        }
        return fileShareUMask;
    }

}
