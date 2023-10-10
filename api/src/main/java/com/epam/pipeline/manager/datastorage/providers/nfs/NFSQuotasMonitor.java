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
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.StorageQuotaTriggersManager;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.search.SearchManager;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NFSQuotasMonitor {

    private static final int GB_TO_BYTES = 1024 * 1024 * 1024;
    private static final double GB_TO_GIB = 0.93;
    private static final int PERCENTS_MULTIPLIER = 100;
    private static final String NFS_STORAGE_TIER = "STANDARD";
    private static final StorageUsage.StorageUsageStats EMPTY_USAGE = new StorageUsage.StorageUsageStats(
            NFS_STORAGE_TIER, 0L, 0L, 0L, 0L, 0L, 0L);

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
    private final PreferenceManager preferenceManager;
    private final Integer notificationResendTimeout;
    private final Map<StorageQuotaAction, Integer> graceConfiguration;
    private final Map<Long, NFSQuotaTrigger> latestTriggers;
    private final Map<Long, NFSQuotaNotificationEntry> notificationTriggers;
    private final Map<Long, NFSQuota> activeQuotas;

    public NFSQuotasMonitor(final DataStorageManager dataStorageManager,
                            final SearchManager searchManager,
                            final MetadataManager metadataManager,
                            final FileShareMountManager fileShareMountManager,
                            final LustreFSManager lustreManager,
                            final MessageHelper messageHelper,
                            final NotificationManager notificationManager,
                            final StorageQuotaTriggersManager triggersManager,
                            final PreferenceManager preferenceManager,
                            final @Value("${data.storage.nfs.quota.metadata.key:fs_notifications}")
                                        String notificationsKey,
                            final @Value("${data.storage.nfs.quota.default.restrictive.status:READ_ONLY}")
                                NFSStorageMountStatus defaultRestrictiveStatus,
                            final @Value("${data.storage.nfs.quota.triggers.resend.timeout.minutes:1440}")
                                Integer notificationResendTimeout) {
        this.dataStorageManager = dataStorageManager;
        this.searchManager = searchManager;
        this.metadataManager = metadataManager;
        this.fileShareMountManager = fileShareMountManager;
        this.lustreManager = lustreManager;
        this.messageHelper = messageHelper;
        this.notificationManager = notificationManager;
        this.triggersManager = triggersManager;
        this.preferenceManager = preferenceManager;
        this.notificationsKey = notificationsKey;
        this.defaultRestrictiveStatus = defaultRestrictiveStatus;
        this.notificationResendTimeout = notificationResendTimeout;
        this.graceConfiguration = new HashMap<>();
        this.latestTriggers = new HashMap<>();
        this.notificationTriggers = new HashMap<>();
        this.activeQuotas = new HashMap<>();
    }

    @Scheduled(fixedDelayString = "${data.storage.nfs.quota.poll:60000}")
    @SchedulerLock(name = "NFSQuotasMonitor_controlQuotas", lockAtMostForString = "PT10M")
    public void controlQuotas() {
        log.info("Start NFS quotas processing...");
        final List<NFSQuotaTrigger> triggersList = triggersManager.loadAll();
        final List<NFSDataStorage> nfsDataStorages = loadAllNFS(dataStorageManager.getDataStorages());
        updateMapsState(triggersList, nfsDataStorages);
        final Map<String, Set<String>> storageSizeMasksMapping = dataStorageManager.loadSizeCalculationMasksMapping();
        nfsDataStorages.forEach(storage -> processStorageQuota(storage, storageSizeMasksMapping));
        final Map<Long, NFSDataStorage> nfsMapping = nfsDataStorages.stream()
            .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        final LocalDateTime checkTime = DateUtils.nowUTC();
        controlStatus(nfsMapping, checkTime);
        controlNotifications(nfsMapping, checkTime);
        log.info("NFS quotas are processed successfully.");
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void processStorageQuota(final NFSDataStorage storage,
                                     final Map<String, Set<String>> storageSizeMasksMapping) {
        try {
            Optional.ofNullable(activeQuotas.get(storage.getId()))
                .ifPresent(quota -> processActiveQuota(
                    quota, storage, dataStorageManager.resolveSizeMasks(storageSizeMasksMapping, storage)));
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

    private void processActiveQuota(final NFSQuota quota, final NFSDataStorage storage,
                                    final Set<String> storageSizeMasks) {
        CollectionUtils.emptyIfNull(quota.getNotifications()).stream()
            .filter(Objects::nonNull)
            .sorted(quotasComparator(storage).reversed())
            .filter(notification -> exceedsLimit(storage, notification, storageSizeMasks))
            .findFirst()
            .ifPresent(notification -> checkMatchingNotification(storage, notification, quota.getRecipients()));
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

    private void checkMatchingNotification(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification,
                                           final List<NFSQuotaNotificationRecipient> recipients) {
        final Set<StorageQuotaAction> actions = notification.getActions();
        final NFSStorageMountStatus newStatus = resolveMountStatus(storage, actions);
        if (requireStatusChange(storage, newStatus, notification)) {
            final LocalDateTime executionTime = DateUtils.nowUTC();
            final Long storageId = storage.getId();
            final LocalDateTime activationTime = resolveActivationTime(storage, newStatus, executionTime);
            final boolean isNotificationRequired = actions.contains(StorageQuotaAction.EMAIL);
            final NFSQuotaTrigger newTrigger = new NFSQuotaTrigger(storageId, notification, recipients,
                                                                   executionTime, newStatus, activationTime,
                                                                   isNotificationRequired);
            final boolean isDelayedActivation = activationTime.isAfter(executionTime);
            updateTrigger(isDelayedActivation
                          ? newTrigger
                          : newTrigger.toBuilder().targetStatusActivationTime(null).build());
            if (isNotificationRequired && isDelayedActivation) {
                notificationManager.notifyOnStorageQuotaExceeding(storage, newStatus, notification, recipients,
                                                                  activationTime);
            }
        }
    }

    private LocalDateTime resolveActivationTime(final NFSDataStorage storage,
                                                final NFSStorageMountStatus newMountStatus,
                                                final LocalDateTime immediateExecutionTime) {
        final NFSStorageMountStatus currentMountStatus = storage.getMountStatus();
        final int graceDelay = searchCorrespondingAction(newMountStatus)
            .map(graceConfiguration::get)
            .orElse(0);
        if (currentMountStatus.getPriority() >= newMountStatus.getPriority()
            || graceDelay == 0) {
            return immediateExecutionTime;
        } else {
            final LocalDateTime activationFromNow = immediateExecutionTime.plus(graceDelay, ChronoUnit.MINUTES);
            return Optional.ofNullable(latestTriggers.get(storage.getId()))
                .filter(lastTrigger -> !lastTrigger.getTargetStatus().equals(NFSStorageMountStatus.ACTIVE))
                .map(NFSQuotaTrigger::getTargetStatusActivationTime)
                .filter(lastDelayedActivation -> lastDelayedActivation.isBefore(activationFromNow))
                .orElse(activationFromNow);
        }
    }

    private Optional<StorageQuotaAction> searchCorrespondingAction(final NFSStorageMountStatus mountStatus) {
        if (mountStatus.equals(NFSStorageMountStatus.READ_ONLY)) {
            return Optional.of(StorageQuotaAction.READ_ONLY);
        } else if (mountStatus.equals(NFSStorageMountStatus.MOUNT_DISABLED)) {
            return Optional.of(StorageQuotaAction.DISABLE);
        } else {
            return Optional.empty();
        }
    }

    private boolean requireStatusChange(final NFSDataStorage storage, final NFSStorageMountStatus newMountStatus,
                                        final NFSQuotaNotificationEntry notification) {
        final NFSStorageMountStatus currentMountStatus = Optional.ofNullable(latestTriggers.get(storage.getId()))
            .map(NFSQuotaTrigger::getTargetStatus)
            .orElse(storage.getMountStatus());
        return !(newMountStatus.equals(currentMountStatus)
                 && hasSameTrigger(storage, notification));
    }

    private boolean hasSameTrigger(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification) {
        return Optional.ofNullable(notificationTriggers.getOrDefault(storage.getId(), NO_ACTIVE_QUOTAS_NOTIFICATION))
            .filter(lastTrigger -> lastTrigger.getValue().equals(notification.getValue()))
            .filter(lastTrigger -> lastTrigger.getType().equals(notification.getType()))
            .isPresent();
    }

    private NFSStorageMountStatus resolveMountStatus(final NFSDataStorage storage,
                                                     final Set<StorageQuotaAction> actions) {
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

    private boolean exceedsLimit(final NFSDataStorage storage, final NFSQuotaNotificationEntry notification,
                                 final Set<String> storageSizeMasks) {
        final Double originalLimit = notification.getValue();
        final StorageUsage storageUsage = searchManager.getStorageUsage(storage, null, false, storageSizeMasks,
                storage.getType().getStorageClasses(), false);
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
        return storageUsage.getUsage()
                .getOrDefault(NFS_STORAGE_TIER, EMPTY_USAGE).getEffectiveSize() > limitBytes;
    }

    private boolean exceedsPercentageLimit(final NFSDataStorage storage, final Double originalLimit,
                                           final StorageUsage storageUsage) {
        final FileShareMount shareMount = fileShareMountManager.load(storage.getFileShareMountId());
        final MountType shareType = shareMount.getMountType();
        switch (shareType) {
            case LUSTRE:
                return lustreManager.findLustreFS(shareMount)
                    .map(fs -> fs.getCapacityGb() * GB_TO_GIB)
                    .map(maxSize -> convertLustrePercentageLimitToAbsoluteValue(originalLimit, maxSize.intValue())
                            * GB_TO_BYTES)
                    .map(limit -> storageUsage.getUsage()
                            .getOrDefault(NFS_STORAGE_TIER, EMPTY_USAGE).getEffectiveSize() > limit)
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

    private Map<Long, NFSQuota> loadStorageQuotas(final List<NFSDataStorage> storages,
                                                  final List<NFSQuotaTrigger> notificationTriggers) {
        final Map<Long, NFSQuota> activeQuotasMap = storages.stream()
            .map(BaseEntity::getId)
            .map(storageId -> metadataManager.loadMetadataItem(storageId, AclClass.DATA_STORAGE))
            .filter(Objects::nonNull)
            .filter(metadataEntry -> metadataEntry.getData().containsKey(notificationsKey))
            .collect(Collectors.toMap(metadataEntry -> metadataEntry.getEntity().getEntityId(),
                                      this::mapPreferenceToQuota));
        activeQuotasMap.values().removeIf(quota -> CollectionUtils.isEmpty(quota.getNotifications()));
        final Map<Long, NFSQuota> removedQuotas = notificationTriggers.stream()
            .filter(trigger -> !activeQuotasMap.containsKey(trigger.getStorageId()))
            .collect(Collectors.toMap(NFSQuotaTrigger::getStorageId,
                trigger -> new NFSQuota(new ArrayList<>(), trigger.getRecipients())));
        activeQuotasMap.putAll(removedQuotas);
        activeQuotasMap.values().forEach(quota -> quota.getNotifications().add(NO_ACTIVE_QUOTAS_NOTIFICATION));
        return activeQuotasMap;
    }

    private NFSQuota mapPreferenceToQuota(final MetadataEntry metadata) {
        final PipeConfValue value = metadata.getData().get(notificationsKey);
        return JsonMapper.parseData(value.getValue(), new TypeReference<NFSQuota>() {});
    }

    private void updateMapsState(final List<NFSQuotaTrigger> triggersList, final List<NFSDataStorage> nfsDataStorages) {
        replaceValuesInMap(graceConfiguration,
                           preferenceManager.getPreference(SystemPreferences.STORAGE_QUOTAS_ACTIONS_GRACE));
        replaceValuesInMap(latestTriggers,
                           triggersList.stream()
                               .collect(Collectors.toMap(NFSQuotaTrigger::getStorageId, Function.identity())));
        replaceValuesInMap(notificationTriggers,
                           triggersList.stream()
                               .collect(Collectors.toMap(NFSQuotaTrigger::getStorageId, NFSQuotaTrigger::getQuota)));
        replaceValuesInMap(activeQuotas, loadStorageQuotas(nfsDataStorages, triggersList));
    }

    private <K, V> void replaceValuesInMap(final Map<K, V> map, final Map<K, V> update) {
        map.clear();
        map.putAll(update);
    }

    private void controlStatus(final Map<Long, NFSDataStorage> nfsMapping, final LocalDateTime checkTime) {
        latestTriggers.values().stream()
            .filter(trigger -> Optional.ofNullable(trigger.getTargetStatusActivationTime())
                .map(activationTime -> activationTime.isBefore(checkTime))
                .orElse(true))
            .map(trigger -> Pair.of(nfsMapping.get(trigger.getStorageId()), trigger))
            .filter(pair -> pair.getKey() != null)
            .filter(pair -> pair.getKey().getMountStatus() != pair.getValue().getTargetStatus())
            .forEach(pair -> {
                final NFSDataStorage storage = pair.getKey();
                final NFSQuotaTrigger trigger = pair.getValue();
                updateStorageStatus(storage, trigger, checkTime);
            });
    }

    private void updateStorageStatus(final NFSDataStorage storage, final NFSQuotaTrigger trigger,
                                     final LocalDateTime checkTime) {
        final NFSStorageMountStatus newStatus = trigger.getTargetStatus();
        final NFSQuotaNotificationEntry notification = trigger.getQuota();
        final List<NFSQuotaNotificationRecipient> recipients = trigger.getRecipients();
        dataStorageManager.updateMountStatus(storage, newStatus);
        storage.setMountStatus(newStatus);
        if (trigger.isNotificationRequired()) {
            notificationManager.notifyOnStorageQuotaExceeding(storage, newStatus, notification, recipients, null);
        }
        updateTrigger(trigger.toBuilder().executionTime(checkTime).build());
    }

    private void controlNotifications(final Map<Long, NFSDataStorage> nfsMapping, final LocalDateTime checkTime) {
        final List<NFSQuotaTrigger> triggersToDelete = new ArrayList<>();
        latestTriggers.values().stream()
            .filter(NFSQuotaTrigger::isNotificationRequired)
            .filter(trigger -> trigger.getExecutionTime()
                .plus(notificationResendTimeout, ChronoUnit.MINUTES)
                .isBefore(checkTime))
            .forEach(expiredTrigger ->
                         resendNotification(expiredTrigger, nfsMapping.get(expiredTrigger.getStorageId()), checkTime,
                                 triggersToDelete));
        ListUtils.emptyIfNull(triggersToDelete).forEach(this::deleteTrigger);
    }

    private void resendNotification(final NFSQuotaTrigger expiredTrigger, final NFSDataStorage storage,
                                    final LocalDateTime checkTime, final List<NFSQuotaTrigger> triggersToDelete) {
        final NFSStorageMountStatus newStatus = expiredTrigger.getTargetStatus();
        final NFSQuotaNotificationEntry notification = expiredTrigger.getQuota();
        final List<NFSQuotaNotificationRecipient> recipients = expiredTrigger.getRecipients();
        notificationManager.notifyOnStorageQuotaExceeding(storage, newStatus, notification, recipients,
                                                          storage.getMountStatus().equals(newStatus)
                                                          ? null
                                                          : expiredTrigger.getTargetStatusActivationTime());
        if (expiredTrigger.getQuota().equals(NO_ACTIVE_QUOTAS_NOTIFICATION)) {
            //NO_ACTIVE_QUOTA is sent only once
            triggersToDelete.add(expiredTrigger);
        } else {
            updateTrigger(expiredTrigger.toBuilder().executionTime(checkTime).build());
        }
    }

    private void deleteTrigger(final NFSQuotaTrigger expiredTrigger) {
        triggersManager.delete(expiredTrigger);
        latestTriggers.remove(expiredTrigger.getStorageId());
    }

    private void updateTrigger(final NFSQuotaTrigger triggerUpdate) {
        triggersManager.insert(triggerUpdate);
        latestTriggers.put(triggerUpdate.getStorageId(), triggerUpdate);
    }
}
