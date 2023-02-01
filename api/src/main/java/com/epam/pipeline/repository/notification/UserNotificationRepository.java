/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.notification;

import com.epam.pipeline.entity.notification.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.time.LocalDateTime;

public interface UserNotificationRepository extends PagingAndSortingRepository<UserNotification, Long> {
    Page<UserNotification> findByUserIdAndIsReadOrderByCreatedDateDesc(Long userId, boolean isRead, Pageable pageable);
    Page<UserNotification> findByUserIdOrderByCreatedDateDesc(Long userId, Pageable pageable);
    void deleteByCreatedDateLessThan(LocalDateTime date);
    void deleteByUserId(Long userId);

    @Modifying
    @Query("update UserNotification set isRead = true, readDate = ?2 where userId = ?1")
    void readAll(Long userId, LocalDateTime readDate);
}
