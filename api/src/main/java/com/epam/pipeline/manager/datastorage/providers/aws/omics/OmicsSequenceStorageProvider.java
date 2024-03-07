/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.omics;

import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionRequest;
import com.epam.pipeline.entity.datastorage.*;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.manager.datastorage.lifecycle.DataStorageLifecycleRestoredListingContainer;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class OmicsSequenceStorageProvider implements StorageProvider<AWSOmicsSequenceDataStorage> {
    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_SEQ;
    }

    @Override
    public String createStorage(AWSOmicsSequenceDataStorage storage) throws DataStorageException {
        return null;
    }

    @Override
    public ActionStatus postCreationProcessing(AWSOmicsSequenceDataStorage storage) {
        return ActionStatus.notSupported();
    }

    @Override
    public void deleteStorage(AWSOmicsSequenceDataStorage dataStorage) throws DataStorageException {

    }

    @Override
    public void applyStoragePolicy(AWSOmicsSequenceDataStorage dataStorage) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public void restoreFileVersion(AWSOmicsSequenceDataStorage dataStorage, String path, String version) throws DataStorageException {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Stream<DataStorageFile> listDataStorageFiles(AWSOmicsSequenceDataStorage dataStorage, String path) {
        if (StringUtils.isNotBlank(path)) {
            throw new IllegalStateException("Not supported");
        }
        return null;
    }

    @Override
    public DataStorageListing getItems(AWSOmicsSequenceDataStorage dataStorage, String path, Boolean showVersion, Integer pageSize, String marker) {
        return null;
    }

    @Override
    public DataStorageListing getItems(AWSOmicsSequenceDataStorage dataStorage, String path, Boolean showVersion, Integer pageSize, String marker, DataStorageLifecycleRestoredListingContainer restoredListing) {
        return null;
    }

    @Override
    public Optional<DataStorageFile> findFile(AWSOmicsSequenceDataStorage dataStorage, String path, String version) {
        return Optional.empty();
    }

    @Override
    public DataStorageDownloadFileUrl generateDownloadURL(AWSOmicsSequenceDataStorage dataStorage, String path, String version, ContentDisposition contentDisposition) {
        return null;
    }

    @Override
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(AWSOmicsSequenceDataStorage dataStorage, String path) {
        return null;
    }

    @Override
    public DataStorageDownloadFileUrl generateUrl(AWSOmicsSequenceDataStorage dataStorage, String path, List<String> permissions, Duration duration) {
        return null;
    }

    @Override
    public DataStorageFile createFile(AWSOmicsSequenceDataStorage dataStorage, String path, byte[] contents) throws DataStorageException {
        return null;
    }

    @Override
    public DataStorageFile createFile(AWSOmicsSequenceDataStorage dataStorage, String path, InputStream dataStream) throws DataStorageException {
        return null;
    }

    @Override
    public DataStorageFolder createFolder(AWSOmicsSequenceDataStorage dataStorage, String path) throws DataStorageException {
        return null;
    }

    @Override
    public void deleteFile(AWSOmicsSequenceDataStorage dataStorage, String path, String version, Boolean totally) throws DataStorageException {

    }

    @Override
    public void deleteFolder(AWSOmicsSequenceDataStorage dataStorage, String path, Boolean totally) throws DataStorageException {

    }

    @Override
    public DataStorageFile moveFile(AWSOmicsSequenceDataStorage dataStorage, String oldPath, String newPath) throws DataStorageException {
        return null;
    }

    @Override
    public DataStorageFolder moveFolder(AWSOmicsSequenceDataStorage dataStorage, String oldPath, String newPath) throws DataStorageException {
        return null;
    }

    @Override
    public DataStorageFile copyFile(AWSOmicsSequenceDataStorage dataStorage, String oldPath, String newPath) {
        return null;
    }

    @Override
    public DataStorageFolder copyFolder(AWSOmicsSequenceDataStorage dataStorage, String oldPath, String newPath) {
        return null;
    }

    @Override
    public boolean checkStorage(AWSOmicsSequenceDataStorage dataStorage) {
        return false;
    }

    @Override
    public Map<String, String> updateObjectTags(AWSOmicsSequenceDataStorage dataStorage, String path, Map<String, String> tags, String version) {
        return null;
    }

    @Override
    public Map<String, String> listObjectTags(AWSOmicsSequenceDataStorage dataStorage, String path, String version) {
        return null;
    }

    @Override
    public Map<String, String> deleteObjectTags(AWSOmicsSequenceDataStorage dataStorage, String path, Set<String> tagsToDelete, String version) {
        return null;
    }

    @Override
    public DataStorageItemContent getFile(AWSOmicsSequenceDataStorage dataStorage, String path, String version, Long maxDownloadSize) {
        return null;
    }

    @Override
    public DataStorageStreamingContent getStream(AWSOmicsSequenceDataStorage dataStorage, String path, String version) {
        return null;
    }

    @Override
    public String buildFullStoragePath(AWSOmicsSequenceDataStorage dataStorage, String name) {
        return null;
    }

    @Override
    public String getDefaultMountOptions(AWSOmicsSequenceDataStorage dataStorage) {
        return null;
    }

    @Override
    public PathDescription getDataSize(AWSOmicsSequenceDataStorage dataStorage, String path, PathDescription pathDescription) {
        return null;
    }

    @Override
    public void verifyStorageLifecyclePolicyRule(StorageLifecycleRule rule) {

    }

    @Override
    public void verifyStorageLifecycleRuleExecution(StorageLifecycleRuleExecution execution) {

    }

    @Override
    public void verifyRestoreActionSupported() {

    }

    @Override
    public String verifyOrDefaultRestoreMode(StorageRestoreActionRequest restoreMode) {
        return null;
    }

    @Override
    public DataStorageItemType getItemType(AWSOmicsSequenceDataStorage dataStorage, String path, String version) {
        return null;
    }
}
