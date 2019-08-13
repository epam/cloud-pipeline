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

package com.epam.pipeline.external.datastorage.entity.datastorage;

import java.util.Date;
import java.util.List;

import com.epam.pipeline.external.datastorage.entity.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/**
 * An abstract entity, that represents a Data Storage, that is used to store and access data from different sources.
 */
@Getter
@Setter
public class DataStorage {

    /**
     * Represents permissions mask for currently authenticated user. Note that
     * some additional postprocessing is required to set the correct mask for
     * an entity.
     */
    private Integer mask;

    /**
     * Username of creator or current owner of an entity
     */
    private String owner;

    /**
     * Flag indicating, whether item is locked from changes or not
     */
    private boolean locked = false;

    /**
     * {@code Long} represents an entity's identifier.
     */
    private Long id;

    /**
     * {@code String} represents an entity's name.
     */
    private String name;

    private Date createdDate;

    private String description;
    private String path;
    private DataStorageType type;
    private Long parentFolderId;
    private StoragePolicy storagePolicy;
    private AclClass aclClass = AclClass.DATA_STORAGE;
    private boolean hasMetadata;
    /**
     * Defines a path to a directory, where this storage should be mounted in a Docker container
     */
    private String mountPoint = "";

    /**
     * Defines mount options for a particular data storage to be used when mounting to a container
     */
    private String mountOptions;

    /**
     * Defines if that data storage can be shared though a proxy service
     */
    private boolean shared;

    private boolean policySupported;
    private String delimiter;
    private String pathMask;

    /* S3 Bucket properties*/
    /**
     * A list of allowed CIDR strings, that define access control
     */
    private List<String> allowedCidrs;

    private Long regionId;

    protected static final StoragePolicy DEFAULT_POLICY = new StoragePolicy();

    public DataStorage() {
    }

    @JsonIgnore
    public boolean isVersioningEnabled() {
        return storagePolicy != null && storagePolicy.isVersioningEnabled();
    }
}
