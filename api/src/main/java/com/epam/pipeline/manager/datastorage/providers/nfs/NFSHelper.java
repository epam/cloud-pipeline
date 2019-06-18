/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NFSHelper {

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
     * NFS path pattern for matching aws paths.
     * This pattern will match the following paths:
     * AWS: {efs-host-name}:{bucket-name} (f.i. fs-12345678:bucket1)
     * */
    private static final Pattern NFS_PATTERN_WITH_HOME_DIR = Pattern.compile("(.+:)[^\\/]+");

    private static final String SMB_SCHEME = "//";
    private static final String PATH_SEPARATOR = "/";

    private NFSHelper() {

    }

    static String getNfsRootPath(String path) {
        path = path.endsWith(PATH_SEPARATOR) ? path.substring(0, path.length() - 1) : path;
        Matcher matcher = NFS_ROOT_PATTERN.matcher(path);
        Matcher matcherWithHomeDir = NFS_PATTERN_WITH_HOME_DIR.matcher(path);
        Matcher azureNfsMatcher = NFS_AZURE_ROOT_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        } else if (matcherWithHomeDir.find()) {
            return matcherWithHomeDir.group(1);
        }else if (azureNfsMatcher.find()) {
            return azureNfsMatcher.group(1);
        } else {
            throw new IllegalArgumentException("Invalid path");
        }
    }

    static String getNFSMountOption(final AbstractCloudRegion cloudRegion,
                                    final AbstractCloudRegionCredentials credentials,
                                    final String defaultOptions) {
        if (cloudRegion != null && cloudRegion.getProvider() == CloudProvider.AZURE) {
            final AzureRegion azureRegion = (AzureRegion) cloudRegion;
            final String account = azureRegion.getStorageAccount();
            final String accountKey = Optional.ofNullable(credentials)
                    .map(c -> ((AzureRegionCredentials)c).getStorageAccountKey())
                    .orElse(null);
            String[] options = defaultOptions != null
                    ? new String[]{defaultOptions, "username=" + account, "password=" + accountKey}
                    : new String[]{"username=" + account, "password=" + accountKey};
            return accountKey != null && account != null ? String.join(",", options) : defaultOptions;
        }
        return StringUtils.isEmpty(defaultOptions) ? "" : "-o " + defaultOptions;
    }

    static String formatNfsPath(String path, String protocol){
        if (protocol.equalsIgnoreCase(MountType.SMB.getProtocol()) && !path.startsWith(SMB_SCHEME)) {
            path = SMB_SCHEME + path;
        }
        return path;
    }
}
