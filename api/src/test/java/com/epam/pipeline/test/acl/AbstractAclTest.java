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

package com.epam.pipeline.test.acl;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.acls.model.Sid;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Superclass for all ACL layer tests
 */
@RunWith(SpringRunner.class)
@AclTestConfiguration
public abstract class AbstractAclTest {

    protected static final String ADMIN_ROLE = "ADMIN";
    protected static final String GENERAL_USER_ROLE = "USER";
    protected static final String SIMPLE_USER_ROLE = "SIMPLE_USER";
    protected static final String OWNER_USER = "OWNER";
    protected static final String SIMPLE_USER = "SIMPLE_USER";
    protected static final String SIMPLE_USER_2 = "SIMPLE_USER_2";
    protected static final String TEST_NAME = "test_name";
    protected static final String TEST_NAME_2 = "test_name_2";

    @Autowired
    protected PermissionGrantingStrategy grantingStrategy;

    @Autowired
    protected JdbcMutableAclServiceImpl aclService;

    @Autowired
    protected AclAuthorizationStrategy aclAuthorizationStrategy;

    @Autowired
    protected PermissionFactory permissionFactory;

    protected void initAclEntity(AbstractSecuredEntity entity) {
        initAclEntity(entity, Collections.emptyList());
    }

    protected void initAclEntity(AbstractSecuredEntity entity, List<AbstractGrantPermission> permissions) {
        ObjectIdentityImpl objectIdentity = new ObjectIdentityImpl(entity);
        AclImpl acl = new AclImpl(objectIdentity, entity.getId(), aclAuthorizationStrategy,
                grantingStrategy, null, null, true, new PrincipalSid(entity.getOwner()));
        if (CollectionUtils.isNotEmpty(permissions)) {
            IntStream
                    .range(0, permissions.size())
                    .forEach(i -> {
                        AbstractGrantPermission permission = permissions.get(i);
                        acl.insertAce(i, permissionFactory.buildFromMask(permission.mask), permission.toSid(), true);
                    });
        }
        doReturn(acl).when(aclService).readAclById(eq(objectIdentity), anyList());
        doReturn(acl).when(aclService).getAcl(eq(entity));
        doReturn(acl).when(aclService).getOrCreateObjectIdentity(eq(entity));
        doReturn(acl).when(aclService).createAcl(eq(entity));
        doReturn(acl).when(aclService).updateAcl(acl);
    }

    @AllArgsConstructor
    protected abstract static class AbstractGrantPermission {
        private int mask;

        public abstract Sid toSid();
    }

    protected static class UserPermission extends AbstractGrantPermission {
        private String userName;

        public UserPermission(String userName, int mask) {
            super(mask);
            this.userName = userName;
        }

        @Override
        public Sid toSid() {
            return new PrincipalSid(userName);
        }
    }

    protected static class AuthorityPermission extends AbstractGrantPermission {
        private String authorityName;

        public AuthorityPermission(int mask, String authorityName) {
            super(mask);
            this.authorityName = authorityName;
        }

        @Override
        public Sid toSid() {
            return new GrantedAuthoritySid(authorityName);
        }
    }
}
