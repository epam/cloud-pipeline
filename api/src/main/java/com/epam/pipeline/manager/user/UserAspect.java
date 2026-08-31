/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.dao.notification.MonitoringNotificationDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class UserAspect {

    private static final Logger LOG = LoggerFactory.getLogger(UserAspect.class);

    @Autowired
    private MonitoringNotificationDao monitoringNotificationDao;

    @Autowired
    private UserManager userManager;

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private NFSStorageProvider nfsStorageProvider;

    /**
     * Deletes all notification to specific user. This Aspect is used when user is being deleted
     * @param joinPoint
     * @param userId
     */
    @Before(value = "execution(* com.epam.pipeline.manager.user.UserManager.delete(..)) && args(userId)")
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAllUserNotification(final JoinPoint joinPoint, final Long userId) {
        monitoringNotificationDao.deleteNotificationsForUser(userId);
    }

    /**
     * After user default storage is updated via updateUser(id, userVO), chown the NFS mount root folder
     * to userUID:userUID so the user owns their default storage.
     */
    @After("execution(* com.epam.pipeline.manager.user.UserManager.updateUser(..)) && args(id, userVO)")
    public void updateDefaultUserStorage(final JoinPoint joinPoint, final Long id, final PipelineUserVO userVO) {
        if (userVO == null || userVO.getDefaultStorageId() == null) {
            return;
        }
        try {
            final PipelineUser user = userManager.load(id);
            if (user.getDefaultStorageId() == null) {
                LOG.debug("Default NFS storage for user {} not found", user.getUserName());
                return;
            }
            final AbstractDataStorage storage = dataStorageManager.load(user.getDefaultStorageId());
            if (!DataStorageType.NFS.equals(storage.getType())) {
                LOG.debug("Default storage for user {} is not NFS", user.getUserName());
                return;
            }
            nfsStorageProvider.chownUserStorageRoot(user, (NFSDataStorage) storage);
        } catch (Exception e) {
            LOG.warn("Failed to chown NFS default storage root for user id {}: {}", id, e.getMessage());
        }
    }
}
