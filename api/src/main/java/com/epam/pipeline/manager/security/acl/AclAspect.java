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

package com.epam.pipeline.manager.security.acl;

import java.util.List;
import java.util.Optional;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.SecuredEntityDelegate;
import com.epam.pipeline.entity.filter.AclSecuredFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import org.apache.commons.collections4.ListUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Aspect @Component
public class AclAspect {

    private static final String RETURN_OBJECT = "entity";
    private static final Logger LOGGER = LoggerFactory.getLogger(AclAspect.class);

    @Autowired private JdbcMutableAclServiceImpl aclService;

    @Autowired private GrantPermissionManager permissionManager;


    @AfterReturning(pointcut = "@within(com.epam.pipeline.manager.security.acl.AclSync) && "
            + "execution(* *.create(..))", returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void createAclIdentity(JoinPoint joinPoint, AbstractSecuredEntity entity) {
        if (entity.getOwner().equals(AuthManager.UNAUTHORIZED_USER)) {
            return;
        }
        LOGGER.debug("Creating ACL Object {} {}", entity.getName(), entity.getClass());
        MutableAcl acl = aclService.createAcl(entity);
        if (entity.getParent() != null) {
            updateParent(entity, acl);
        }
        // owner has all permissions for a new object
        entity.setMask(AbstractSecuredEntity.ALL_PERMISSIONS_MASK);
    }

    @AfterReturning(pointcut = "@within(com.epam.pipeline.manager.security.acl.AclSync) && "
            + "execution(* *.update(..))", returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateAclIdentity(JoinPoint joinPoint, AbstractSecuredEntity entity) {
        if (entity.getOwner().equals(AuthManager.UNAUTHORIZED_USER)) {
            return;
        }
        LOGGER.debug("Updating ACL Object {} {} {}", entity.getName(), entity.getClass(), entity.getId());
        MutableAcl acl = aclService.getOrCreateObjectIdentity(entity);
        if (entity.getParent() == null && acl.getParentAcl() == null) {
            return;
        }
        if (entity.getParent() == null) {
            acl.setParent(null);
            aclService.updateAcl(acl);
        } else if (acl.getParentAcl() == null
                || acl.getParentAcl().getObjectIdentity().getIdentifier() != entity.getParent()
                .getId()) {
            updateParent(entity, acl);
        }
        setMask(joinPoint, entity);
    }

    @AfterReturning(pointcut = "@within(com.epam.pipeline.manager.security.acl.AclSync) && "
            + "execution(* *.delete(..))", returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAclIdentity(JoinPoint joinPoint, AbstractSecuredEntity entity) {
        LOGGER.debug("Deleting ACL object for Object {} {}", entity.getName(), entity.getClass());
        aclService.deleteAcl(new ObjectIdentityImpl(entity), false);
        entity.setMask(null);
    }

    @AfterReturning(pointcut = "@annotation(com.epam.pipeline.manager.security.acl.AclMask)",
            returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void setMask(JoinPoint joinPoint, AbstractSecuredEntity entity) {
        entity.setMask(permissionManager.getPermissionsMask(entity, true, true));
    }

    @AfterReturning(pointcut = "@annotation(com.epam.pipeline.manager.security.acl.AclMaskList)",
            returning = "list")
    @Transactional(propagation = Propagation.REQUIRED)
    public void setMaskForList(JoinPoint joinPoint, List<? extends AbstractSecuredEntity> list) {
        list.forEach(entity ->
                entity.setMask(permissionManager.getPermissionsMask(entity, true, true)));
    }

    @AfterReturning(pointcut = "@annotation(com.epam.pipeline.manager.security.acl.AclMaskDelegateList)",
            returning = "list")
    @Transactional(propagation = Propagation.REQUIRED)
    public void setMaskForDelegateList(JoinPoint joinPoint, List<? extends SecuredEntityDelegate> list) {
        ListUtils.emptyIfNull(list).forEach(delegate ->
                Optional.ofNullable(delegate.toDelegate())
                        .ifPresent(entity -> entity.setMask(
                                permissionManager.getPermissionsMask(entity, true, true))));
    }

    @AfterReturning(pointcut = "@annotation(com.epam.pipeline.manager.security.acl.AclMaskPage)",
            returning = "page")
    @Transactional(propagation = Propagation.REQUIRED)
    public void setMaskForPage(JoinPoint joinPoint, PagedResult<List<PipelineRun>> page) {
        page.getElements().forEach(entity ->
                entity.setMask(permissionManager.getPermissionsMask(entity, true, true)));
    }

    @AfterReturning(pointcut = "@annotation(com.epam.pipeline.manager.security.acl.AclTree)",
            returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void filterTree(JoinPoint joinPoint, AbstractHierarchicalEntity entity) {
        permissionManager.filterTree(entity, AclPermission.READ);
    }

    @Before("@annotation(com.epam.pipeline.manager.security.acl.AclFilter) && args(filter,..)")
    public void extendFilter(JoinPoint joinPoint, AclSecuredFilter filter) {
        permissionManager.extendFilter(filter);
    }


    private void updateParent(AbstractSecuredEntity entity, MutableAcl acl) {
        MutableAcl parentAcl = aclService.getOrCreateObjectIdentity(entity.getParent());
        acl.setParent(parentAcl);
        aclService.updateAcl(acl);
    }

}
