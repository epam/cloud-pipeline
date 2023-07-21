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

package com.epam.pipeline.security.saml.impersonation;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationManager implements UserDetailsChecker, UserDetailsService {

    private final UserManager userManager;
    private final UserAccessService userAccessService;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    @Override
    @PreAuthorize("@userPermissionsManager.impersonatePermission(#userToImpersonate)")
    public void check(final UserDetails userToImpersonate) {
        Assert.notNull(userToImpersonate, messageHelper.getMessage(MessageConstants.ERROR_IMPERSONATION_EMPTY_USER));
        final String impersonatedName = userToImpersonate.getUsername();
        log.info("Attempt to impersonate user from: " + authManager.getAuthorizedUser() + " to: " + impersonatedName);
        Assert.isTrue(!impersonatedName.equals(authManager.getAuthorizedUser()),
                      messageHelper.getMessage(MessageConstants.ERROR_SELF_IMPERSONATION_NOT_ALLOWED,
                                               impersonatedName));
        final PipelineUser user = userManager.loadUserByName(impersonatedName);
        userAccessService.validateUserBlockStatus(user);
        userAccessService.validateUserGroupsBlockStatus(user);
    }

    @Override
    public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
        return userManager.loadUserContext(username);
    }
}
