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

package com.epam.pipeline.notifier.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.notifier.repository.NotificationRepository;
import com.epam.pipeline.notifier.service.task.NotificationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private ExecutorService notificationThreadPool;

    @Value(value = "${notification.at.time:5}")
    private int notificationAtTime;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private List<NotificationManager> notificationManagers;

    /**
     * Scheduled task to load batch of {@link NotificationMessage} from database
     * and delegate it to all realizations of {@link NotificationManager}
     */
    @Scheduled(fixedDelayString = "${notification.scheduler.delay}")
    @Transactional(propagation = Propagation.REQUIRED)
    public void sendNotification() {
        LOGGER.debug("Start scheduled notification loop...");
        Pageable limit = new PageRequest(0, notificationAtTime);
        List<NotificationMessage> result = notificationRepository.loadNotification(limit);
        result.forEach(message -> {
                notificationRepository.deleteById(message.getId());

                for (NotificationManager notificationManager : notificationManagers) {
                    CompletableFuture.runAsync(
                            () -> notificationManager.notifySubscribers(message),
                            notificationThreadPool)
                            .exceptionally(throwable -> {
                                    LOGGER.warn("Exception while trying to send email", throwable);
                                    return null;
                                });
                }
            });
        LOGGER.debug("End scheduled notification loop...");
    }

}
