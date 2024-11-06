/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage;

import com.epam.pipeline.entity.region.CloudProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum DataStorageType {
    S3("S3", StorageServiceType.OBJECT_STORAGE,
            new HashSet<>(Arrays.asList(Constants.STANDARD_STORAGE_CLASS, "GLACIER", "GLACIER_IR", "DEEP_ARCHIVE"))
    ),
    NFS("NFS", StorageServiceType.FILE_SHARE, Collections.singleton(Constants.STANDARD_STORAGE_CLASS)),
    AZ("AZ", StorageServiceType.OBJECT_STORAGE, Collections.singleton(Constants.STANDARD_STORAGE_CLASS)),
    GS("GS", StorageServiceType.OBJECT_STORAGE, Collections.singleton(Constants.STANDARD_STORAGE_CLASS)),
    AWS_OMICS_REF("AWS_OMICS_REF",
            StorageServiceType.AWS_OMICS_REF, Collections.singleton(Constants.STANDARD_STORAGE_CLASS)),
    AWS_OMICS_SEQ("AWS_OMICS_SEQ",
            StorageServiceType.AWS_OMICS_SEQ, Collections.singleton(Constants.STANDARD_STORAGE_CLASS));

    private final String id;
    private final StorageServiceType serviceType;
    private final Set<String> storageClasses;

    private static final Map<String, DataStorageType> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(v -> v.id, v -> v));
    private static final Map<String, DataStorageType> NAMES_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, v -> v));

    public static DataStorageType fromServiceType(final CloudProvider provider,
                                                  final StorageServiceType serviceType) {
        switch (serviceType) {
            case FILE_SHARE: return NFS;
            case OBJECT_STORAGE: return getObjectStorageType(provider);
            case AWS_OMICS_REF: return AWS_OMICS_REF;
            case AWS_OMICS_SEQ: return AWS_OMICS_SEQ;
            default: throw new IllegalArgumentException("Unsupported service " + serviceType);
        }
    }

    public static DataStorageType getById(String id) {
        if (id == null) {
            return null;
        }
        return ID_MAP.get(id.toUpperCase());
    }

    public static DataStorageType getByName(String name) {
        if (name == null) {
            return null;
        }
        return NAMES_MAP.get(name);
    }

    public static Set<String> getIds() {
        return ID_MAP.keySet();
    }

    private static DataStorageType getObjectStorageType(final CloudProvider provider) {
        switch (provider) {
            case AWS: return S3;
            case AZURE: return AZ;
            case GCP: return GS;
            default: throw new IllegalArgumentException("Unsupported provider for object storage: " + provider);
        }
    }

    public static class Constants {
        public static final String STANDARD_STORAGE_CLASS = "STANDARD";
    }
}
