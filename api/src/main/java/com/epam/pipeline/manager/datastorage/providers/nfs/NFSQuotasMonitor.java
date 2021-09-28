/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
 
package com.epam.pipeline.manager.datastorage.providers.nfs;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuota;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.search.SearchManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NFSQuotasMonitor {

    private static final int GB_TO_BYTES = 1024 * 1024 * 1024;
    private static final String SIZE_QUOTA_GB = "GB";
    private static final String SIZE_QUOTA_PERCENTS = "PERCENT";
    private static final int PERCENTS_MULTIPLIER = 100;

    private final DataStorageManager dataStorageManager;
    private final SearchManager searchManager;
    private final MetadataManager metadataManager;
    private final FileShareMountManager fileShareMountManager;
    private final LustreFSManager lustreManager;
    private final MessageHelper messageHelper;
    private final String emailNotificationAction;
    private final String readOnlyAction;
    private final String disableMountAction;
    private final String notificationsKey;

    public NFSQuotasMonitor(final DataStorageManager dataStorageManager,
                            final SearchManager searchManager,
                            final MetadataManager metadataManager,
                            final FileShareMountManager fileShareMountManager,
                            final LustreFSManager lustreManager,
                            final MessageHelper messageHelper,
                            final @Value("${data.storage.nfs.quota.action.email:EMAIL}")
                                        String emailNotificationAction,
                            final @Value("${data.storage.nfs.quota.action.readonly:READONLY}")
                                        String readOnlyAction,
                            final @Value("${data.storage.nfs.quota.action.disabled:DISABLE_MOUNT}")
                                        String disableMountAction,
                            final @Value("${data.storage.nfs.quota.metadata.key:fs_notifications}")
                                        String notificationsKey) {
        this.dataStorageManager = dataStorageManager;
        this.searchManager = searchManager;
        this.metadataManager = metadataManager;
        this.fileShareMountManager = fileShareMountManager;
        this.lustreManager = lustreManager;
        this.messageHelper = messageHelper;
        this.emailNotificationAction = emailNotificationAction;
        this.readOnlyAction = readOnlyAction;
        this.disableMountAction = disableMountAction;
        this.notificationsKey = notificationsKey;
    }

    @Scheduled(fixedDelayString = "${data.storage.nfs.quota.poll:60000}")
    public void controlQuotas() {
        log.info("Start NFS quotas processing...");
        final List<NFSDataStorage> nfsDataStorages = loadAllNFS();
        final Map<Long, NFSQuota> activeQuotas = loadStorageQuotas(nfsDataStorages);
        nfsDataStorages.forEach(storage -> {
            final NFSStorageMountStatus statusUpdate = Optional.ofNullable(activeQuotas.get(storage.getId()))
                .map(quota -> processActiveQuota(quota, storage))
                .orElse(NFSStorageMountStatus.ACTIVE);
            dataStorageManager.updateMountStatus(storage, statusUpdate);
        });
        log.info("NFS quotas are processed successfully.");
    }

    private List<NFSDataStorage> loadAllNFS() {
        return dataStorageManager.getDataStorages().stream()
            .filter(storage -> DataStorageType.NFS.equals(storage.getType()))
            .map(NFSDataStorage.class::cast)
            .collect(Collectors.toList());
    }

    private NFSStorageMountStatus processActiveQuota(final NFSQuota quota, final NFSDataStorage storage) {
        return CollectionUtils.emptyIfNull(quota.getNotifications()).stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(NFSQuotaNotificationEntry::getValue).reversed())
            .filter(this::hasRestrictingActions)
            .filter(notification -> excessLimit(storage, notification))
            .map(notification -> mapNotificationToStatus(storage.getId(), notification))
            .findFirst()
            .orElse(NFSStorageMountStatus.ACTIVE);
    }

    private NFSStorageMountStatus mapNotificationToStatus(final Long storageId,
                                                          final NFSQuotaNotificationEntry notification) {
        final Set<String> actions = notification.getActions();
        if (actions.contains(disableMountAction)) {
            return NFSStorageMountStatus.MOUNT_DISABLED;
        } else if (actions.contains(readOnlyAction)) {
            return NFSStorageMountStatus.READ_ONLY;
        } else {
            log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_UNKNOWN_RESTRICTION, actions, storageId));
            return NFSStorageMountStatus.READ_ONLY;
        }
    }

    private boolean hasRestrictingActions(final NFSQuotaNotificationEntry notification) {
        final Set<String> actions = notification.getActions();
        return CollectionUtils.size(actions) > 1
               || (CollectionUtils.size(actions) == 1 && !actions.contains(emailNotificationAction));
    }

    private boolean excessLimit(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification) {
        final Double originalLimit = notification.getValue();
        final StorageUsage storageUsage = searchManager.getStorageUsage(storage, null, true);
        final String notificationType = notification.getType();
        if (SIZE_QUOTA_GB.equalsIgnoreCase(notificationType)) {
            final long limitBytes = (long) (originalLimit * GB_TO_BYTES);
            return storageUsage.getSize() > limitBytes;
        } else if (SIZE_QUOTA_PERCENTS.equals(notificationType)) {
            return excessPercentageLimit(storage, originalLimit, storageUsage);
        } else {
            log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_UNKNOWN_TYPE, notificationType));
            return false;
        }
    }

    private boolean excessPercentageLimit(final NFSDataStorage storage, final Double originalLimit,
                                          final StorageUsage storageUsage) {
        final FileShareMount shareMount = fileShareMountManager.load(storage.getFileShareMountId());
        final MountType shareType = shareMount.getMountType();
        if (MountType.NFS.equals(shareType)) {
            log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_NFS_PERCENTAGE_QUOTA_WARN,
                                              storage.getId()));
        } else if (MountType.LUSTRE.equals(shareType)) {
            return lustreManager.findLustreFS(shareMount)
                .map(LustreFS::getCapacityGb)
                .map(maxSize -> maxSize * originalLimit / PERCENTS_MULTIPLIER * GB_TO_BYTES)
                .map(limit -> storageUsage.getSize() > limit)
                .orElse(false);
        } else {
            log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_PERCENTS_UNKNOWN_SHARE_TYPE, shareType));
        }
        return false;
    }

    private Map<Long, NFSQuota> loadStorageQuotas(final List<NFSDataStorage> storages) {
        return storages.stream()
            .map(BaseEntity::getId)
            .map(storageId -> metadataManager.loadMetadataItem(storageId, AclClass.DATA_STORAGE))
            .filter(Objects::nonNull)
            .filter(metadataEntry -> metadataEntry.getData().containsKey(notificationsKey))
            .collect(Collectors.toMap(metadataEntry -> metadataEntry.getEntity().getEntityId(),
                                      this::mapPreferenceToQuota));
    }

    private NFSQuota mapPreferenceToQuota(final MetadataEntry metadata) {
        final PipeConfValue value = metadata.getData().get(notificationsKey);
        return JsonMapper.parseData(value.getValue(), new TypeReference<NFSQuota>() {});
    }
}
