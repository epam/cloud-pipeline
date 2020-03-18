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

package com.epam.pipeline.security.saml;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SAMLUserDetailsServiceImpl implements SAMLUserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLUserDetailsServiceImpl.class);
    private static final String ATTRIBUTES_DELIMITER = "=";

    @Value("${saml.authorities.attribute.names: null}")
    private List<String> authorities;

    @Value("#{'${saml.user.attributes}'.split(',')}")
    private Set<String> samlAttributes;

    @Value("${saml.user.auto.create: EXPLICIT}")
    private SamlUserRegisterStrategy autoCreateUsers;

    @Value("${saml.user.blocked.attribute: }")
    private String blockedAttribute;

    @Value("${saml.user.blocked.attribute.true.val: true}")
    private String blockedAttributeTrueValue;

    @Autowired
    private UserManager userManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Override
    public UserContext loadUserBySAML(SAMLCredential credential) {
        final String userName = credential.getNameID().getValue().toUpperCase();
        final List<String> groups = readAuthorities(credential);
        final Map<String, String> attributes = readAttributes(credential);
        final UserContext userContext = Optional.ofNullable(userManager.loadUserByName(userName))
            .map(loadedUser -> processRegisteredUser(userName, groups, attributes, loadedUser))
            .orElseGet(() -> processNewUser(userName, groups, attributes));
        validateGroupsBlockingStatus(userContext.getAuthorities(), userName);
        if (hasBlockedStatusAttribute(credential)) {
            userManager.updateUserBlockingStatus(userContext.getUserId(), true);
            throwUserIsBlocked(userName);
        }
        LOGGER.info("Successfully authenticate user: " + userContext.getUsername());
        return userContext;
    }

    private UserContext processNewUser(final String userName, final List<String> groups,
                                       final Map<String, String> attributes) {
        checkAbilityToCreate(userName, groups);
        LOGGER.debug(messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName));
        final List<Long> roles = roleManager.getDefaultRolesIds();
        final PipelineUser createdUser = userManager.createUser(userName,
                                                                roles, groups, attributes, null);
        userManager.updateUserFirstLoginDate(createdUser.getId(), DateUtils.nowUTC());
        LOGGER.debug("Created user {} with groups {}", userName, groups);
        final UserContext userContext = new UserContext(createdUser.getId(), userName);
        userContext.setGroups(createdUser.getGroups());
        userContext.setRoles(createdUser.getRoles());
        return userContext;
    }

    private UserContext processRegisteredUser(final String userName, final List<String> groups,
                                              final Map<String, String> attributes, final PipelineUser loadedUser) {
        LOGGER.debug("Found user by name {}", userName);
        if (loadedUser.isBlocked()) {
            throwUserIsBlocked(userName);
        }
        loadedUser.setUserName(userName);
        final List<Long> roles = loadedUser.getRoles().stream().map(Role::getId).collect(Collectors.toList());
        if (loadedUser.getFirstLoginDate() == null) {
            userManager.updateUserFirstLoginDate(loadedUser.getId(), DateUtils.nowUTC());
        }
        if (userManager.needToUpdateUser(groups, attributes, loadedUser)) {
            final PipelineUser updatedUser =
                userManager.updateUserSAMLInfo(loadedUser.getId(), userName, roles, groups, attributes);
            LOGGER.debug("Updated user groups {} ", groups);
            return new UserContext(updatedUser);
        } else {
            return new UserContext(loadedUser);
        }
    }

    private void validateGroupsBlockingStatus(final List<GrantedAuthority> authorities, final String userName) {
        final List<String> groups = ListUtils.emptyIfNull(authorities)
                .stream()
                .map(GrantedAuthority::getAuthority)
                .distinct()
                .collect(Collectors.toList());
        final boolean isValidGroupList = ListUtils.emptyIfNull(userManager.loadGroupBlockingStatus(groups))
                .stream()
                .noneMatch(GroupStatus::isBlocked);
        if (!isValidGroupList) {
            LOGGER.info("Authentication failed! User {} is blocked due to one of his groups is blocked!", userName);
            throw new LockedException("User is blocked!");
        }
    }

    private void throwUserIsBlocked(final String userName) {
        LOGGER.info("Authentication failed! User {} is blocked!", userName);
        throw new LockedException("User is blocked!");
    }

    private boolean hasBlockedStatusAttribute(final SAMLCredential credential) {
        final String blockingStatus = credential.getAttributeAsString(blockedAttribute);
        return StringUtils.isNotEmpty(blockingStatus)
                && blockingStatus.equalsIgnoreCase(blockedAttributeTrueValue);
    }

    List<String> readAuthorities(SAMLCredential credential) {
        return ListUtils.emptyIfNull(authorities)
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(authName -> getGroupsFromArrayValue(credential, authName))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<String> getGroupsFromArrayValue(final SAMLCredential credential,
                                                 final String authName) {
        final String[] attributeValues = credential.getAttributeAsStringArray(authName);
        if (ArrayUtils.isEmpty(attributeValues)) {
            return Collections.emptyList();
        }
        return Arrays.stream(attributeValues)
                .filter(StringUtils::isNotBlank)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    Map<String, String> readAttributes(SAMLCredential credential) {
        if (CollectionUtils.isEmpty(samlAttributes)) {
            return Collections.emptyMap();
        }
        Map<String, String> parsedAttributes = new HashMap<>();
        for (String attribute : samlAttributes) {
            if (attribute.contains(ATTRIBUTES_DELIMITER)) {
                String[] splittedRecord = attribute.split(ATTRIBUTES_DELIMITER);
                String key = splittedRecord[0];
                String value = splittedRecord[1];
                if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                    LOGGER.error("Can not parse saml user attributes property.");
                    continue;
                }
                String attributeValues = credential.getAttributeAsString(value);
                if (StringUtils.isNotEmpty(attributeValues)) {
                    parsedAttributes.put(key, attributeValues);
                }
            }
        }
        return parsedAttributes;
    }

    private void checkAbilityToCreate(final String userName, final List<String> groups) {
        switch (autoCreateUsers) {
            case EXPLICIT:
                log.error(messageHelper.getMessage(MessageConstants.ERROR_USER_NOT_REGISTERED_EXPLICITLY, userName));
                throw new UsernameNotFoundException(
                        messageHelper.getMessage(MessageConstants.ERROR_USER_NOT_REGISTERED_EXPLICITLY, userName));
            case EXPLICIT_GROUP:
                if (!permissionManager.isGroupRegistered(groups)) {
                    log.error(messageHelper.getMessage(MessageConstants.ERROR_USER_NOT_REGISTERED_GROUP_EXPLICITLY,
                            userName));
                    throw new UsernameNotFoundException(
                            messageHelper.getMessage(MessageConstants.ERROR_USER_NOT_REGISTERED_GROUP_EXPLICITLY,
                                    String.join(", ", groups), userName));
                }
                break;
            default:
                break;
        }
    }
}
