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

package com.epam.pipeline.assertions.datastorage;

import com.epam.pipeline.assertions.base.AbstractSecuredEntityAssert;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.pipeline.Folder;

import java.util.Objects;

public abstract class AbstractDataStorageAssert<S extends AbstractDataStorageAssert<S, A>,
        A extends AbstractDataStorage> extends AbstractSecuredEntityAssert<S, A> {

    public AbstractDataStorageAssert(A actual, Class<S> selfType) {
        super(actual, selfType);
    }

    public S hasDescription(String description) {
        isNotNull();
        if (!Objects.equals(actual.getDescription(), description)) {
            failWithMessage("Expected data storage description to be <%s> but was <%s>.",
                    description, actual.getDescription());
        }
        return myself;
    }

    public S hasPath(String path) {
        isNotNull();
        if (!Objects.equals(actual.getPath(), path)) {
            failWithMessage("Expected data storage path to be <%s> but was <%s>.", path, actual.getPath());
        }
        return myself;
    }

    public S hasType(DataStorageType type) {
        isNotNull();
        if (!Objects.equals(actual.getType(), type)) {
            failWithMessage("Expected data storage path to be <%s> but was <%s>.", type, actual.getType());
        }
        return myself;
    }

    public S hasParentFolderId(Long parentFolderId) {
        isNotNull();
        if (!Objects.equals(actual.getParentFolderId(), parentFolderId)) {
            failWithMessage("Expected data storage parent id to be <%s> but was <%s>.",
                    parentFolderId, actual.getParentFolderId());
        }
        return myself;
    }

    public S hasParent(Folder parent) {
        isNotNull();
        if (!Objects.equals(actual.getParent(), parent)) {
            failWithMessage("Expected data storage parent to be <%s> but was <%s>.",
                    parent, actual.getParent());
        }
        return myself;
    }

    public S hasPolicy(StoragePolicy policy) {
        isNotNull();
        if (!Objects.equals(actual.getStoragePolicy(), policy)) {
            failWithMessage("Expected data storage policy to be <%s> but was <%s>.",
                    policy, actual.getStoragePolicy());
        }
        return myself;
    }

    public S hasMountPoint(String mountPoint) {
        isNotNull();
        if (!Objects.equals(actual.getMountPoint(), mountPoint)) {
            failWithMessage("Expected data storage mount point to be <%s> but was <%s>.",
                    mountPoint, actual.getMountPoint());
        }
        return myself;
    }

    public S hasMountOptions(String mountOptions) {
        isNotNull();
        if (!Objects.equals(actual.getMountOptions(), mountOptions)) {
            failWithMessage("Expected data storage mount options to be <%s> but was <%s>.",
                    mountOptions, actual.getMountOptions());
        }
        return myself;
    }

    public S isShared(boolean shared) {
        isNotNull();
        if (!Objects.equals(actual.isShared(), shared)) {
            failWithMessage("Expected data storage shared to be <%s> but was <%s>.",
                    shared, actual.isShared());
        }
        return myself;
    }

    public S isPolicySuported(boolean policySupported) {
        isNotNull();
        if (!Objects.equals(actual.isPolicySupported(), policySupported)) {
            failWithMessage("Expected data storage policy support to be <%s> but was <%s>.",
                    policySupported, actual.isPolicySupported());
        }
        return myself;
    }

    public S hasDelimiter(String delimiter) {
        isNotNull();
        if (!Objects.equals(actual.getDelimiter(), delimiter)) {
            failWithMessage("Expected data storage delimiter to be <%s> but was <%s>.",
                    delimiter, actual.getDelimiter());
        }
        return myself;
    }

    public S hasPathMask(String pathMask) {
        isNotNull();
        if (!Objects.equals(actual.getPathMask(), pathMask)) {
            failWithMessage("Expected data storage path mask to be <%s> but was <%s>.",
                    pathMask, actual.getPathMask());
        }
        return myself;
    }

}
