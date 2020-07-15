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
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import org.apache.commons.lang3.tuple.Pair;
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
    private static final String NFS_MOUNT_CMD_PATTERN = "sudo mount -t %s %s %s %s";
    private static final String AZURE_CREDS_FOMAT = "username=%s,password=%s";

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

    static Pair<String, MountCommand> getNFSMountOption(final AbstractCloudRegion cloudRegion,
                                                        final AbstractCloudRegionCredentials credentials,
                                                        final String defaultOptions, final String protocol) {
        if (cloudRegion != null && cloudRegion.getProvider() == CloudProvider.AZURE
                && protocol.equalsIgnoreCase(MountType.SMB.getProtocol())) {
            final AzureRegion azureRegion = (AzureRegion) cloudRegion;
            return buildAzureSmbMountOptions(azureRegion, credentials, defaultOptions);
        }
        final String result = StringUtils.isEmpty(defaultOptions) ? "" : "-o " + defaultOptions;
        return Pair.of(result,
                MountCommand.builder()
                        .credentialsRequired(false)
                        .commandPattern(result)
                        .build());
    }

    static Pair<String, MountCommand> getNFSMountCommand(final AbstractCloudRegion cloudRegion,
                                                         final AbstractCloudRegionCredentials credentials,
                                                         final String defaultOptions, final String protocol,
                                                         final String rootNfsPath, final String mntDir) {
        final Pair<String, MountCommand> mountOptions = getNFSMountOption(cloudRegion, credentials,
                defaultOptions, protocol);

        return Pair.of(formatMountCommand(protocol, rootNfsPath, mntDir, mountOptions.getKey()),
                MountCommand.builder()
                        .credentialsRequired(mountOptions.getValue().isCredentialsRequired())
                        .commandPattern(formatMountCommand(protocol, rootNfsPath, mntDir,
                                mountOptions.getValue().getCommandPattern()))
                        .build());
    }

    static String formatNfsPath(String path, String protocol){
        if (protocol.equalsIgnoreCase(MountType.SMB.getProtocol()) && !path.startsWith(SMB_SCHEME)) {
            path = SMB_SCHEME + path;
        }
        return path;
    }

    private static Pair<String, MountCommand> buildAzureSmbMountOptions(
            final AzureRegion azureRegion, final AbstractCloudRegionCredentials credentials,
            final String defaultOptions) {
        final String account = azureRegion.getStorageAccount();
        final String accountKey = Optional.ofNullable(credentials)
                .map(c -> ((AzureRegionCredentials)c).getStorageAccountKey())
                .orElse(null);
        final boolean credentialsFound = accountKey != null && account != null;

        final String optionsFormat = buildAzureSmbMountOptionsFormat(credentialsFound, defaultOptions);
        final String result = StringUtils.isEmpty(optionsFormat) ? "" : "-o " + optionsFormat;
        return Pair.of(credentialsFound ? String.format(result, account, accountKey) : result,
                MountCommand.builder()
                        .credentialsRequired(credentialsFound)
                        .commandPattern(result)
                        .build());
    }

    private static String buildAzureSmbMountOptionsFormat(final boolean credentialsFound, final String defaultOptions) {
        if (credentialsFound) {
            return defaultOptions != null
                    ? String.format("%s,%s", defaultOptions, AZURE_CREDS_FOMAT)
                    : AZURE_CREDS_FOMAT;
        }
        return defaultOptions;
    }

    private static String formatMountCommand(final String protocol, final String rootNfsPath,
                                             final String mntDir, final String mountOptions) {
        return String.format(NFS_MOUNT_CMD_PATTERN, protocol, mountOptions, rootNfsPath, mntDir);
    }
}
