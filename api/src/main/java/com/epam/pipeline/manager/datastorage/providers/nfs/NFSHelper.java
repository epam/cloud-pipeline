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

import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NFSHelper {

    /**
     * NFS path pattern for matching aws and google paths.
     * This pattern will match the following paths:
     * AWS: {efs-host-name}:/{bucket-name} (f.i. fs-12345678:/bucket1)
     * Google: {gcp-host-name}:/{root-path}/{bucket-name} (f.i. gcfs-12345678:/vol1/bucket1)
    * */
    private static final Pattern NFS_ROOT_PATTERN = Pattern.compile("(.+:.*\\/)[^\\/]*");


    /**
     * NFS path pattern for matching azure paths.
     * This pattern will match the following paths:
     * Azure: {efs-host-name}/{root-path}/{bucket-name} (f.i. azfs-12345678/vol1/bucket1)
     * */
    private static final Pattern NFS_AZURE_ROOT_PATTERN = Pattern.compile("([^\\/]+\\/[^\\/]+\\/)[^\\/]+");

    /**
     * NFS path pattern for matching Lustre paths.
     * This pattern will match the following paths:
     * <MGS NID>[:<MGS NID>]:/<fsname>, where <MGS NID> is <IPv4 address>@<LND protocol><lnd#>
     * f.i.: 192.168.227.11@tcp1:/demo, 192.168.227.11@tcp1:192.168.227.12@tcp1:/demo
     */
    private static final String LUSTRE_MGS_NID_REGEX = "([^:]+(:\\d+)?@\\w+)";
    private static final Pattern NFS_LUSTRE_ROOT_PATTERN =
        Pattern.compile(String.format("^%1$s(:%1$s)*(:\\/)[^\\/]+", LUSTRE_MGS_NID_REGEX));

    /**
     * NFS path pattern for matching aws paths.
     * This pattern will match the following paths:
     * AWS: {efs-host-name}:{bucket-name} (f.i. fs-12345678:bucket1)
     * */
    private static final Pattern NFS_PATTERN_WITH_HOME_DIR = Pattern.compile("(.+:)[^\\/]+");

    private static final String SMB_SCHEME = "//";
    private static final String PATH_SEPARATOR = "/";
    private static final String NFS_HOST_DELIMITER = ":/";

    private NFSHelper() {

    }

    public static boolean isValidLustrePath(final String lustrePath) {
        return NFS_LUSTRE_ROOT_PATTERN.matcher(lustrePath).find();
    }

    static String getNfsRootPath(String path) {
        path = path.endsWith(PATH_SEPARATOR) ? path.substring(0, path.length() - 1) : path;
        final Matcher lustreMatcher = NFS_LUSTRE_ROOT_PATTERN.matcher(path);
        final Matcher matcher = NFS_ROOT_PATTERN.matcher(path);
        final Matcher matcherWithHomeDir = NFS_PATTERN_WITH_HOME_DIR.matcher(path);
        final Matcher azureNfsMatcher = NFS_AZURE_ROOT_PATTERN.matcher(path);
        if (lustreMatcher.find()) {
            return lustreMatcher.group();
        } else if (matcher.find()) {
            return matcher.group(1);
        } else if (matcherWithHomeDir.find()) {
            return matcherWithHomeDir.group(1);
        } else if (azureNfsMatcher.find()) {
            return azureNfsMatcher.group(1);
        } else {
            throw new IllegalArgumentException("Invalid path");
        }
    }

    static String getNFSMountOption(final AbstractCloudRegion cloudRegion,
                                    final AbstractCloudRegionCredentials credentials,
                                    final String defaultOptions, String protocol) {
        String result = defaultOptions;
        if (cloudRegion != null && cloudRegion.getProvider() == CloudProvider.AZURE
                && protocol.equalsIgnoreCase(MountType.SMB.getProtocol())) {
            final AzureRegion azureRegion = (AzureRegion) cloudRegion;
            final String account = azureRegion.getStorageAccount();
            final String accountKey = Optional.ofNullable(credentials)
                    .map(c -> ((AzureRegionCredentials)c).getStorageAccountKey())
                    .orElse(null);
            String[] options = defaultOptions != null
                    ? new String[]{defaultOptions, "username=" + account, "password=" + accountKey}
                    : new String[]{"username=" + account, "password=" + accountKey};
            result = accountKey != null && account != null ? String.join(",", options) : defaultOptions;
        }
        return StringUtils.isEmpty(result) ? "" : "-o " + result;
    }

    static String formatNfsPath(String path, String protocol) {
        if (protocol.equalsIgnoreCase(MountType.SMB.getProtocol()) && !path.startsWith(SMB_SCHEME)) {
            path = SMB_SCHEME + path;
        }
        if (protocol.equalsIgnoreCase(MountType.LUSTRE.getProtocol())
            && !NFS_LUSTRE_ROOT_PATTERN.matcher(path).find()) {
            throw new IllegalArgumentException("Invalid Lustre path format!");
        }
        if (protocol.equalsIgnoreCase(MountType.NFS.getProtocol()) && !path.contains(NFS_HOST_DELIMITER)) {
            path = path + NFS_HOST_DELIMITER;
        }
        return path;
    }

    static void deleteFolderIfEmpty(final File folder) throws IOException {
        final String[] files = folder.list();
        if (ArrayUtils.isEmpty(files)) {
            FileUtils.deleteDirectory(folder);
        }
    }
}
