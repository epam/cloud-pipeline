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

package com.epam.pipeline.security.acl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.jdbc.JdbcMutableAclService;
import org.springframework.security.acls.jdbc.LookupStrategy;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.AlreadyExistsException;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class JdbcMutableAclServiceImpl extends JdbcMutableAclService {

    private static final String CLASS_IDENTITY_QUERY = "SELECT currval('acl_class_id_seq');";
    private static final String SID_IDENTITY_QUERY = "SELECT currval('acl_sid_id_seq');";

    private String deleteSidByIdQuery = "delete from acl_sid where id=?";
    private String deleteEntriesBySidQuery = "delete from acl_entry where sid=?";

    @Autowired
    private MessageHelper messageHelper;

    public JdbcMutableAclServiceImpl(DataSource dataSource, LookupStrategy lookupStrategy,
            AclCache aclCache) {
        super(dataSource, lookupStrategy, aclCache);
        setClassIdentityQuery(CLASS_IDENTITY_QUERY);
        setSidIdentityQuery(SID_IDENTITY_QUERY);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MutableAcl createAcl(AbstractSecuredEntity securedEntity) {
        Assert.notNull(securedEntity, "Object Identity required");

        ObjectIdentity objectIdentity = new ObjectIdentityImpl(securedEntity);
        // Check this object identity hasn't already been persisted
        if (retrieveObjectIdentityPrimaryKey(objectIdentity) != null) {
            throw new AlreadyExistsException("Object identity '" + objectIdentity
                    + "' already exists");
        }

        PrincipalSid sid = new PrincipalSid(securedEntity.getOwner().toUpperCase());

        // Create the acl_object_identity row
        createObjectIdentity(objectIdentity, sid);

        // Retrieve the ACL via superclass (ensures cache registration, proper retrieval
        // etc)
        Acl acl = readAclById(objectIdentity);
        Assert.isInstanceOf(MutableAcl.class, acl, messageHelper.getMessage(MessageConstants.ERROR_MUTABLE_ACL_RETURN));

        return (MutableAcl) acl;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public MutableAcl getOrCreateObjectIdentity(AbstractSecuredEntity securedEntity) {
        ObjectIdentity identity = new ObjectIdentityImpl(securedEntity);
        if (retrieveObjectIdentityPrimaryKey(identity) != null) {
            Acl acl = readAclById(identity);
            Assert.isInstanceOf(MutableAcl.class, acl, messageHelper
                    .getMessage(MessageConstants.ERROR_MUTABLE_ACL_RETURN));
            return (MutableAcl) acl;
        } else {
            MutableAcl acl = createAcl(identity);
            if (securedEntity.getParent() != null && securedEntity.getParent().getId() != null) {
                MutableAcl parentAcl = getOrCreateObjectIdentity(securedEntity.getParent());
                acl.setParent(parentAcl);
                updateAcl(acl);
            }
            return acl;
        }
    }

    public Map<ObjectIdentity, Acl> getObjectIdentities(Set<AbstractSecuredEntity> securedEntities) {
        List<ObjectIdentity> objectIdentities = securedEntities.stream()
                .map(ObjectIdentityImpl::new)
                .collect(Collectors.toList());
        return readAclsById(objectIdentities);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteSidById(Long sidId) {
        jdbcTemplate.update(deleteEntriesBySidQuery, sidId);
        jdbcTemplate.update(deleteSidByIdQuery, sidId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Sid createOrGetSid(String userName, boolean isPrincipal) {
        createOrRetrieveSidPrimaryKey(userName, isPrincipal, true);
        return isPrincipal ? new PrincipalSid(userName) : new GrantedAuthoritySid(userName);
    }

    public Sid getSid(String user, boolean isPrincipal) {
        Assert.notNull(createOrRetrieveSidPrimaryKey(user, isPrincipal, false),
                messageHelper.getMessage(MessageConstants.ERROR_USER_NAME_NOT_FOUND, user));
        return isPrincipal ? new PrincipalSid(user) : new GrantedAuthoritySid(user);
    }

    public Long getSidId(String user, boolean isPrincipal) {
        Sid sid = isPrincipal ? new PrincipalSid(user) : new GrantedAuthoritySid(user);
        return createOrRetrieveSidPrimaryKey(sid, false);
    }

    public MutableAcl getAcl(AbstractSecuredEntity securedEntity) {
        ObjectIdentity identity = new ObjectIdentityImpl(securedEntity);
        if (retrieveObjectIdentityPrimaryKey(identity) != null) {
            Acl acl = readAclById(identity);
            Assert.isInstanceOf(MutableAcl.class, acl, messageHelper
                    .getMessage(MessageConstants.ERROR_MUTABLE_ACL_RETURN));
            return (MutableAcl) acl;
        } else {
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void getOrCreateObjectIdentityWithParent(AbstractSecuredEntity entity,
            AbstractSecuredEntity parent) {
        MutableAcl acl = getOrCreateObjectIdentity(entity);
        if ((parent == null || parent.getId() == null) && acl.getParentAcl() == null) {
            return;
        }
        if (parent == null || parent.getId() == null) {
            acl.setParent(null);
            updateAcl(acl);
        } else if (acl.getParentAcl() == null
                || acl.getParentAcl().getObjectIdentity().getIdentifier() != parent.getId()) {
            MutableAcl parentAcl = getOrCreateObjectIdentity(parent);
            acl.setParent(parentAcl);
            updateAcl(acl);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void changeOwner(final AbstractSecuredEntity entity, final String owner) {
        final MutableAcl aclFolder = getOrCreateObjectIdentity(entity);
        aclFolder.setOwner(createOrGetSid(owner, true));
        updateAcl(aclFolder);
    }
}
