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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.AppliedQuota;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.notification.NotificationGroup;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.entity.notification.NotificationTimestamp;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.notification.filter.NotificationFilter;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.Sid;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.PipelineRunMapper;
import com.epam.pipeline.controller.vo.notification.NotificationMessageVO;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
@Slf4j
public class NotificationManager implements NotificationService { // TODO: rewrite with Strategy pattern?
    private static final double PERCENT = 100.0;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([^ ]*\\b)");

    @Autowired
    private UserManager userManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationSettingsManager settingsManager;

    @Autowired
    private ContextualNotificationManager contextualNotificationManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * Internal method for creating notification message that selecting appropriate email template from db,
     * serialize PipelineRun to key-value object and save it to notification_queue table.
     * @param run
     * @param duration Running duration of a run in seconds.
     * @param settings defines, if a long initialization or long running message template should be used
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyLongRunningTask(final PipelineRun run, final Long duration, final NotificationSettings settings) {
        log.debug(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_SUBMITTED, run.getPodId()));

        final String instanceTypesToExclude = preferenceManager.getPreference(SystemPreferences
                .SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES);

        if (!noneMatchExcludedInstanceType(run, instanceTypesToExclude)) {
            return;
        }

        if (matchExcludeRunParameters(run, parseRunExcludeParams())) {
            return;
        }

        final NotificationMessage notificationMessage = new NotificationMessage();

        if (settings.isKeepInformedOwner()) {
            PipelineUser pipelineOwner = userManager.loadUserByName(run.getOwner());
            notificationMessage.setToUserId(pipelineOwner.getId());
        }

        notificationMessage.setCopyUserIds(getCCUsers(settings));

        notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
        if (notificationMessage.getTemplate() == null) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                    settings.getTemplateId()));
        }

        notificationMessage.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold(), duration));
        saveNotification(notificationMessage);
    }

    /**
     * Notify users that a new issue was created for a given entity. Will notify mentioned users as well.
     *
     * @param issue an issue to notify about
     * @param entity an entity for wich issue was created
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIssue(final Issue issue, final AbstractSecuredEntity entity, final String htmlText) {
        final NotificationSettings newIssueSettings = settingsManager.load(NotificationType.NEW_ISSUE);
        if (newIssueSettings == null || !newIssueSettings.isEnabled()) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_TEMPLATE_NOT_CONFIGURED,
                            "new issue"));
            return;
        }

        final NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(newIssueSettings.getTemplateId()));
        message.setCopyUserIds(getMentionedUsers(issue.getText()));

        final Issue copyWithHtml = issue.toBuilder().text(htmlText).build();
        message.setTemplateParameters(jsonMapper.convertValue(copyWithHtml,
                                                              new TypeReference<Map<String, Object>>() {}));

        if (newIssueSettings.isKeepInformedOwner()) {
            PipelineUser owner = userManager.loadUserByName(entity.getOwner());
            message.setToUserId(owner.getId());
        }

        saveNotification(message);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIssueComment(final IssueComment comment, final Issue issue, final String htmlText) {
        final NotificationSettings newIssueCommentSettings = settingsManager
                .load(NotificationType.NEW_ISSUE_COMMENT);
        if (newIssueCommentSettings == null || !newIssueCommentSettings.isEnabled()) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_TEMPLATE_NOT_CONFIGURED,
                    "new issue"));
            return;
        }

        final NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(newIssueCommentSettings.getTemplateId()));

        final AbstractSecuredEntity entity = entityManager.load(issue.getEntity().getEntityClass(),
                                                          issue.getEntity().getEntityId());
        final List<PipelineUser> referencedUsers = userManager.loadUsersByNames(Arrays.asList(entity.getOwner(),
                                                                                        issue.getAuthor()));
        final List<Long> ccUserIds = getMentionedUsers(comment.getText());
        referencedUsers.stream()
            .filter(u -> u.getUserName().equals(entity.getOwner()))
            .findFirst()
            .ifPresent(owner -> ccUserIds.add(owner.getId()));

        message.setCopyUserIds(ccUserIds);

        if (newIssueCommentSettings.isKeepInformedOwner()) {
            PipelineUser author = referencedUsers.stream()
                .filter(u -> u.getUserName().equals(issue.getAuthor()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No issue author was found"));
            message.setToUserId(author.getId());
        }

        final IssueComment copyWithHtml = comment.toBuilder().text(htmlText).build();

        final Map<String, Object> commentParams = jsonMapper.convertValue(copyWithHtml,
                                                                    new TypeReference<Map<String, Object>>() {});
        commentParams.put("issue", jsonMapper.convertValue(issue, new TypeReference<Map<String, Object>>() {}));
        message.setTemplateParameters(commentParams);

        saveNotification(message);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyRunStatusChanged(final PipelineRun pipelineRun) {
        contextualNotificationManager.notifyRunStatusChanged(pipelineRun);

        final NotificationSettings runStatusSettings = settingsManager.load(NotificationType.PIPELINE_RUN_STATUS);
        if (runStatusSettings == null || !runStatusSettings.isEnabled()) {
            log.info("No template configured for pipeline run status changes notifications or it was disabled!");
            return;
        }

        final List<TaskStatus> runStatusesToReport = ListUtils.emptyIfNull(runStatusSettings.getStatusesToInform());
        if (!CollectionUtils.isEmpty(runStatusesToReport) && !runStatusesToReport.contains(pipelineRun.getStatus())) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_RUN_STATUS_NOT_CONFIGURED_FOR_NOTIFICATION,
                    pipelineRun.getStatus(),
                    runStatusesToReport.stream().map(TaskStatus::name).collect(Collectors.joining(", "))));
            return;
        }

        final NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(runStatusSettings.getTemplateId()));
        message.setTemplateParameters(PipelineRunMapper.map(pipelineRun));

        message.setCopyUserIds(getCCUsers(runStatusSettings));

        if (runStatusSettings.isKeepInformedOwner()) {
            PipelineUser pipelineOwner = userManager.loadUserByName(pipelineRun.getOwner());
            message.setToUserId(pipelineOwner.getId());
        }

        saveNotification(message);
    }

    /**
     * Issues a notification of an idle Pipeline Run for multiple runs.
     *
     * @param pipelineCpuRatePairs a list of pairs of PipelineRun and Double cpu usage rate value
     * @param notificationType a type of notification to be issued. Supported types are IDLE_RUN, IDLE_RUN_PAUSED,
     *                         IDLE_RUN_STOPPED
     * @throws IllegalArgumentException if notificationType is not from IDLE_RUN group
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIdleRuns(final List<Pair<PipelineRun, Double>> pipelineCpuRatePairs,
                               final NotificationType notificationType) {
        if (CollectionUtils.isEmpty(pipelineCpuRatePairs)) {
            return;
        }

        Assert.isTrue(NotificationGroup.IDLE_RUN == notificationType.getGroup(),
                      "Only IDLE_RUN group notification types are allowed");

        final NotificationSettings idleRunSettings = settingsManager.load(notificationType);
        if (idleRunSettings == null || !idleRunSettings.isEnabled()) {
            log.info("No template configured for idle pipeline run notifications or it was disabled!");
            return;
        }

        final List<Long> ccUserIds = getCCUsers(idleRunSettings);
        final Map<String, PipelineUser> pipelineOwners = getPipelinesOwners(pipelineCpuRatePairs);

        final double idleCpuLevel = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT);
        final String instanceTypesToExclude = preferenceManager.getPreference(SystemPreferences
                .SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES);
        final Map<String, NotificationFilter> runParametersFilters = parseRunExcludeParams();

        final List<Pair<PipelineRun, Double>> filtered = pipelineCpuRatePairs.stream()
                .filter(pair -> shouldNotifyIdleRun(pair.getLeft().getId(), notificationType, idleRunSettings))
                .filter(pair -> noneMatchExcludedInstanceType(pair.getLeft(), instanceTypesToExclude))
                .filter(pair -> !matchExcludeRunParameters(pair.getLeft(), runParametersFilters))
                .collect(Collectors.toList());
        final List<NotificationMessage> messages = filtered.stream()
                .map(pair -> buildMessageForIdleRun(idleRunSettings, ccUserIds, pipelineOwners, idleCpuLevel, pair))
                .collect(Collectors.toList());
        saveNotifications(messages);

        if (NotificationType.IDLE_RUN.equals(notificationType)) {
            final List<Long> runIds = filtered.stream()
                    .map(pair -> pair.getLeft().getId()).collect(Collectors.toList());
            monitoringNotificationDao.updateNotificationTimestamp(runIds, NotificationType.IDLE_RUN);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyHighResourceConsumingRuns(
            final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> pipelinesMetrics,
            final NotificationType notificationType) {
        if (CollectionUtils.isEmpty(pipelinesMetrics)) {
            log.debug("No pipelines are high loaded, notifications won't be sent!");
            return;
        }

        final NotificationSettings notificationSettings = settingsManager.load(notificationType);
        if (notificationSettings == null || !notificationSettings.isEnabled()) {
            log.info("No template configured for high consuming pipeline run notifications or it was disabled!");
            return;
        }

        final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> filtered = pipelinesMetrics.stream()
                .filter(run -> shouldNotify(run.getLeft().getId(), notificationSettings))
                .collect(Collectors.toList());

        log.debug("High resource consuming notifications for pipelines: " +
                filtered.stream()
                        .map(p -> p.getLeft().getId().toString())
                        .collect(Collectors.joining(",")) + " will be sent!");

        final List<Long> ccUserIds = getCCUsers(notificationSettings);

        final Map<String, PipelineUser> pipelineOwners = getPipelinesOwners(filtered);

        final double memThreshold = preferenceManager.getPreference(SystemPreferences.SYSTEM_MEMORY_THRESHOLD_PERCENT);
        final double diskThreshold = preferenceManager.getPreference(SystemPreferences.SYSTEM_DISK_THRESHOLD_PERCENT);

        final List<NotificationMessage> messages = filtered.stream().map(pair -> {
            NotificationMessage message = new NotificationMessage();
            message.setTemplate(new NotificationTemplate(notificationSettings.getTemplateId()));
            message.setTemplateParameters(PipelineRunMapper.map(pair.getLeft()));
            message.getTemplateParameters().put("memoryThreshold", memThreshold);
            message.getTemplateParameters().put("memoryRate",
                    pair.getRight().getOrDefault(ELKUsageMetric.MEM, 0.0) * PERCENT);
            message.getTemplateParameters().put("diskThreshold", diskThreshold);
            message.getTemplateParameters().put("diskRate", pair.getRight()
                    .getOrDefault(ELKUsageMetric.FS, 0.0) * PERCENT);
            if (notificationSettings.isKeepInformedOwner()) {
                message.setToUserId(pipelineOwners.getOrDefault(pair.getLeft().getOwner(), new PipelineUser()).getId());
            }
            message.setCopyUserIds(ccUserIds);
            return message;
        }).collect(Collectors.toList());

        final List<Long> runIds = filtered.stream()
                .map(pm -> pm.getLeft().getId()).collect(Collectors.toList());
        saveNotifications(messages);
        monitoringNotificationDao.updateNotificationTimestamp(runIds, notificationType);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyStuckInStatusRuns(final List<PipelineRun> runs) {
        final NotificationSettings settings = settingsManager.load(NotificationType.LONG_STATUS);

        if (settings == null || !settings.isEnabled() || settings.getTemplateId() == 0) {
            log.info("No template configured for stuck status notifications or it was disabled!");
            return;
        }

        final LocalDateTime now = DateUtils.nowUTC();
        final Long threshold = settings.getThreshold();
        if (threshold == null || threshold <= 0) {
            return;
        }
        runs.stream()
                .filter(run -> isRunStuckInStatus(settings, now, threshold, run))
                .forEach(run -> {
                    log.debug("Sending stuck status {} notification for run {}.",
                            run.getStatus(), run.getId());
                    final NotificationMessage notificationMessage = new NotificationMessage();
                    if (settings.isKeepInformedOwner()) {
                        PipelineUser pipelineOwner = userManager.loadUserByName(run.getOwner());
                        notificationMessage.setToUserId(pipelineOwner.getId());
                    }
                    notificationMessage.setCopyUserIds(getCCUsers(settings));
                    notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
                    notificationMessage.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold()));
                    saveNotification(notificationMessage);
                });
    }

    /**
     * Creates notifications for long paused runs.
     * @param pausedRuns the list of the {@link PipelineRun} objects that in paused state
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyLongPausedRuns(final List<PipelineRun> pausedRuns) {
        final List<PipelineRun> longPausedRuns = createNotificationsForLongPausedRuns(pausedRuns,
                NotificationType.LONG_PAUSED);

        if (CollectionUtils.isEmpty(longPausedRuns)) {
            return;
        }

        final List<Long> runIds = longPausedRuns.stream()
                .map(PipelineRun::getId)
                .collect(Collectors.toList());
        monitoringNotificationDao.updateNotificationTimestamp(runIds, NotificationType.LONG_PAUSED);
    }

    /**
     * Creates notifications for long paused runs that shall be stopped.
     * @param pausedRuns the list of the {@link PipelineRun} objects that in paused state
     * @return the list of the {@link PipelineRun} objects that in long paused state
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRun> notifyLongPausedRunsBeforeStop(final List<PipelineRun> pausedRuns) {
        return createNotificationsForLongPausedRuns(pausedRuns, NotificationType.LONG_PAUSED_STOPPED);
    }

    /**
     * Creates notifications regarding storage quotas.
     * @param storage the NFS storage that exceeding the quota
     * @param exceededQuota the quota, that was exceeded
     * @param recipients list of users to be notified
     * @param activationTime time of the quota activation (null if immediately)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyOnStorageQuotaExceeding(final NFSDataStorage storage,
                                              final NFSStorageMountStatus newStatus,
                                              final NFSQuotaNotificationEntry exceededQuota,
                                              final List<NFSQuotaNotificationRecipient> recipients,
                                              final LocalDateTime activationTime) {
        final NotificationSettings notificationSettings =
            settingsManager.load(NotificationType.STORAGE_QUOTA_EXCEEDING);
        if (notificationSettings == null || !notificationSettings.isEnabled()) {
            log.info("No template configured for storage quotas notifications or it was disabled!");
            return;
        }
        log.info("Storage quota exceeding notification for datastorage id={} will be sent!", storage.getId());

        final List<Long> ccUserIds = mapRecipientsToUserIds(recipients);
        if (CollectionUtils.isEmpty(ccUserIds)) {
            log.info("Resolved list of users is empty, skipping notification creation...");
            return;
        }

        final NotificationMessage quotaNotificationMessage = new NotificationMessage();
        quotaNotificationMessage.setCopyUserIds(ccUserIds);
        quotaNotificationMessage.setTemplate(new NotificationTemplate(notificationSettings.getTemplateId()));
        quotaNotificationMessage.setTemplateParameters(
            buildQuotasPlaceholdersDict(storage, exceededQuota, newStatus, activationTime));
        saveNotification(quotaNotificationMessage);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyOnBillingQuotaExceeding(final AppliedQuota appliedQuota) {
        Optional.ofNullable(getNotificationSettings(NotificationType.BILLING_QUOTA_EXCEEDING))
                .ifPresent(settings -> {
                    log.info("Sending notification for billing quota {}", appliedQuota.getQuota());
                    final List<Long> ccUserIds = mapRecipientsToUserIds(appliedQuota.getQuota().getRecipients());
                    if (CollectionUtils.isEmpty(ccUserIds)) {
                        log.info("Resolved list of users is empty, skipping notification creation...");
                        return;
                    }
                    final NotificationMessage message = new NotificationMessage();
                    message.setCopyUserIds(ccUserIds);
                    message.setTemplate(new NotificationTemplate(settings.getTemplateId()));
                    message.setTemplateParameters(buildBillingQuotaParams(appliedQuota));
                    saveNotification(message);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyPipelineUsers(final List<PipelineUser> pipelineUsers, final NotificationType type) {
        if (CollectionUtils.isEmpty(pipelineUsers)) {
            log.debug("No users found for '{}' notification", type.name());
            return;
        }
        final NotificationSettings notificationSettings = settingsManager.load(type);
        if (notificationSettings == null || !notificationSettings.isEnabled()) {
            log.info("No template configured for '{}' users notifications or it was disabled!", type.name());
            return;
        }

        final List<Long> ccUserIds = getCCUsers(notificationSettings);

        final List<Long> storageIdsToLoad = ListUtils.emptyIfNull(pipelineUsers).stream()
                .map(PipelineUser::getDefaultStorageId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, String> userStorages = ListUtils.emptyIfNull(
                dataStorageManager.getDatastoragesByIds(storageIdsToLoad)).stream()
                .collect(Collectors.toMap(AbstractDataStorage::getId, AbstractDataStorage::getName));

        final NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTemplate(new NotificationTemplate(notificationSettings.getTemplateId()));
        notificationMessage.setTemplateParameters(buildUsersTemplateArguments(pipelineUsers, userStorages));
        notificationMessage.setCopyUserIds(ccUserIds);
        saveNotification(notificationMessage);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyFullNodePools(final List<NodePool> nodePools) {
        if (CollectionUtils.isEmpty(nodePools)) {
            log.debug("No full node pools found to notify");
            return;
        }
        final NotificationSettings notificationSettings =
                settingsManager.load(NotificationType.FULL_NODE_POOL);
        if (notificationSettings == null || !notificationSettings.isEnabled()) {
            log.info("No template configured for node pool notifications or it was disabled!");
            return;
        }

        final List<NodePool> filteredPools = nodePools.stream()
                .filter(pool -> shouldNotify(pool.getId(), notificationSettings))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(filteredPools)) {
            log.debug("No full node pools found to notify");
            return;
        }

        log.debug("Notification for node pools [{}] will be send", filteredPools.stream()
                .map(NodePool::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(",")));

        final List<Long> ccUserIds = getCCUsers(notificationSettings);
        final NotificationMessage message = buildMessageForFullNodePool(filteredPools, notificationSettings, ccUserIds);
        saveNotification(message);
        monitoringNotificationDao.updateNotificationTimestamp(filteredPools.stream()
                .map(NodePool::getId)
                .collect(Collectors.toList()), NotificationType.FULL_NODE_POOL);
    }

    /**
     * Creates a custom notification.
     *
     * @param messageVO Notification message request that contains at least subject, body and toUser fields.
     * @return Created notification.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public NotificationMessage createNotification(final NotificationMessageVO messageVO) {
        Assert.notNull(messageVO.getSubject(), messageHelper.getMessage(
                MessageConstants.ERROR_NOTIFICATION_SUBJECT_NOT_SPECIFIED));
        Assert.notNull(messageVO.getBody(), messageHelper.getMessage(
                MessageConstants.ERROR_NOTIFICATION_BODY_NOT_SPECIFIED));
        Assert.notNull(messageVO.getToUser(), messageHelper.getMessage(
                MessageConstants.ERROR_NOTIFICATION_RECEIVER_NOT_SPECIFIED));

        final NotificationMessage message = toMessage(messageVO);
        saveNotification(message);
        return message;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void removeNotificationTimestamps(final Long id) {
        monitoringNotificationDao.deleteNotificationTimestampsForId(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void removeNotificationTimestamps(final Long id, final NotificationType type) {
        monitoringNotificationDao.deleteNotificationTimestampsForIdAndType(id, type);
    }

    public Optional<NotificationTimestamp> loadLastNotificationTimestamp(final Long id,
                                                                         final NotificationType type) {
        return monitoringNotificationDao.loadNotificationTimestamp(id, type);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveNotifications(final List<NotificationMessage> messages) {
        monitoringNotificationDao.createMonitoringNotifications(messages);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveNotification(final NotificationMessage message) {
        monitoringNotificationDao.createMonitoringNotification(message);
    }

    private List<Long> mapRecipientsToUserIds(final List<? extends Sid> recipients) {
        final Stream<PipelineUser> plainUsersStream = recipients.stream()
                .filter(Sid::isPrincipal)
                .map(Sid::getName)
                .map(userManager::loadUserByName);
        final Stream<PipelineUser> usersFromGroupsStream = recipients.stream()
                .filter(recipient -> !recipient.isPrincipal())
                .map(Sid::getName)
                .map(userManager::loadUsersByGroupOrRole)
                .flatMap(Collection::stream);
        return Stream.concat(plainUsersStream, usersFromGroupsStream)
                .filter(Objects::nonNull)
                .map(PipelineUser::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildQuotasPlaceholdersDict(final NFSDataStorage storage,
                                                            final NFSQuotaNotificationEntry quota,
                                                            final NFSStorageMountStatus newStatus,
                                                            final LocalDateTime activationTime) {
        final Map<String, Object> templateParameters = new HashMap<>();
        templateParameters.put("storageId", storage.getId());
        templateParameters.put("storageName", storage.getName());
        templateParameters.put("threshold", NFSQuotaNotificationEntry.NO_ACTIVE_QUOTAS_NOTIFICATION.equals(quota)
                ? "no_active_quotas"
                : quota.toThreshold());
        templateParameters.put("previousMountStatus", storage.getMountStatus());
        templateParameters.put("newMountStatus", newStatus);
        templateParameters.put("activationTime", activationTime);
        return templateParameters;
    }

    private boolean isRunStuckInStatus(final NotificationSettings settings,
                                       final LocalDateTime now,
                                       final Long threshold,
                                       final PipelineRun run) {
        final List<RunStatus> runStatuses = run.getRunStatuses();
        if (CollectionUtils.isEmpty(runStatuses)) {
            log.debug("Status timestamps are not available for run {}. " +
                    "Skipping stuck status duration check.", run.getId());
            return false;
        }

        final Optional<RunStatus> lastStatus = runStatuses.stream()
                .filter(status -> run.getStatus().equals(status.getStatus()))
                .max(Comparator.comparing(RunStatus::getTimestamp));

        return lastStatus
                .map(status -> {
                    final long secondsFromStatusUpdate = status.getTimestamp().until(now, ChronoUnit.SECONDS);
                    return secondsFromStatusUpdate >= threshold && shouldNotify(run.getId(), settings);
                })
                .orElseGet(() -> {
                    log.debug("Failed to find status {} timestamp for run {}.", run.getStatus(), run.getId());
                    return false;
                });
    }

    private boolean shouldNotify(final Long id, final NotificationSettings notificationSettings) {
        final Long resendDelay = notificationSettings.getResendDelay();
        final Optional<NotificationTimestamp> notificationTimestamp = loadLastNotificationTimestamp(
                id,
                notificationSettings.getType());

        return notificationTimestamp
                .map(timestamp -> NotificationTimestamp.isTimeoutEnds(timestamp, resendDelay,  ChronoUnit.SECONDS))
                .orElse(true);
    }

    private <T> Map<String, PipelineUser> getPipelinesOwners(final List<Pair<PipelineRun, T>> pipelineCpuRatePairs) {
        return userManager.loadUsersByNames(pipelineCpuRatePairs.stream()
                .map(p -> p.getLeft().getOwner())
                .collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(PipelineUser::getUserName, user -> user));

    }

    private Map<String, PipelineUser> getPipelinesOwnersFromRuns(final List<PipelineRun> runs) {
        return userManager
                .loadUsersByNames(runs.stream()
                        .map(AbstractSecuredEntity::getOwner)
                        .collect(Collectors.toList())
                ).stream()
                .collect(Collectors.toMap(PipelineUser::getUserName, Function.identity()));
    }

    private List<Long> getCCUsers(final NotificationSettings idleRunSettings) {
        final List<Long> ccUserIds = getKeepInformedUserIds(idleRunSettings);

        if (idleRunSettings.isKeepInformedAdmins()) {
            final List<Long> adminsIds = roleManager.loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId())
                    .getUsers().stream().map(PipelineUser::getId).collect(Collectors.toList());
            return ListUtils.union(ccUserIds, adminsIds);
        }
        return ccUserIds;
    }

    private List<Long> getKeepInformedUserIds(final NotificationSettings settings) {
        return ListUtils.emptyIfNull(settings.getInformedUserIds());
    }

    private List<Long> getMentionedUsers(final String text) {
        final Matcher matcher = MENTION_PATTERN.matcher(text);

        final List<String> userNames = new ArrayList<>(matcher.groupCount());
        while (matcher.find()) {
            userNames.add(matcher.group(1));
        }

        return userManager.loadUsersByNames(userNames).stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList());

    }

    private NotificationMessage toMessage(final NotificationMessageVO messageVO) {
        final NotificationMessage message = new NotificationMessage();
        message.setSubject(messageVO.getSubject());
        message.setBody(messageVO.getBody());
        message.setTemplateParameters(messageVO.getParameters());
        final List<Long> copyUserIds = ListUtils.emptyIfNull(messageVO.getCopyUsers())
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(this::getUserByName)
                .map(PipelineUser::getId)
                .collect(Collectors.toList());
        message.setCopyUserIds(copyUserIds);
        message.setToUserId(getUserByName(messageVO.getToUser()).getId());
        return message;
    }

    private PipelineUser getUserByName(final String username) {
        final PipelineUser user = userManager.loadUserByName(username);
        Assert.notNull(user, messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, username));
        return user;
    }

    private NotificationMessage buildMessageForLongPausedRun(final PipelineRun run, final List<Long> ccUsers,
                                                             final NotificationSettings settings,
                                                             final Map<String, PipelineUser> pipelineOwners) {
        log.debug("Sending long paused run notification for run {}.", run.getId());
        final NotificationMessage message = new NotificationMessage();
        if (settings.isKeepInformedOwner()) {
            message.setToUserId(pipelineOwners.getOrDefault(run.getOwner(), new PipelineUser()).getId());
        }
        message.setCopyUserIds(ccUsers);
        message.setTemplate(new NotificationTemplate(settings.getTemplateId()));
        message.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold()));
        return message;
    }

    private List<PipelineRun> createNotificationsForLongPausedRuns(final List<PipelineRun> pausedRuns,
                                                                   final NotificationType notificationType) {
        final NotificationSettings settings = settingsManager.load(notificationType);

        if (settings == null || !settings.isEnabled() || settings.getTemplateId() == 0) {
            log.info("No template configured for long paused status notifications or it was disabled!");
            return Collections.emptyList();
        }

        final LocalDateTime now = DateUtils.nowUTC();
        final Long threshold = settings.getThreshold();
        if (threshold == null || threshold <= 0) {
            log.debug("Threshold is not specified for notification type '{}'", notificationType.name());
            return Collections.emptyList();
        }

        final String instanceTypesToExclude = preferenceManager.getPreference(SystemPreferences
                .SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES);
        final Map<String, NotificationFilter> runParametersFilters = parseRunExcludeParams();

        final List<Long> ccUsers = getCCUsers(settings);
        final List<PipelineRun> filtered = pausedRuns.stream()
                .filter(run -> noneMatchExcludedInstanceType(run, instanceTypesToExclude))
                .filter(run -> !matchExcludeRunParameters(run, runParametersFilters))
                .filter(run -> isRunStuckInStatus(settings, now, threshold, run))
                .collect(Collectors.toList());
        final Map<String, PipelineUser> pipelineOwners = getPipelinesOwnersFromRuns(filtered);
        final List<NotificationMessage> messages = filtered.stream()
                .map(run -> buildMessageForLongPausedRun(run, ccUsers, settings, pipelineOwners))
                .collect(Collectors.toList());
        saveNotifications(messages);
        return filtered;
    }

    private boolean noneMatchExcludedInstanceType(final PipelineRun run, final String instanceTypesToExclude) {
        if (StringUtils.isBlank(instanceTypesToExclude)) {
            return true;
        }
        final RunInstance instance = run.getInstance();
        if (Objects.isNull(instance)) {
            log.debug("Cannot get instance info for run '{}'", run.getId());
            return true;
        }
        final String nodeType = instance.getNodeType();
        if (StringUtils.isBlank(nodeType)) {
            log.debug("Cannot get node type for run '{}'", run.getId());
            return true;
        }
        return Arrays.stream(instanceTypesToExclude.split(","))
                .noneMatch(pattern -> matcher.match(pattern, nodeType));
    }

    private NotificationMessage buildMessageForIdleRun(final NotificationSettings idleRunSettings,
                                                       final List<Long> ccUserIds,
                                                       final Map<String, PipelineUser> pipelineOwners,
                                                       final double idleCpuLevel,
                                                       final Pair<PipelineRun, Double> pair) {
        log.debug("Sending idle run notification for run '{}'.", pair.getLeft().getId());
        final NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(idleRunSettings.getTemplateId()));
        message.setTemplateParameters(PipelineRunMapper.map(pair.getLeft()));
        message.getTemplateParameters().put("idleCpuLevel", idleCpuLevel);
        message.getTemplateParameters().put("cpuRate", pair.getRight() * PERCENT);
        if (idleRunSettings.isKeepInformedOwner()) {
            message.setToUserId(pipelineOwners.getOrDefault(pair.getLeft().getOwner(), new PipelineUser()).getId());
        }
        message.setCopyUserIds(ccUserIds);
        return message;
    }

    private boolean shouldNotifyIdleRun(final Long runId, final NotificationType notificationType,
                                        final NotificationSettings notificationSettings) {
        if (!NotificationType.IDLE_RUN.equals(notificationType)) {
            return true;
        }
        return shouldNotify(runId, notificationSettings);
    }

    private Map<String, Object> buildUsersTemplateArguments(final List<PipelineUser> pipelineUsers,
                                                            final Map<Long, String> userStorages) {
        final Map<String, Object> templateArguments = new HashMap<>();

        if (!CollectionUtils.isEmpty(pipelineUsers)) {
            templateArguments.put("pipelineUsers", pipelineUsers.stream()
                    .map(user -> buildUserTemplateArguments(user, userStorages))
                    .collect(Collectors.toList()));
        }

        return templateArguments;
    }

    private Map<String, Object> buildUserTemplateArguments(final PipelineUser user,
                                                           final Map<Long, String> userStorages) {
        final Map<String, Object> userArguments = new HashMap<>();
        userArguments.put("name", user.getUserName());
        userArguments.put("email", user.getEmail());
        if (Objects.nonNull(user.getDefaultStorageId())) {
            userArguments.put("storage_name", userStorages.get(user.getDefaultStorageId()));
        }
        userArguments.put("registration_date", user.getRegistrationDate());
        if (Objects.nonNull(user.getLastLoginDate())) {
            userArguments.put("last_login_date", user.getLastLoginDate());
        }
        if (Objects.nonNull(user.getBlockDate())) {
            userArguments.put("block_date", user.getBlockDate());
        }
        return userArguments;
    }

    private Map<String, NotificationFilter> parseRunExcludeParams() {
        final Map<String, NotificationFilter> excludeParams = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_NOTIFICATIONS_EXCLUDE_PARAMS);

        if (CollectionUtils.isEmpty(excludeParams)) {
            return Collections.emptyMap();
        }

        return excludeParams;
    }

    private boolean matchExcludeRunParameters(final PipelineRun run,
                                              final Map<String, NotificationFilter> filters) {
        if (CollectionUtils.isEmpty(filters) || CollectionUtils.isEmpty(run.getPipelineRunParameters())) {
            return false;
        }

        return run.getPipelineRunParameters().stream()
                .filter(parameter -> filters.containsKey(parameter.getName()))
                .anyMatch(parameter -> matchFilter(filters.get(parameter.getName()), parameter.getValue()));
    }

    private boolean matchFilter(final NotificationFilter filter, final String currentValue) {
        switch (filter.getOperator()) {
            case EQUAL:
                return Objects.equals(currentValue, filter.getValue());
            case NOT_EQUAL:
                return !Objects.equals(currentValue, filter.getValue());
            default:
                return false;
        }
    }

    private NotificationMessage buildMessageForFullNodePool(final List<NodePool> nodePools,
                                                            final NotificationSettings settings,
                                                            final List<Long> recipients) {
        final NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
        notificationMessage.setTemplateParameters(buildNodePoolsTemplate(nodePools));
        notificationMessage.setCopyUserIds(recipients);
        return notificationMessage;
    }

    private Map<String, Object> buildNodePoolsTemplate(final List<NodePool> nodePools) {
        return Collections.singletonMap("pools", nodePools.stream()
                .map(this::buildNodePoolTemplate)
                .collect(Collectors.toList()));
    }

    private Map<String, Object> buildNodePoolTemplate(final NodePool nodePool) {
        return jsonMapper.convertValue(nodePool, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> buildBillingQuotaParams(final AppliedQuota appliedQuota) {
        final Quota quota = appliedQuota.getQuota();
        final QuotaAction action = appliedQuota.getAction();
        final Map<String, Object> templateParameters = new HashMap<>();
        templateParameters.put("expense", appliedQuota.getExpense());
        templateParameters.put("from", appliedQuota.getFrom());
        templateParameters.put("to", appliedQuota.getTo());
        templateParameters.put("group", quota.getQuotaGroup());
        templateParameters.put("quota", quota.getValue());
        templateParameters.put("type", quota.getType());
        templateParameters.put("subject", quota.getSubject());
        templateParameters.put("actions", action.getActions()
                .stream()
                .map(Enum::name)
                .collect(Collectors.joining(",")));
        templateParameters.put("threshold", action.getThreshold());
        return templateParameters;
    }

    private NotificationSettings getNotificationSettings(final NotificationType type) {
        final NotificationSettings settings =
                settingsManager.load(type);
        if (settings == null || !settings.isEnabled() || settings.getTemplateId() == 0) {
            log.info("No template configured for {} notification or it was disabled!", type);
            return null;
        }
        return settings;
    }
}
