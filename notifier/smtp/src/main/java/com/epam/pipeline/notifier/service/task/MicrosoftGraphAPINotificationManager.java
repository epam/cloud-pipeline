/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.notifier.service.task;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.notifier.entity.message.MessageText;
import com.epam.pipeline.notifier.repository.UserRepository;
import com.epam.pipeline.notifier.service.TemplateService;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.EmailValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sends pipeline notifications through Microsoft Graph using the
 * <a href="https://learn.microsoft.com/en-us/graph/api/user-sendmail">sendMail</a> API.
 * <p>
 * The delegated mailbox is {@code notification.azure.sender}. Authentication uses an Azure AD
 * application (client credentials): {@code notification.azure.tenant.id},
 * {@code notification.azure.client.id}, and {@code notification.azure.client.secret}.
 * Scopes default to Graph application permissions (for example {@code https://graph.microsoft.com/.default}).
 * </p>
 * <p>
 * When {@code notification.azure.dry-run} is {@code true}, the Graph {@code sendMail} request is not executed;
 * instead, recipients, subject, and resolved body are written to the application log.
 * </p>
 *
 * @see NotificationManager
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.enable.azure", havingValue = "true")
public class MicrosoftGraphAPINotificationManager implements NotificationManager {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String[] scopes;
    private final String sender;
    private final boolean dryRun;
    private final UserRepository userRepository;
    private final TemplateService templateService;

    /**
     * @param tenantId        Azure AD tenant (directory) ID
     * @param clientId        application (client) ID of the registered app used for Graph
     * @param clientSecret    client secret for the application
     * @param scopes          OAuth scopes (typically {@code https://graph.microsoft.com/.default})
     * @param sender          email of the mailbox that sends mail
     * @param dryRun          when {@code true}, skip {@code sendMail} and only log the composed message
     * @param userRepository  a {@link UserRepository} object
     * @param templateService builds subject and body (Velocity) from the notification message
     */
    public MicrosoftGraphAPINotificationManager(@Value("${notification.azure.tenant.id}") final String tenantId,
                                                @Value("${notification.azure.client.id}") final String clientId,
                                                @Value("${notification.azure.client.secret}") final String clientSecret,
                                                @Value("${notification.azure.scopes}") final String[] scopes,
                                                @Value("${notification.azure.sender}") final String sender,
                                                @Value("${notification.azure.dry-run}") final boolean dryRun,
                                                final UserRepository userRepository,
                                                final TemplateService templateService) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = scopes;
        this.sender = sender;
        this.dryRun = dryRun;
        this.userRepository = userRepository;
        this.templateService = templateService;
    }

    /**
     * Builds a Graph {@link Message}, authenticates with client credentials, and posts {@code sendMail}
     * for {@link #sender}, unless there are no valid recipients or dry-run is enabled.
     *
     * @param message notification row
     */
    @Override
    public void notifySubscribers(final NotificationMessage message) {
        log.info("Trying to send message #{} over Microsoft Graph API", message.getId());
        final Message emailMessage = toEmailMessage(message);
        if (CollectionUtils.isEmpty(emailMessage.getToRecipients())
                && CollectionUtils.isEmpty(emailMessage.getCcRecipients())) {
            log.info("Microsoft Graph mail for message #{} skipped: no valid recipients", message.getId());
            return;
        }

        final ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
        final GraphServiceClient graphClient = new GraphServiceClient(credential, scopes);

        final SendMailPostRequestBody requestBody = new SendMailPostRequestBody();
        requestBody.setMessage(emailMessage);
        requestBody.setSaveToSentItems(false);

        if (dryRun) {
            logDryRunMailPreview(message.getId(), emailMessage);
            return;
        }

        graphClient
                .users()
                .byUserId(sender)
                .sendMail()
                .post(requestBody);
    }

    private Message toEmailMessage(final NotificationMessage message) {
        final MessageText messageText = templateService.buildMessageText(message);

        final Message emailMessage = new Message();
        emailMessage.setSubject(messageText.getSubject());

        final ItemBody body = new ItemBody();
        body.setContent(messageText.getBody());
        body.setContentType(BodyType.Html);
        emailMessage.setBody(body);

        emailMessage.setToRecipients(buildToRecipients(message));
        emailMessage.setCcRecipients(buildCcRecipients(message));

        return emailMessage;
    }

    private List<Recipient> buildToRecipients(final NotificationMessage message) {
        if (message.getToUserId() == null) {
            return Collections.emptyList();
        }
        final PipelineUser targetUser = userRepository.findOne(message.getToUserId());
        if (targetUser == null) {
            log.info("Cannot find user with id {} for message {}", message.getToUserId(), message.getId());
            return Collections.emptyList();
        }
        final String address = targetUser.getEmail();
        if (!isValidEmail(address)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toRecipient(address));
    }

    private List<Recipient> buildCcRecipients(final NotificationMessage message) {
        if (CollectionUtils.isEmpty(message.getCopyUserIds())) {
            return Collections.emptyList();
        }
        return userRepository.findByIdIn(message.getCopyUserIds()).stream()
                .map(PipelineUser::getEmail)
                .filter(this::isValidEmail)
                .map(this::toRecipient)
                .collect(Collectors.toList());
    }

    private Recipient toRecipient(final String emailAddress) {
        final Recipient recipient = new Recipient();
        final EmailAddress email = new EmailAddress();
        email.setAddress(emailAddress);
        recipient.setEmailAddress(email);
        return recipient;
    }

    private boolean isValidEmail(final String email) {
        return EmailValidator.getInstance().isValid(email);
    }

    private void logDryRunMailPreview(final Long messageId, final Message emailMessage) {
        log.info("[DRY-RUN #{}] — To recipients: {}", messageId,
                formatRecipientAddresses(emailMessage.getToRecipients()));
        log.info("[DRY-RUN #{}] — CC recipients: {}", messageId,
                formatRecipientAddresses(emailMessage.getCcRecipients()));
        log.info("[DRY-RUN #{}] — Subject: {}", messageId, emailMessage.getSubject());
        final String resolvedBody = Optional.ofNullable(emailMessage.getBody())
                .map(ItemBody::getContent)
                .orElse("");
        log.info("[DRY-RUN #{}] — Body:\n{}", messageId, resolvedBody);
    }

    private String formatRecipientAddresses(final List<Recipient> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            return "(none)";
        }
        return recipients.stream()
                .map(r -> Optional.ofNullable(r.getEmailAddress()).map(EmailAddress::getAddress).orElse("?"))
                .collect(Collectors.joining(", "));
    }
}
