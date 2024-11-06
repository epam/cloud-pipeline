/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.controller.vo.user.RunnerSidVO;
import com.epam.pipeline.dto.user.OnlineUsers;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.ImpersonationStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.RunnerSid;
import com.epam.pipeline.manager.quota.RunLimitsService;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.manager.user.OnlineUsersService;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.user.UserRunnersManager;
import com.epam.pipeline.manager.user.UsersFileImportManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;
import static com.epam.pipeline.security.acl.AclExpressions.OR_USER_READER;
import static com.epam.pipeline.security.acl.AclExpressions.USER_READ_FILTER;
import static com.epam.pipeline.security.acl.AclExpressions.USER_READ_PERMISSION;
import static com.epam.pipeline.security.acl.AclExpressions.OR;

@Service
public class UserApiService {

    @Autowired
    private UserManager userManager;

    @Autowired
    private UsersFileImportManager usersFileImportManager;

    @Autowired
    private UserRunnersManager userRunnersManager;

    @Autowired
    private OnlineUsersService onlineUsersService;

    @Autowired
    private RunLimitsService runLimitsService;

    /**
     * Registers a new user
     * @param userVO specifies user to create
     * @return created user
     */
    @PreAuthorize(ADMIN_ONLY)
    @AclMask
    public PipelineUser createUser(PipelineUserVO userVO) {
        return userManager.create(userVO);
    }

    /**
     * Updates existing user, currently only defaultStorageId is supported for update
     * @param id
     * @param userVO
     * @return
     */
    @PreAuthorize(ADMIN_ONLY)
    @AclMask
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

    @PreAuthorize(ADMIN_ONLY + OR_USER_READER + OR + USER_READ_PERMISSION)
    @AclMask
    public PipelineUser loadUser(Long id, final boolean quotas) {
        return userManager.load(id, quotas);
    }

    @PostAuthorize(ADMIN_ONLY + OR_USER_READER + OR + "hasPermission(returnObject, 'READ')")
    @AclMask
    public PipelineUser loadUserByName(final String name) {
        return userManager.loadByNameOrId(name);
    }

    @PreAuthorize(ADMIN_ONLY)
    public void deleteUser(Long id) {
        userManager.delete(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public PipelineUser updateUserRoles(Long id, List<Long> roles) {
        return userManager.updateUser(id, roles);
    }

    @PostFilter(USER_READ_FILTER)
    @AclMaskList
    public List<PipelineUser> loadUsers(final boolean loadQuotas) {
        return new ArrayList<>(userManager.loadAllUsers(loadQuotas));
    }

    @PostFilter(USER_READ_FILTER)
    @AclMaskList
    public List<PipelineUser> loadUsersWithActivityStatus(final boolean loadQuotas) {
        return new ArrayList<>(userManager.loadUsersWithActivityStatus(loadQuotas));
    }

    //TODO
    @PreAuthorize(ADMIN_OR_GENERAL_USER + OR_USER_READER)
    public List<UserInfo> loadUsersInfo(final List<String> userNames) {
        return userManager.loadUsersInfo(userNames);
    }

    public PipelineUser getCurrentUser() {
        return userManager.getCurrentUser();
    }

    /**
     * Loads a {@code List} of {@code PipelineUser} that are the members of a specified by group
     * @param group a user group name
     * @return a loaded {@code List} of {@code PipelineUser} from the database that satisfy the group name
     */
    @PostFilter(USER_READ_FILTER)
    @AclMaskList
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
    @PostFilter(USER_READ_FILTER)
    @AclMaskList
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

    /**
     * Adds runners to user (service account).
     * @param id the user ID
     * @param runners the list of the {@link RunnerSid} objects which correspond to users/roles that may
     *                launch pipelines as specified user
     * @return the list of the runners
     */
    @PreAuthorize(ADMIN_ONLY)
    public List<RunnerSidVO> updateRunners(final Long id, final List<RunnerSidVO> runners) {
        return userRunnersManager.saveRunners(id, runners);
    }

    /**
     * Returns runners for specified user
     * @param id the user ID
     * @return the list of the runners
     */
    @PreAuthorize(ADMIN_ONLY + OR_USER_READER)
    public List<RunnerSid> getRunners(final Long id) {
        return userRunnersManager.getRunners(id);
    }

    public ImpersonationStatus getImpersonationStatus() {
        return userManager.getImpersonationStatus();
    }

    @PreAuthorize(ADMIN_ONLY)
    public OnlineUsers saveCurrentlyOnlineUsers() {
        return onlineUsersService.saveCurrentlyOnlineUsers();
    }

    @PreAuthorize(ADMIN_ONLY)
    public boolean deleteExpiredOnlineUsers(final LocalDate date) {
        return onlineUsersService.deleteExpired(date);
    }

    public Map<String, Integer> getCurrentUserLaunchLimits(final boolean loadAll) {
        return runLimitsService.getCurrentUserLaunchLimits(loadAll);
    }
}
