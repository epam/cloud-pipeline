/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.service.user;

import com.epam.pipeline.entity.user.OnlineUsersEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.monitor.repo.user.OnlineUsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineUsersService {
    private final OnlineUsersRepository onlineUsersRepository;

    @Transactional
    public void save(final List<Long> userIds) {
        final OnlineUsersEntity entity = new OnlineUsersEntity();
        entity.setLogDate(DateUtils.nowUTC());
        entity.setUserIds(userIds);

        onlineUsersRepository.save(entity);
    }

    @Transactional
    public void deleteExpired(final LocalDateTime date) {
        onlineUsersRepository.deleteAllByLogDateLessThan(date);
    }
}
