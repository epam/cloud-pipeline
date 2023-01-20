package com.epam.pipeline.manager.datastorage.tag;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageType;
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
import org.apache.commons.collections4.CollectionUtils;
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
        final String absolutePath = storage.resolveAbsolutePath(path);
        final Map<String, String> defaultTags = Collections.singletonMap(ProviderUtils.OWNER_TAG_KEY, authorizedUser);
        // Upsert instead of insert here to safe previous tags if any,
        // in case we don't create a new file but update it with new content
        tagManager.upsert(storage.getRootId(), new DataStorageObject(absolutePath, null), defaultTags);
        if (storage.isVersioningEnabled()) {
            tagManager.upsert(storage.getRootId(), new DataStorageObject(absolutePath, version), defaultTags);
        }
    }

    public Map<String, String> loadFileTags(final AbstractDataStorage storage,
                                            final String path,
                                            final String version) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        final DataStorageObject object = new DataStorageObject(absolutePath, version);
        return mapFrom(tagManager.load(storage.getRootId(), object));
    }

    public Map<Long, List<DataStorageTag>> search(final List<AbstractDataStorage> storages,
                                                  final Map<String, String> tags) {
        Assert.notNull(tags, "Please specify at least a tag key to search by!");
        Assert.state(!tags.isEmpty(), "Please specify at least a tag key to search by!");
        final List<Long> dataStorageRootIds = CollectionUtils.emptyIfNull(storages).stream()
                .map(AbstractDataStorage::getRootId).collect(Collectors.toList());
        return tagManager.search(dataStorageRootIds, tags);
    }

    public Map<String, String> updateFileTags(final AbstractDataStorage storage,
                                              final String path,
                                              final String version,
                                              final Map<String, String> tagsToAdd,
                                              final Boolean rewrite) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        final Function<DataStorageObject, List<DataStorageTag>> updateTags = rewrite
                ? object -> tagManager.insert(storage.getRootId(), object, tagsToAdd)
                : object -> tagManager.upsert(storage.getRootId(), object, tagsToAdd);
        if (storage.isVersioningEnabled()) {
            storageProviderManager.findFile(storage, path)
                    .map(DataStorageFile::getVersion)
                    .map(latestVersion ->
                            StringUtils.isBlank(version)
                                    ? new DataStorageObject(absolutePath, latestVersion)
                                    : latestVersion.equals(version)
                                    ? new DataStorageObject(absolutePath)
                                    : null)
                    .ifPresent(updateTags::apply);
        }
        return mapFrom(updateTags.apply(new DataStorageObject(absolutePath, version)));
    }

    public void moveFileTags(final AbstractDataStorage storage,
                             final String oldPath,
                             final String newPath,
                             final String newVersion) {
        copyFileTags(storage, oldPath, newPath, newVersion);
        if (!storage.isVersioningEnabled()) {
            final String oldAbsolutePath = storage.resolveAbsolutePath(oldPath);
            tagManager.delete(storage.getRootId(), new DataStorageObject(oldAbsolutePath));
        }
    }

    public void moveFolderTags(final AbstractDataStorage storage,
                               final String oldPath,
                               final String newPath) {
        copyFolderTags(storage, oldPath, newPath);
        if (!storage.isVersioningEnabled()) {
            final String oldAbsolutePath = storage.resolveAbsolutePath(oldPath);
            tagManager.deleteAllInFolder(storage.getRootId(), oldAbsolutePath);
        }
    }

    public void copyFileTags(final AbstractDataStorage storage,
                             final String oldPath,
                             final String newPath,
                             final String newVersion) {
        final String oldAbsolutePath = storage.resolveAbsolutePath(oldPath);
        final String newAbsolutePath = storage.resolveAbsolutePath(newPath);
        final Map<String, String> tagMap = mapFrom(tagManager.load(storage.getRootId(),
                new DataStorageObject(oldAbsolutePath)));
        tagManager.upsert(storage.getRootId(), new DataStorageObject(newAbsolutePath), tagMap);
        if (storage.isVersioningEnabled()) {
            tagManager.upsert(storage.getRootId(), new DataStorageObject(newAbsolutePath, newVersion), tagMap);
        }
    }

    public void copyFolderTags(final AbstractDataStorage storage,
                               final String oldPath,
                               final String newPath) {
        final String oldAbsolutePath = storage.resolveAbsolutePath(oldPath);
        final String newAbsolutePath = storage.resolveAbsolutePath(newPath);
        tagManager.copyFolder(storage.getRootId(), oldAbsolutePath, newAbsolutePath);
        if (storage.isVersioningEnabled()) {
            StreamUtils.chunked(storageProviderManager.listFiles(storage, newPath + storage.getDelimiter()),
                    getOperationsBulkSize())
                    .forEach(chunk -> tagBatchManager.copy(storage.getId(), new DataStorageTagCopyBatchRequest(
                            chunk.stream()
                                    .map(file -> new DataStorageTagCopyRequest(
                                            DataStorageTagCopyRequest.object(file.getPath(), null),
                                            DataStorageTagCopyRequest.object(file.getPath(), file.getVersion())))
                                    .collect(Collectors.toList()))));
        }
    }

    public void restoreFileTags(final AbstractDataStorage storage,
                                final String path,
                                final String version) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        storageProviderManager.findFile(storage, path)
                .map(DataStorageFile::getVersion)
                .ifPresent(latestVersion -> {
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(absolutePath, version),
                            new DataStorageObject(absolutePath));
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(absolutePath, version),
                            new DataStorageObject(absolutePath, latestVersion));
                });
        tagManager.delete(storage.getRootId(), new DataStorageObject(absolutePath, version));
    }

    public void deleteFileTags(final AbstractDataStorage storage,
                               final String path,
                               final String version,
                               final Set<String> tags) {
        final String absolutePath = storage.resolveAbsolutePath(path);
        final DataStorageObject object = new DataStorageObject(absolutePath, version);
        final List<DataStorageTag> existingTags = tagManager.load(storage.getRootId(), object);
        tags.forEach(tag -> Assert.isTrue(existingTags.stream().anyMatch(it -> it.getKey().equals(tag)),
                messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST, tag)));
        if (storage.isVersioningEnabled()) {
            storageProviderManager.findFile(storage, path)
                    .map(DataStorageFile::getVersion)
                    .map(latestVersion ->
                            StringUtils.isBlank(version)
                                    ? new DataStorageObject(absolutePath, latestVersion)
                                    : latestVersion.equals(version)
                                    ? new DataStorageObject(absolutePath)
                                    : null)
                    .ifPresent(obj -> tagManager.delete(storage.getRootId(), obj, tags));
        }
        tagManager.delete(storage.getRootId(), object, tags);
    }

    public void deleteFileTags(final AbstractDataStorage storage,
                               final String path,
                               final String version,
                               final Boolean totally) {
        if (storage.getType() == DataStorageType.GS && ProviderUtils.isSyntheticDeletionMarker(version)) {
            restoreFileTags(storage, path, ProviderUtils.getVersionFromSyntheticDeletionMarker(version));
            return;
        }
        final String absolutePath = storage.resolveAbsolutePath(path);
        if (storage.isVersioningEnabled()) {
            if (version != null) {
                final Optional<String> latestVersion = storageProviderManager.findFile(storage, path)
                        .map(DataStorageFile::getVersion);
                if (latestVersion.isPresent()) {
                    tagManager.copy(storage.getRootId(),
                            new DataStorageObject(absolutePath, latestVersion.get()),
                            new DataStorageObject(absolutePath));
                    tagManager.delete(storage.getRootId(), new DataStorageObject(absolutePath, version));
                } else {
                    tagManager.delete(storage.getRootId(), new DataStorageObject(absolutePath, version));
                    tagManager.delete(storage.getRootId(), new DataStorageObject(absolutePath));
                }
            } else if (totally) {
                tagManager.deleteAll(storage.getRootId(), absolutePath);
            }
        } else {
            tagManager.deleteAll(storage.getRootId(), absolutePath);
        }
    }

    public void deleteFolderTags(final AbstractDataStorage storage,
                                 final String path,
                                 final Boolean totally) {
        if (!storage.isVersioningEnabled() || totally) {
            tagManager.deleteAllInFolder(storage.getRootId(), storage.resolveAbsolutePath(path));
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
