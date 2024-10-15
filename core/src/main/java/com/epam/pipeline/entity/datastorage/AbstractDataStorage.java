/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.ToolFingerprint;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * An abstract entity, that represents a Data Storage, that is used to store and access data from different sources.
 */
@Getter
@Setter
public abstract class AbstractDataStorage extends AbstractSecuredEntity {

    private String description;
    private String path;
    private DataStorageType type;
    private Long parentFolderId;
    private Folder parent;
    private StoragePolicy storagePolicy;
    private AclClass aclClass = AclClass.DATA_STORAGE;
    private boolean hasMetadata;
    private Long fileShareMountId;

    /**
     * Defines a path to a directory, where this storage should be mounted in a Docker container
     */
    private String mountPoint = "";

    /**
     * Defines mount options for a particular data storage to be used when mounting to a container
     */
    private String mountOptions;

    /**
     * Defines if this storage should be mounted with its fileShareMount path or directly with its own path.
     * By default, false, which means to use fileShareMount path.
     */
    private boolean mountExactPath;

    /**
     * Defines if that data storage can be shared though a proxy service
     */
    private boolean shared;

    /**
     * Defines if 'data-leak' rules applied
     */
    private boolean sensitive;

    /**
     * List of the tools with its versions to which datastorage should only be mounted
     * */
    private List<ToolFingerprint> toolsToMount;

    public AbstractDataStorage(final Long id, final String name,
            final String path, final DataStorageType type) {
        this(id, name, path, type, DEFAULT_POLICY, "");
    }

    protected static final StoragePolicy DEFAULT_POLICY = new StoragePolicy();

    public abstract String getDelimiter();

    public abstract String getPathMask();

    public AbstractDataStorage() {
    }

    public AbstractDataStorage(final Long id, final String name,
            final String path, final DataStorageType type, final StoragePolicy policy, String mountPoint) {
        super(id, name);
        this.path = path;
        this.type = type;
        this.storagePolicy = policy == null ? DEFAULT_POLICY : policy;

        if (!StringUtils.isEmpty(mountPoint)) {
            this.mountPoint = mountPoint;
        }
    }

    @Override
    public void clearParent() {
        this.parent = null;
    }

    @JsonIgnore
    public boolean isVersioningEnabled() {
        return storagePolicy != null && storagePolicy.isVersioningEnabled();
    }

    // TODO: remove when UI client is updated
    @Deprecated
    public Integer getShortTermStorageDuration() {
        return storagePolicy == null ? null : storagePolicy.getShortTermStorageDuration();
    }

    // TODO: remove when UI client is updated
    @Deprecated
    public Integer getLongTermStorageDuration() {
        return storagePolicy == null ? null : storagePolicy.getLongTermStorageDuration();
    }

    /**
     * Defines if StoragePolicy is supported for this {@link AbstractDataStorage}
     * @return
     */
    public abstract boolean isPolicySupported();

    public String getRoot() {
        return getPath().split(getDelimiter())[0];
    }
}
