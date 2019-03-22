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

package com.epam.pipeline.security;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SecuredEntityDeletionAspect} deletes {@link com.epam.pipeline.entity.metadata.MetadataEntry} and
 * all {@link com.epam.pipeline.entity.issue.Issue}s that belong to {@link AbstractSecuredEntity}.
 */
@Aspect
@Component
public class SecuredEntityDeletionAspect {

    private static final String RETURN_OBJECT = "entity";
    private static final String ROLE = "role";
    private static final String USER = "user";
    private static final Logger LOGGER = LoggerFactory.getLogger(SecuredEntityDeletionAspect.class);

    @Autowired
    private IssueManager issueManager;

    @Autowired
    private MetadataManager metadataManager;

    @AfterReturning(pointcut = "@within(com.epam.pipeline.manager.security.acl.AclSync) && "
            + "execution(* *.delete(..))", returning = RETURN_OBJECT)
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteSecuredEntity(JoinPoint joinPoint, AbstractSecuredEntity entity) {
        EntityVO entityVO = new EntityVO(entity.getId(), entity.getAclClass());
        LOGGER.debug("Deleting issues for Object {} {}", entity.getName(), entity.getClass());
        issueManager.deleteIssuesForEntity(entityVO);
        LOGGER.debug("Deleting metadata for Object {} {}", entity.getName(), entity.getClass());
        metadataManager.deleteMetadata(entityVO);
    }

    @AfterReturning(pointcut = "execution(* *.deleteUser(..))", returning = USER)
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteUser(JoinPoint joinPoint, PipelineUser user) {
        LOGGER.debug("Deleting metadata for Object {} {}", user.getUserName(), AclClass.PIPELINE_USER);
        metadataManager.deleteMetadata(new EntityVO(user.getId(), AclClass.PIPELINE_USER));
    }

    @AfterReturning(pointcut = "execution(* *.deleteRole(..))", returning = ROLE)
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteRole(JoinPoint joinPoint, Role role) {
        LOGGER.debug("Deleting metadata for Object {} {}", role.getName(), AclClass.ROLE);
        metadataManager.deleteMetadata(new EntityVO(role.getId(), AclClass.ROLE));
    }
}
