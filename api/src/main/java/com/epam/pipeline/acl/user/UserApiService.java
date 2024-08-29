/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.user;

import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UsersFileImportManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;
import static com.epam.pipeline.security.acl.AclExpressions.OR_USER_READER;

@Service
public class UserApiService {

    @Autowired
    private UserManager userManager;

    @Autowired
    private UsersFileImportManager usersFileImportManager;

    /**
     * Registers a new user
     * @param userVO specifies user to create
     * @return created user
     */
    @PreAuthorize(ADMIN_ONLY)
    public PipelineUser createUser(PipelineUserVO userVO) {
        return userManager.createUser(userVO);
    }

    /**
     * Updates existing user, currently only defaultStorageId is supported for update
     * @param id
     * @param userVO
     * @return
     */
    @PreAuthorize(ADMIN_ONLY)
    public PipelineUser updateUser(final Long id, final PipelineUserVO userVO) {
        return userManager.updateUser(id, userVO);
    }

    /**
     * Updates `blocked` field of given user
     * @param id id of the user to be updated
     * @param blockStatus boolean condition of user (true - blocked, false - not blocked)
     * @return updated user
     */
    @PreAuthorize(ADMIN_ONLY)
    public PipelineUser updateUserBlockingStatus(final Long id, final boolean blockStatus) {
        return userManager.updateUserBlockingStatus(id, blockStatus);
    }

    /**
     * Updates `blocked` field of a given group or creates a new group status if no such exists
     * @param groupName name of the group to be updated
     * @param blockStatus boolean condition of group (true - blocked, false - not blocked)
     * @return created/updated groupStatus
     */
    @PreAuthorize(ADMIN_ONLY)
    public GroupStatus upsertGroupBlockingStatus(final String groupName, final boolean blockStatus) {
        return userManager.upsertGroupBlockingStatus(groupName, blockStatus);
    }

    @PreAuthorize(ADMIN_ONLY)
    public GroupStatus deleteGroupBlockingStatus(final String groupName) {
        return userManager.deleteGroupBlockingStatus(groupName);
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public List<GroupStatus> loadAllGroupsBlockingStatuses() {
        return userManager.loadAllGroupsBlockingStatuses();
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public PipelineUser loadUser(Long id) {
        return userManager.loadUserById(id);
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public PipelineUser loadUserByName(final String name) {
        return userManager.loadUserByName(name);
    }

    @PreAuthorize(ADMIN_ONLY)
    public void deleteUser(Long id) {
        userManager.deleteUser(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public PipelineUser updateUserRoles(Long id, List<Long> roles) {
        return userManager.updateUser(id, roles);
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public List<PipelineUser> loadUsers() {
        return new ArrayList<>(userManager.loadAllUsers());
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public List<PipelineUser> loadUsersWithActivityStatus() {
        return new ArrayList<>(userManager.loadUsersWithActivityStatus());
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER + OR_USER_READER)
    public List<UserInfo> loadUsersInfo() {
        return userManager.loadUsersInfo();
    }

    public PipelineUser getCurrentUser() {
        return userManager.getCurrentUser();
    }

    /**
     * Loads a {@code List} of {@code PipelineUser} that are the members of a specified by group
     * @param group a user group name
     * @return a loaded {@code List} of {@code PipelineUser} from the database that satisfy the group name
     */
    public List<PipelineUser> loadUsersByGroup(String group) {
        return new ArrayList<>(userManager.loadUsersByGroup(group));
    }

    /**
     * Checks whether a specific user is a member of a specific group
     * @param userName a name of user
     * @param group a user group name
     * @return true if a specific user is a member of a specific group
     */
    public boolean checkUserByGroup(String userName, String group) {
        return userManager.checkUserByGroup(userName, group);
    }

    /**
     * Searches for a user group name by a prefix
     * @param prefix a prefix of a group name to search
     * @return a loaded {@code List} of group name that satisfy the prefix
     */
    public List<String> findGroups(String prefix) {
        return userManager.findGroups(prefix);
    }

    /**
     * Returns a set of per user customized UI controls, e.g. button labels. Controls are
     * resolves according to user granted authorities (roles, groups, username).
     * @return
     */
    public List<CustomControl> getUserControls() {
        return userManager.getUserControls();
    }

    /**
     * Searches for a users by a prefix
     * @param prefix a prefix of a user name to search
     * @return a loaded {@code List} of {@link PipelineUser} that satisfy the prefix
     */
    public List<PipelineUser> findUsers(String prefix) {
        return userManager.findUsers(prefix);
    }

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public byte[] exportUsers(final PipelineUserExportVO attr) {
        return userManager.exportUsers(attr);
    }

    /**
     * Generates a JWT token for specified user
     * @param userName the name of the user
     * @param expiration token expiration time (seconds)
     * @return generated token
     */
    @PreAuthorize(ADMIN_ONLY)
    public JwtRawToken issueToken(final String userName, final Long expiration) {
        return userManager.issueToken(userName, expiration);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<PipelineUserEvent> importUsersFromCsv(final boolean createUser, final boolean createGroup,
                                                      final List<String> systemDictionariesToCreate,
                                                      final MultipartFile file) {
        return usersFileImportManager.importUsersFromFile(createUser, createGroup, systemDictionariesToCreate, file);
    }
}
