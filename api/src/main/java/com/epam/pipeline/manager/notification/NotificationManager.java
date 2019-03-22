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

package com.epam.pipeline.manager.notification;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.epam.pipeline.entity.user.ExtendedRole;
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
     * @param settings defines, if a long initialization or long running message template should be used
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyLongRunningTask(PipelineRun run, NotificationSettings settings) {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.INFO_NOTIFICATION_SUBMITTED, run.getPodId()));

        NotificationMessage notificationMessage = new NotificationMessage();

        if (settings.isKeepInformedOwner()) {
            PipelineUser pipelineOwner = userManager.loadUserByName(run.getOwner());
            notificationMessage.setToUserId(pipelineOwner.getId());
        }

        List<Long> ccUserIds = getKeepInformedUserIds(settings);

        if (settings.isKeepInformedAdmins()) {
            ExtendedRole extendedRole = roleManager.loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId());
            ccUserIds.addAll(extendedRole.getUsers().stream()
                                 .map(PipelineUser::getId)
                                 .collect(Collectors.toList()));
        }

        notificationMessage.setCopyUserIds(ccUserIds);

        notificationMessage.setTemplate(new NotificationTemplate(settings.getTemplateId()));
        if (notificationMessage.getTemplate() == null) {
            LOGGER.error(messageHelper.getMessage(MessageConstants.ERROR_NOTIFICATION_NOT_FOUND,
                    settings.getTemplateId()));
        }

        notificationMessage.setTemplateParameters(PipelineRunMapper.map(run, settings.getThreshold()));
        monitoringNotificationDao.createMonitoringNotification(notificationMessage);
    }

    private List<Long> getKeepInformedUserIds(NotificationSettings settings) {
        if (CollectionUtils.isEmpty(settings.getInformedUserIds())) {
            return new ArrayList<>();
        } else {
            return settings.getInformedUserIds();
        }
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

    private List<Long> getMentionedUsers(String text) {
        Matcher matcher = MENTION_PATTERN.matcher(text);

        List<String> userNames = new ArrayList<>(matcher.groupCount());
        while (matcher.find()) {
            userNames.add(matcher.group(1));
        }

        return userManager.loadUsersByNames(userNames).stream()
            .map(PipelineUser::getId)
            .collect(Collectors.toList());

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

        NotificationMessage message = new NotificationMessage();
        message.setTemplate(new NotificationTemplate(runStatusSettings.getTemplateId()));
        message.setTemplateParameters(PipelineRunMapper.map(pipelineRun, null));

        List<Long> ccUserIds = getKeepInformedUserIds(runStatusSettings);

        if (runStatusSettings.isKeepInformedAdmins()) {
            ExtendedRole extendedRole = roleManager.loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId());
            ccUserIds.addAll(extendedRole.getUsers().stream()
                                 .map(PipelineUser::getId)
                                 .collect(Collectors.toList()));
        }

        message.setCopyUserIds(ccUserIds);

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

        Assert.isTrue(NotificationSettings.NotificationGroup.IDLE_RUN == notificationType.getGroup(),
                      "Only IDLE_RUN group notification types are allowed");

        NotificationSettings idleRunSettings = notificationSettingsManager.load(notificationType);
        if (idleRunSettings == null || !idleRunSettings.isEnabled()) {
            LOGGER.info("No template configured for idle pipeline run notifications or it was disabled!");
            return;
        }

        List<Long> ccUserIds = getKeepInformedUserIds(idleRunSettings);
        if (idleRunSettings.isKeepInformedAdmins()) {
            ExtendedRole extendedRole = roleManager.loadRoleWithUsers(DefaultRoles.ROLE_ADMIN.getId());
            ccUserIds.addAll(extendedRole.getUsers().stream()
                                 .map(PipelineUser::getId)
                                 .collect(Collectors.toList()));
        }

        double idleCpuLevel = preferenceManager.getPreference(SystemPreferences.SYSTEM_IDLE_CPU_THRESHOLD_PERCENT);

        Map<String, PipelineUser> pipelineOwners = userManager.loadUsersByNames(pipelineCpuRatePairs.stream()
                                                                            .map(p -> p.getLeft().getOwner())
                                                                            .collect(Collectors.toList())).stream()
            .collect(Collectors.toMap(PipelineUser::getUserName, user -> user));

        List<NotificationMessage> messages = pipelineCpuRatePairs.stream().map(pair -> {
            NotificationMessage message = new NotificationMessage();
            message.setTemplate(new NotificationTemplate(idleRunSettings.getTemplateId()));
            message.setTemplateParameters(PipelineRunMapper.map(pair.getLeft(), null));
            message.getTemplateParameters().put("idleCpuLevel", idleCpuLevel);
            message.getTemplateParameters().put("cpuRate", pair.getRight() * PERCENT);

            message.setToUserId(pipelineOwners.getOrDefault(pair.getLeft().getOwner(), new PipelineUser()).getId());
            message.setCopyUserIds(ccUserIds);
            return message;
        })
            .collect(Collectors.toList());

        monitoringNotificationDao.createMonitoringNotifications(messages);
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

    private NotificationMessage toMessage(final NotificationMessageVO messageVO) {
        final NotificationMessage message = new NotificationMessage();
        message.setSubject(messageVO.getSubject());
        message.setBody(messageVO.getBody());
        message.setTemplateParameters(messageVO.getParameters());
        final List<Long> copyUserIds = Optional.ofNullable(messageVO.getCopyUsers())
                .orElseGet(Collections::emptyList)
                .stream()
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

}
