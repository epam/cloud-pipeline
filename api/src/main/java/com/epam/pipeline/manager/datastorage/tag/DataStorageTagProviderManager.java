package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.tag.DataStorageObject;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyRequest;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataStorageTagProviderManager {
    
    private static final int DEFAULT_OPERATIONS_BULK_SIZE = 1000;

    private final StorageProviderManager storageProviderManager;
    private final DataStorageTagManager tagManager;
    private final DataStorageTagBatchManager tagBatchManager;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    public void createFileTags(final AbstractDataStorage storage,
                               final String path,
                               final String version) {
        final String authorizedUser = authManager.getAuthorizedUser();
        final String relativePath = storage.resolveRootPath(path);
        final Map<String, String> defaultTags = Collections.singletonMap(ProviderUtils.OWNER_TAG_KEY, authorizedUser);
        tagManager.insert(storage.getRootId(), new DataStorageObject(relativePath, null), defaultTags);
        if (storage.isVersioningEnabled()) {
            tagManager.insert(storage.getRootId(), new DataStorageObject(relativePath, version), defaultTags);
        }
    }

    public Map<String, String> loadFileTags(final AbstractDataStorage storage,
                                            final String path,
                                            final String version) {
        final String relativePath = storage.resolveRootPath(path);
        final DataStorageObject object = new DataStorageObject(relativePath, version);
        return mapFrom(tagManager.load(storage.getRootId(), object));
    }

    public Map<String, String> updateFileTags(final AbstractDataStorage storage,
                                              final String path,
                                              final String version,
                                              final Map<String, String> tagsToAdd,
                                              final Boolean rewrite) {
        final String relativePath = storage.resolveRootPath(path);
        final Function<DataStorageObject, List<DataStorageTag>> updateTags = rewrite
                ? object -> tagManager.insert(storage.getRootId(), object, tagsToAdd)
                : object -> tagManager.upsert(storage.getRootId(), object, tagsToAdd);
        if (storage.isVersioningEnabled()) {
            storageProviderManager.findFile(storage, path)
                    .map(DataStorageFile::getVersion)
                    .map(latestVersion ->
                            StringUtils.isBlank(version)
                                    ? new DataStorageObject(relativePath, latestVersion)
                                    : latestVersion.equals(version)
                                    ? new DataStorageObject(relativePath)
                                    : null)
                    .ifPresent(updateTags::apply);
        }
        return mapFrom(updateTags.apply(new DataStorageObject(relativePath, version)));
    }

    public void moveFileTags(final AbstractDataStorage storage,
                             final String oldPath,
                             final String newPath,
                             final String newVersion) {
        final String oldRelativePath = storage.resolveRootPath(oldPath);
        final String newRelativePath = storage.resolveRootPath(newPath);
        final Map<String, String> tagMap = mapFrom(tagManager.load(storage.getRootId(),
                new DataStorageObject(oldRelativePath)));
        tagManager.upsert(storage.getRootId(), new DataStorageObject(newRelativePath), tagMap);
        if (storage.isVersioningEnabled()) {
            tagManager.upsert(storage.getRootId(), new DataStorageObject(newRelativePath, newVersion), tagMap);
        } else {
            tagManager.delete(storage.getRootId(), new DataStorageObject(oldRelativePath));
        }
    }

    public void moveFolderTags(final AbstractDataStorage storage,
                               final String oldPath,
                               final String newPath) {
        final String oldRelativePath = storage.resolveRootPath(oldPath);
        final String newRelativePath = storage.resolveRootPath(newPath);
        tagManager.copyFolder(storage.getRootId(), oldRelativePath, newRelativePath);
        if (storage.isVersioningEnabled()) {
            StreamUtils.chunked(storageProviderManager.listFiles(storage, newPath + storage.getDelimiter()),
                    getOperationsBulkSize())
                    .forEach(chunk -> tagBatchManager.copy(storage.getId(), new DataStorageTagCopyBatchRequest(
                            chunk.stream()
                                    .map(file -> new DataStorageTagCopyRequest(
                                            DataStorageTagCopyRequest.object(file.getPath(), null),
                                            DataStorageTagCopyRequest.object(file.getPath(), file.getVersion())))
                                    .collect(Collectors.toList()))));
        } else {
            tagManager.deleteAllInFolder(storage.getRootId(), oldRelativePath);
        }
    }

    public void restoreFileTags(final AbstractDataStorage storage,
                                final String path,
                                final String version) {
        final String relativePath = storage.resolveRootPath(path);
        storageProviderManager.findFile(storage, path)
                .map(DataStorageFile::getVersion)
                .ifPresent(latestVersion -> {
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(relativePath, version),
                            new DataStorageObject(relativePath));
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(relativePath, version),
                            new DataStorageObject(relativePath, latestVersion));
                });
        tagManager.delete(storage.getRootId(), new DataStorageObject(relativePath, version));
    }

    public void deleteFileTags(final AbstractDataStorage storage,
                               final String path,
                               final String version,
                               final Set<String> tags) {
        final String relativePath = storage.resolveRootPath(path);
        final DataStorageObject object = new DataStorageObject(relativePath, version);
        final List<DataStorageTag> existingTags = tagManager.load(storage.getRootId(), object);
        tags.forEach(tag -> Assert.isTrue(existingTags.stream().anyMatch(it -> it.getKey().equals(tag)),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)));
        if (storage.isVersioningEnabled()) {
            storageProviderManager.findFile(storage, path)
                    .map(DataStorageFile::getVersion)
                    .map(latestVersion ->
                            StringUtils.isBlank(version)
                                    ? new DataStorageObject(relativePath, latestVersion)
                                    : latestVersion.equals(version)
                                    ? new DataStorageObject(relativePath)
                                    : null)
                    .ifPresent(obj -> tagManager.delete(storage.getRootId(), obj, tags));
        }
        tagManager.delete(storage.getRootId(), object, tags);
    }

    public void deleteFileTags(final AbstractDataStorage storage,
                               final String path,
                               final String version,
                               final Boolean totally) {
        final String relativePath = storage.resolveRootPath(path);
        if (storage.isVersioningEnabled()) {
            if (version != null) {
                final Optional<String> latestVersion = storageProviderManager.findFile(storage, path)
                        .map(DataStorageFile::getVersion);
                if (latestVersion.isPresent()) {
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(relativePath, latestVersion.get()),
                            new DataStorageObject(relativePath));
                    tagManager.delete(storage.getRootId(), new DataStorageObject(relativePath, version));
                } else {
                    tagManager.delete(storage.getRootId(), new DataStorageObject(relativePath, version));
                    tagManager.delete(storage.getRootId(), new DataStorageObject(relativePath));
                }
            } else if (totally) {
                tagManager.deleteAll(storage.getRootId(), relativePath);
            }
        } else {
            tagManager.deleteAll(storage.getRootId(), relativePath);
        }
    }

    public void deleteFolderTags(final AbstractDataStorage storage,
                                 final String path,
                                 final Boolean totally) {
        if (!storage.isVersioningEnabled() || totally) {
            tagManager.deleteAllInFolder(storage.getRootId(), storage.resolveRootPath(path));
        }
    }

    public void deleteStorageTags(final AbstractDataStorage storage) {
        deleteFolderTags(storage, "", true);
    }

    private Integer getOperationsBulkSize() {
        return Optional.of(SystemPreferences.DATA_STORAGE_OPERATIONS_BULK_SIZE)
                .map(preferenceManager::getPreference)
                .orElse(DEFAULT_OPERATIONS_BULK_SIZE);
    }

    private Map<String, String> mapFrom(final List<DataStorageTag> tags) {
        return tags.stream().collect(Collectors.toMap(DataStorageTag::getKey, DataStorageTag::getValue));
    }
}
