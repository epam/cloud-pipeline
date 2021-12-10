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

import static com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry.NO_ACTIVE_QUOTAS_NOTIFICATION;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StorageQuotaAction;
import com.epam.pipeline.entity.datastorage.StorageQuotaType;
import com.epam.pipeline.entity.datastorage.StorageUsage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuota;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaTrigger;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.StorageQuotaTriggersManager;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.search.SearchManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private static final int PERCENTS_MULTIPLIER = 100;

    private final DataStorageManager dataStorageManager;
    private final SearchManager searchManager;
    private final MetadataManager metadataManager;
    private final FileShareMountManager fileShareMountManager;
    private final LustreFSManager lustreManager;
    private final MessageHelper messageHelper;
    private final NotificationManager notificationManager;
    private final String notificationsKey;
    private final NFSStorageMountStatus defaultRestrictiveStatus;
    private final StorageQuotaTriggersManager triggersManager;

    public NFSQuotasMonitor(final DataStorageManager dataStorageManager,
                            final SearchManager searchManager,
                            final MetadataManager metadataManager,
                            final FileShareMountManager fileShareMountManager,
                            final LustreFSManager lustreManager,
                            final MessageHelper messageHelper,
                            final NotificationManager notificationManager,
                            final StorageQuotaTriggersManager triggersManager,
                            final @Value("${data.storage.nfs.quota.metadata.key:fs_notifications}")
                                        String notificationsKey,
                            final @Value("${data.storage.nfs.quota.default.restrictive.status:READ_ONLY}")
                                NFSStorageMountStatus defaultRestrictiveStatus) {
        this.dataStorageManager = dataStorageManager;
        this.searchManager = searchManager;
        this.metadataManager = metadataManager;
        this.fileShareMountManager = fileShareMountManager;
        this.lustreManager = lustreManager;
        this.messageHelper = messageHelper;
        this.notificationManager = notificationManager;
        this.triggersManager = triggersManager;
        this.notificationsKey = notificationsKey;
        this.defaultRestrictiveStatus = defaultRestrictiveStatus;
    }

    @Scheduled(fixedDelayString = "${data.storage.nfs.quota.poll:60000}")
    @SchedulerLock(name = "NFSQuotasMonitor_controlQuotas", lockAtMostForString = "PT10M")
    public void controlQuotas() {
        log.info("Start NFS quotas processing...");
        final List<AbstractDataStorage> activeStorages = dataStorageManager.getDataStorages();
        final Map<Long, NFSQuotaNotificationEntry> notificationTriggers = triggersManager.loadAll().stream()
                .collect(Collectors.toMap(NFSQuotaTrigger::getStorageId, NFSQuotaTrigger::getQuota));
        final List<NFSDataStorage> nfsDataStorages = loadAllNFS(activeStorages);
        final Map<Long, NFSQuota> activeQuotas = loadStorageQuotas(nfsDataStorages);
        nfsDataStorages.forEach(storage -> processStorageQuota(storage, activeQuotas, notificationTriggers));
        log.info("NFS quotas are processed successfully.");
    }

    private void processStorageQuota(final NFSDataStorage storage,
                                     final Map<Long, NFSQuota> activeQuotas,
                                     final Map<Long, NFSQuotaNotificationEntry> notificationTriggers) {
        try {
            final NFSStorageMountStatus statusUpdate = Optional.ofNullable(activeQuotas.get(storage.getId()))
                .map(quota -> processActiveQuota(quota, storage, notificationTriggers))
                .orElse(NFSStorageMountStatus.ACTIVE);
            dataStorageManager.updateMountStatus(storage, statusUpdate);
        } catch (Exception e) {
            log.error("An error occurred during processing quotas for storageId={}: {}",
                      storage.getId(), e.getMessage());
        }
    }

    private List<NFSDataStorage> loadAllNFS(final List<AbstractDataStorage> activeStorages) {
        return activeStorages.stream()
            .filter(storage -> DataStorageType.NFS.equals(storage.getType()))
            .map(NFSDataStorage.class::cast)
            .collect(Collectors.toList());
    }

    private NFSStorageMountStatus processActiveQuota(final NFSQuota quota, final NFSDataStorage storage,
                                                     final Map<Long, NFSQuotaNotificationEntry> notificationTriggers) {
        final List<NFSQuotaNotificationEntry> quotaNotifications = Optional.ofNullable(quota.getNotifications())
            .orElseGet(ArrayList::new);
        quotaNotifications.add(NO_ACTIVE_QUOTAS_NOTIFICATION);
        return quotaNotifications.stream()
            .filter(Objects::nonNull)
            .sorted(quotasComparator(storage).reversed())
            .filter(notification -> exceedsLimit(storage, notification))
            .findFirst()
            .map(notification -> mapNotificationToStatus(storage, notification, quota.getRecipients(),
                                                         notificationTriggers))
            .orElse(NFSStorageMountStatus.ACTIVE);
    }

    private Comparator<NFSQuotaNotificationEntry> quotasComparator(final NFSDataStorage storage) {
        return (entry1, entry2) -> {
            final StorageQuotaType type1 = entry1.getType();
            final StorageQuotaType type2 = entry2.getType();
            if (type1.equals(type2)) {
                return entry1.getValue().compareTo(entry2.getValue());
            } else {
                final FileShareMount shareMount = fileShareMountManager.load(storage.getFileShareMountId());
                final MountType shareType = shareMount.getMountType();
                switch (shareType) {
                    case LUSTRE:
                        return lustreManager.findLustreFS(shareMount)
                            .map(lustreFS -> compareMixedQuotasForLustre(entry1, entry2, lustreFS))
                            .orElse(0);
                    case NFS:
                        // assuming for NFS, that percentage quota is always greater, than the absolute one
                        return StorageQuotaType.GIGABYTES.equals(type1) ? -1 : 1;
                    default:
                        return 0;
                }
            }
        };
    }

    private int compareMixedQuotasForLustre(final NFSQuotaNotificationEntry entry1,
                                            final NFSQuotaNotificationEntry entry2,
                                            final LustreFS lustreFS) {
        final Integer capacityGb = lustreFS.getCapacityGb();
        final Double absoluteQuota1;
        final Double absoluteQuota2;
        if (StorageQuotaType.GIGABYTES.equals(entry1.getType())) {
            absoluteQuota1 = entry1.getValue();
            absoluteQuota2 = convertLustrePercentageLimitToAbsoluteValue(entry2.getValue(), capacityGb);
        } else {
            absoluteQuota1 = convertLustrePercentageLimitToAbsoluteValue(entry1.getValue(), capacityGb);
            absoluteQuota2 = entry2.getValue();
        }
        return absoluteQuota1.compareTo(absoluteQuota2);
    }

    private NFSStorageMountStatus mapNotificationToStatus(final NFSDataStorage storage,
                                                          final NFSQuotaNotificationEntry notification,
                                                          final List<NFSQuotaNotificationRecipient> recipients,
                                                          final Map<Long, NFSQuotaNotificationEntry>
                                                              notificationTriggers) {
        final Set<StorageQuotaAction> actions = notification.getActions();
        final NFSStorageMountStatus mountStatus = resolveMountStatus(storage, actions);
        if (actions.contains(StorageQuotaAction.EMAIL) && requireNotification(storage, mountStatus, notification,
                                                                              notificationTriggers)) {
            notificationManager.notifyOnStorageQuotaExceeding(storage, mountStatus, notification, recipients);
            triggersManager.insert(new NFSQuotaTrigger(storage.getId(), notification));
        }
        return mountStatus;
    }

    private boolean requireNotification(final NFSDataStorage storage, final NFSStorageMountStatus newMountStatus,
                                        final NFSQuotaNotificationEntry notification,
                                        final Map<Long, NFSQuotaNotificationEntry> notificationTriggers) {
        return !(newMountStatus.equals(storage.getMountStatus())
                 && hasSameTrigger(storage, notification, notificationTriggers));
    }

    private boolean hasSameTrigger(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification,
                                   final Map<Long, NFSQuotaNotificationEntry> notificationTriggers) {
        return Optional.ofNullable(notificationTriggers.getOrDefault(storage.getId(), NO_ACTIVE_QUOTAS_NOTIFICATION))
            .filter(lastTrigger -> lastTrigger.getValue().equals(notification.getValue()))
            .filter(lastTrigger -> lastTrigger.getType().equals(notification.getType()))
            .isPresent();
    }

    private NFSStorageMountStatus resolveMountStatus(NFSDataStorage storage, Set<StorageQuotaAction> actions) {
        final NFSStorageMountStatus mountStatus;
        if (actions.contains(StorageQuotaAction.READ_ONLY)) {
            mountStatus = NFSStorageMountStatus.READ_ONLY;
        } else if (actions.contains(StorageQuotaAction.DISABLE)) {
            mountStatus = NFSStorageMountStatus.MOUNT_DISABLED;
        } else if (actions.contains(StorageQuotaAction.EMAIL)) {
            mountStatus = NFSStorageMountStatus.ACTIVE;
        } else {
            log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_UNKNOWN_RESTRICTION,
                                              actions, storage.getId()));
            mountStatus = defaultRestrictiveStatus;
        }
        return mountStatus;
    }

    private boolean exceedsLimit(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification) {
        final Double originalLimit = notification.getValue();
        final StorageUsage storageUsage = searchManager.getStorageUsage(storage, null, true);
        final StorageQuotaType notificationType = notification.getType();
        switch (notificationType) {
            case GIGABYTES:
                return exceedsAbsoluteLimit(originalLimit, storageUsage);
            case PERCENTS:
                return exceedsPercentageLimit(storage, originalLimit, storageUsage);
            default:
                log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_UNKNOWN_TYPE, notificationType));
                return false;
        }
    }

    private boolean exceedsAbsoluteLimit(final Double originalLimit, final StorageUsage storageUsage) {
        final long limitBytes = (long) (originalLimit * GB_TO_BYTES);
        return storageUsage.getSize() > limitBytes;
    }

    private boolean exceedsPercentageLimit(final NFSDataStorage storage, final Double originalLimit,
                                           final StorageUsage storageUsage) {
        final FileShareMount shareMount = fileShareMountManager.load(storage.getFileShareMountId());
        final MountType shareType = shareMount.getMountType();
        switch (shareType) {
            case LUSTRE:
                return lustreManager.findLustreFS(shareMount)
                    .map(LustreFS::getCapacityGb)
                    .map(maxSize -> convertLustrePercentageLimitToAbsoluteValue(originalLimit, maxSize) * GB_TO_BYTES)
                    .map(limit -> storageUsage.getSize() > limit)
                    .orElse(false);
            case NFS:
                log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_NFS_PERCENTAGE_QUOTA_WARN,
                                                  storage.getId()));
                break;
            default:
                log.warn(messageHelper.getMessage(MessageConstants.STORAGE_QUOTA_PERCENTS_UNKNOWN_SHARE_TYPE,
                                                  shareType));
                break;
        }
        return false;
    }

    private Double convertLustrePercentageLimitToAbsoluteValue(final Double percentage, final Integer capacityGb) {
        return capacityGb * percentage / PERCENTS_MULTIPLIER;
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
