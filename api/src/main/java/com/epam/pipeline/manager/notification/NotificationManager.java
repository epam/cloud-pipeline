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

package com.epam.pipeline.manager.notification;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationGroup;
import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import com.epam.pipeline.entity.notification.NotificationTimestamp;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.notification.NotificationTemplate;
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
public class NotificationManager { // TODO: rewrite with Strategy pattern?
    private static final double PERCENT = 100.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationManager.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([^ ]*\\b)");

    @Autowired
    private UserManager userManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private NotificationSettingsManager notificationSettingsManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PreferenceManager preferenceManager;

    /**
     * Internal method for creating notification message that selecting appropriate email template from db,
     * serialize PipelineRun to key-value object and save it to notification_queue table.
     * @param run
     * @param duration Running duration of a run in seconds.
     * @param settings defines, if a long initialization or long running message template should be used
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyLongRunningTask(PipelineRun run, Long duration, NotificationSettings settings) {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_SUBMITTED, run.getPodId()));

        NotificationMessage notificationMessage = new NotificationMessage();

        if (settings.isKeepInformedOwner()) {
            PipelineUser pipelineOwner = userManager.loadUserByName(run.getOwner());
            notificationMessage.setToUserId(pipelineOwner.getId());
        }

        notificationMessage.setCopyUserIds(getCCUsers(settings));

        notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
        if (notificationMessage.getTemplate() == null) {
            LOGGER.error(messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                    settings.getTemplateId()));
        }

        notificationMessage.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold(), duration));
        monitoringNotificationDao.createMonitoringNotification(notificationMessage);
    }

    /**
     * Notify users that a new issue was created for a given entity. Will notify mentioned users as well.
     *
     * @param issue an issue to notify about
     * @param entity an entity for wich issue was created
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIssue(Issue issue, AbstractSecuredEntity entity, String htmlText) {
        NotificationSettings newIssueSettings = notificationSettingsManager.load(NotificationType.NEW_ISSUE);
        if (newIssueSettings == null || !newIssueSettings.isEnabled()) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_TEMPLATE_NOT_CONFIGURED,
                            "new issue"));
            return;
        }

        NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(newIssueSettings.getTemplateId()));
        message.setCopyUserIds(getMentionedUsers(issue.getText()));

        Issue copyWithHtml = issue.toBuilder().text(htmlText).build();
        message.setTemplateParameters(jsonMapper.convertValue(copyWithHtml,
                                                              new TypeReference<Map<String, Object>>() {}));

        if (newIssueSettings.isKeepInformedOwner()) {
            PipelineUser owner = userManager.loadUserByName(entity.getOwner());
            message.setToUserId(owner.getId());
        }

        monitoringNotificationDao.createMonitoringNotification(message);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIssueComment(IssueComment comment, Issue issue, String htmlText) {
        NotificationSettings newIssueCommentSettings = notificationSettingsManager
                .load(NotificationType.NEW_ISSUE_COMMENT);
        if (newIssueCommentSettings == null || !newIssueCommentSettings.isEnabled()) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_TEMPLATE_NOT_CONFIGURED,
                    "new issue"));
            return;
        }

        NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(newIssueCommentSettings.getTemplateId()));

        AbstractSecuredEntity entity = entityManager.load(issue.getEntity().getEntityClass(),
                                                          issue.getEntity().getEntityId());
        List<PipelineUser> referencedUsers = userManager.loadUsersByNames(Arrays.asList(entity.getOwner(),
                                                                                        issue.getAuthor()));
        List<Long> ccUserIds = getMentionedUsers(comment.getText());
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

        IssueComment copyWithHtml = comment.toBuilder().text(htmlText).build();

        Map<String, Object> commentParams = jsonMapper.convertValue(copyWithHtml,
                                                                    new TypeReference<Map<String, Object>>() {});
        commentParams.put("issue", jsonMapper.convertValue(issue, new TypeReference<Map<String, Object>>() {}));
        message.setTemplateParameters(commentParams);

        monitoringNotificationDao.createMonitoringNotification(message);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyRunStatusChanged(PipelineRun pipelineRun) {
        NotificationSettings runStatusSettings = notificationSettingsManager.load(NotificationType.PIPELINE_RUN_STATUS);

        if (runStatusSettings == null || !runStatusSettings.isEnabled()) {
            LOGGER.info("No template configured for pipeline run status changes notifications or it was disabled!");
            return;
        }

        final List<TaskStatus> runStatusesToReport = ListUtils.emptyIfNull(runStatusSettings.getStatusesToInform());
        if (!CollectionUtils.isEmpty(runStatusesToReport) && !runStatusesToReport.contains(pipelineRun.getStatus())) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_RUN_STATUS_NOT_CONFIGURED_FOR_NOTIFICATION,
                    pipelineRun.getStatus(),
                    runStatusesToReport.stream().map(TaskStatus::name).collect(Collectors.joining(", "))));
            return;
        }

        NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(runStatusSettings.getTemplateId()));
        message.setTemplateParameters(PipelineRunMapper.map(pipelineRun, null));

        message.setCopyUserIds(getCCUsers(runStatusSettings));

        if (runStatusSettings.isKeepInformedOwner()) {
            PipelineUser pipelineOwner = userManager.loadUserByName(pipelineRun.getOwner());
            message.setToUserId(pipelineOwner.getId());
        }

        monitoringNotificationDao.createMonitoringNotification(message);
    }

    /**
     * Issues a notification of an idle Pipeline Run for multiple runs.
     *
     * @param pipelineCpuRatePairs a list of pairs of PipelineRun and Double cpu usage rate value
     * @param notificationType a type of notification to be issued. Supported types are IDLE_RUN, IDLE_RUN_PAUSED,
     *                         IDLE_RUN_STOPPED
     * @throws IllegalArgumentException if notificationType is not from IDLE_RUN group
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyIdleRuns(List<Pair<PipelineRun, Double>> pipelineCpuRatePairs,
                               NotificationType notificationType) {
        if (CollectionUtils.isEmpty(pipelineCpuRatePairs)) {
            return;
        }

        Assert.isTrue(NotificationGroup.IDLE_RUN == notificationType.getGroup(),
                      "Only IDLE_RUN group notification types are allowed");

        NotificationSettings idleRunSettings = notificationSettingsManager.load(notificationType);
        if (idleRunSettings == null || !idleRunSettings.isEnabled()) {
            LOGGER.info("No template configured for idle pipeline run notifications or it was disabled!");
            return;
        }

        final List<Long> ccUserIds = getCCUsers(idleRunSettings);


        final Map<String, PipelineUser> pipelineOwners = getPipelinesOwners(pipelineCpuRatePairs);

        final double idleCpuLevel = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT);
        final List<NotificationMessage> messages = pipelineCpuRatePairs.stream().map(pair -> {
            NotificationMessage message = new NotificationMessage();
            message.setTemplate(new NotificationTemplate(idleRunSettings.getTemplateId()));
            message.setTemplateParameters(PipelineRunMapper.map(pair.getLeft(), null));
            message.getTemplateParameters().put("idleCpuLevel", idleCpuLevel);
            message.getTemplateParameters().put("cpuRate", pair.getRight() * PERCENT);
            if (idleRunSettings.isKeepInformedOwner()) {
                message.setToUserId(pipelineOwners.getOrDefault(pair.getLeft().getOwner(), new PipelineUser()).getId());
            }
            message.setCopyUserIds(ccUserIds);
            return message;
        })
            .collect(Collectors.toList());

        monitoringNotificationDao.createMonitoringNotifications(messages);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyHighResourceConsumingRuns(
            final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> pipelinesMetrics,
            final NotificationType notificationType) {
        if (CollectionUtils.isEmpty(pipelinesMetrics)) {
            LOGGER.debug("No pipelines are high loaded, notifications won't be sent!");
            return;
        }

        final NotificationSettings notificationSettings = notificationSettingsManager.load(notificationType);
        if (notificationSettings == null || !notificationSettings.isEnabled()) {
            LOGGER.info("No template configured for high consuming pipeline run notifications or it was disabled!");
            return;
        }

        final List<Pair<PipelineRun, Map<ELKUsageMetric, Double>>> filtered = pipelinesMetrics.stream()
                .filter(run -> shouldNotify(run.getLeft().getId(), notificationSettings))
                .collect(Collectors.toList());

        LOGGER.debug("High resource consuming notifications for pipelines: " +
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
            message.setTemplateParameters(PipelineRunMapper.map(pair.getLeft(), null));
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
        monitoringNotificationDao.createMonitoringNotifications(messages);
        monitoringNotificationDao.updateNotificationTimestamp(runIds, notificationType);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyStuckInStatusRuns(final List<PipelineRun> runs) {
        final NotificationSettings settings = notificationSettingsManager.load(NotificationType.LONG_STATUS);

        if (settings == null || !settings.isEnabled() || settings.getTemplateId() == 0) {
            LOGGER.info("No template configured for stuck status notifications or it was disabled!");
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
                    LOGGER.debug("Sending stuck status {} notification for run {}.",
                            run.getStatus(), run.getId());
                    final NotificationMessage notificationMessage = new NotificationMessage();
                    if (settings.isKeepInformedOwner()) {
                        PipelineUser pipelineOwner = userManager.loadUserByName(run.getOwner());
                        notificationMessage.setToUserId(pipelineOwner.getId());
                    }
                    notificationMessage.setCopyUserIds(getCCUsers(settings));
                    notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
                    notificationMessage.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold()));
                    monitoringNotificationDao.createMonitoringNotification(notificationMessage);
                });
    }

    /**
     * Creates notifications for long paused runs.
     * @param pausedRuns the list of the {@link PipelineRun} objects that in paused state
     */
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
    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRun> notifyLongPausedRunsBeforeStop(final List<PipelineRun> pausedRuns) {
        return createNotificationsForLongPausedRuns(pausedRuns, NotificationType.LONG_PAUSED_STOPPED);
    }

