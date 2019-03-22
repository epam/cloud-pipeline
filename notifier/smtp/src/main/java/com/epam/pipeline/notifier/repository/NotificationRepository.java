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

package com.epam.pipeline.notifier.repository;

import com.epam.pipeline.entity.notification.NotificationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * {@link NotificationRepository} provides methods to load, delete {@link NotificationMessage} from database.
 */
public interface NotificationRepository extends JpaRepository<NotificationMessage, Long> {

    /**
     * Load limited number of {@link NotificationMessage} ordered by @{@link NotificationMessage#id}
     * @param pageable   object to limit number of returned messages
     * @return list of {@link NotificationMessage}
     */
    @Query("select n from NotificationMessage n order by n.id")
    List<NotificationMessage> loadNotification(Pageable pageable);

    /**
     * Delete {@link NotificationMessage} by id
     * @param id   id of {@link NotificationMessage} to be deleted
     */
    @Transactional(propagation = Propagation.MANDATORY)
    int deleteById(Long id);
}
