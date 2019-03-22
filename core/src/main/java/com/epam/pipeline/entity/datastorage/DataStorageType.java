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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum DataStorageType {
    S3("S3"),
    NFS("NFS"),
    AZ("AZ");

    private String id;
    private static Map<String, DataStorageType> idMap;
    private static Map<String, DataStorageType> namesMap;

    static {
        idMap = Arrays.stream(values()).collect(Collectors.toMap(v -> v.id, v -> v));
        namesMap = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, v -> v));
    }

    DataStorageType(String id) {
        this.id = id;
    }

    public static DataStorageType fromServiceType(final CloudProvider provider,
                                                  final StorageServiceType serviceType) {
        switch (serviceType) {
            case FILE_SHARE: return NFS;
            case OBJECT_STORAGE: return getObjectStorageType(provider);
            default: throw new IllegalArgumentException("Unsupported service " + serviceType);
        }
    }

    public static DataStorageType getById(String id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id.toUpperCase());
    }

    public static DataStorageType getByName(String name) {
        if (name == null) {
            return null;
        }
        return namesMap.get(name);
    }

    public String getId() {
        return this.id;
    }

    private static DataStorageType getObjectStorageType(final CloudProvider provider) {
        switch (provider) {
            case AWS: return S3;
            case AZURE: return AZ;
            default: throw new IllegalArgumentException("Unsupported provider for object storage: " + provider);
        }
    }


}