    private boolean isRunStuckInStatus(final NotificationSettings settings,
                                       final LocalDateTime now,
                                       final Long threshold,
                                       final PipelineRun run) {
        final List<RunStatus> runStatuses = run.getRunStatuses();
        if (CollectionUtils.isEmpty(runStatuses)) {
            LOGGER.debug("Status timestamps are not available for run {}. " +
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
                    LOGGER.debug("Failed to find status {} timestamp for run {}.", run.getStatus(), run.getId());
                    return false;
                });
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
        monitoringNotificationDao.createMonitoringNotification(message);

        return message;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void removeNotificationTimestamps(final Long runId) {
        monitoringNotificationDao.deleteNotificationTimestampsForRun(runId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void removeNotificationTimestampsByPipelineId(final Long id) {
        monitoringNotificationDao.deleteNotificationTimestampsForPipeline(id);
    }

    public Optional<NotificationTimestamp> loadLastNotificationTimestamp(final Long runId,
                                                                         final NotificationType type) {
        return monitoringNotificationDao.loadNotificationTimestamp(runId, type);
    }

    private boolean shouldNotify(final Long runId, final NotificationSettings notificationSettings) {
        final Long resendDelay = notificationSettings.getResendDelay();
        final Optional<NotificationTimestamp> notificationTimestamp = loadLastNotificationTimestamp(
                runId,
                notificationSettings.getType());

        return notificationTimestamp
                .map(timestamp -> NotificationTimestamp.isTimeoutEnds(timestamp, resendDelay,  ChronoUnit.SECONDS))
                .orElse(true);
    }

    private <T> Map<String, PipelineUser> getPipelinesOwners(List<Pair<PipelineRun, T>> pipelineCpuRatePairs) {
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
        LOGGER.debug("Sending long paused run notification for run {}.", run.getId());
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
        final NotificationSettings settings = notificationSettingsManager.load(notificationType);

        if (settings == null || !settings.isEnabled() || settings.getTemplateId() == 0) {
            LOGGER.info("No template configured for long paused status notifications or it was disabled!");
            return Collections.emptyList();
        }

        final LocalDateTime now = DateUtils.nowUTC();
        final Long threshold = settings.getThreshold();
        if (threshold == null || threshold <= 0) {
            LOGGER.debug("Threshold is not specified for notification type '{}'", notificationType.name());
            return Collections.emptyList();
        }

        final List<Long> ccUsers = getCCUsers(settings);
        final List<PipelineRun> filtered = pausedRuns.stream()
                .filter(run -> isRunStuckInStatus(settings, now, threshold, run))
                .collect(Collectors.toList());
        final Map<String, PipelineUser> pipelineOwners = getPipelinesOwnersFromRuns(filtered);
        final List<NotificationMessage> messages = filtered.stream()
                .map(run -> buildMessageForLongPausedRun(run, ccUsers, settings, pipelineOwners))
                .collect(Collectors.toList());
        monitoringNotificationDao.createMonitoringNotifications(messages);

        return filtered;
    }
}
