/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security;


import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.user.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserAccessService {

    private final UserManager userManager;
    private final boolean validateUser;

    public UserAccessService(final UserManager userManager,
                             final @Value("${jwt.validate.token.user:false}") boolean validateUser) {
        this.userManager = userManager;
        this.validateUser = validateUser;
    }

    public void validateUserBlockStatus(final String userName) {
        if (!validateUser) {
            return;
        }
        final PipelineUser user = userManager.loadUserByName(userName);
        if (user == null) {
            log.info("Failed to find user by name {}. Access is still allowed.", userName);
            return;
        }
        validateUserBlockStatus(user);
        validateUserGroupsBlockStatus(user);
    }

    public void validateUserBlockStatus(final PipelineUser user) {
        if (user.isBlocked()) {
            throwUserIsBlocked(user.getUserName());
        }
    }

    public void throwUserIsBlocked(final String userName) {
        log.info("Authentication failed! User {} is blocked!", userName);
        throw new LockedException("User is blocked!");
    }

    public void validateUserGroupsBlockStatus(final PipelineUser user) {
        List<GrantedAuthority> authorities = new UserContext(user).getAuthorities();
        final List<String> groups = ListUtils.emptyIfNull(authorities)
                .stream()
                .map(GrantedAuthority::getAuthority)
                .distinct()
                .collect(Collectors.toList());
        final boolean isValidGroupList = ListUtils.emptyIfNull(userManager.loadGroupBlockingStatus(groups))
                .stream()
                .noneMatch(GroupStatus::isBlocked);
        if (!isValidGroupList) {
            log.info("Authentication failed! User {} is blocked due to one of his groups is blocked!",
                    user.getUserName());
            throw new LockedException("User is blocked!");
        }
    }
}
