/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.dto.user.OnlineUsers;
import com.epam.pipeline.entity.user.OnlineUsersEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.user.OnlineUsersMapper;
import com.epam.pipeline.repository.user.OnlineUsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineUsersService {
    private final OnlineUsersRepository onlineUsersRepository;
    private final OnlineUsersMapper onlineUsersMapper;
    private final UserManager userManager;

    @Transactional
    public OnlineUsers saveCurrentlyOnlineUsers() {
        final List<Long> userIds = CollectionUtils.emptyIfNull(userManager.getOnlineUsers()).stream()
                .map(PipelineUser::getId)
                .collect(Collectors.toList());

        final OnlineUsersEntity entity = new OnlineUsersEntity();
        entity.setLogDate(DateUtils.nowUTC());
        entity.setUserIds(userIds);

        return onlineUsersMapper.toDto(onlineUsersRepository.save(entity));
    }

    @Transactional
    public boolean deleteExpired(final LocalDate date) {
        onlineUsersRepository.deleteByLogDateLessThan(date.atStartOfDay());
        return true;
    }

    public List<OnlineUsers> getUsersByPeriod(final LocalDateTime start, final LocalDateTime end,
                                              final Set<Long> filterUsers) {
        final List<OnlineUsersEntity> users = Objects.isNull(filterUsers)
                ? onlineUsersRepository.findByLogDateGreaterThanAndLogDateLessThan(start, end)
                : onlineUsersRepository.findByLogDateGreaterThanAndLogDateLessThanAndUserIdsIn(start, end, filterUsers);
        return users.stream()
                .map(onlineUsersMapper::toDto)
                .collect(Collectors.toList());
    }
}
