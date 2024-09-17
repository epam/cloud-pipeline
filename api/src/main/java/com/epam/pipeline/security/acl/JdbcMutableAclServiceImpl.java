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

package com.epam.pipeline.security.acl;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclEntitySummary;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@Slf4j
public class JdbcMutableAclServiceImpl extends JdbcMutableAclService {

    private static final String CLASS_IDENTITY_QUERY = "SELECT currval('acl_class_id_seq');";
    private static final String SID_IDENTITY_QUERY = "SELECT currval('acl_sid_id_seq');";
    private static final String DELETE_SID_BY_ID_QUERY = "delete from acl_sid where id=?";
    private static final String DELETE_ENTRIES_BY_SID_QUERY = "delete from acl_entry where sid=?";
    private static final String LOAD_ENTRIES_BY_SIDS_COUNT_QUERY =
        "SELECT count(*) FROM pipeline.acl_entry where sid IN (@in@)";
    private static final String LOAD_OWNER_ENTRIES_BY_SID_COUNT_QUERY =
            "SELECT count(*) FROM pipeline.acl_object_identity where owner_sid=?";
    private static final String LOAD_ENTRIES_SUMMARY_BY_SID_QUERY =
        "SELECT entries.acl_object_identity,"
        + " identities.object_id_identity,"
        + " classes.class"
        + " FROM pipeline.acl_entry entries"
        + " INNER JOIN pipeline.acl_object_identity identities on entries.acl_object_identity=identities.id"
        + " INNER JOIN pipeline.acl_class classes on classes.id=identities.object_id_class"
        + " WHERE sid=?";

    @Autowired
    private MessageHelper messageHelper;
    private AclCache aclCache;

    public JdbcMutableAclServiceImpl(DataSource dataSource, LookupStrategy lookupStrategy,
            AclCache aclCache) {
        super(dataSource, lookupStrategy, aclCache);
        setClassIdentityQuery(CLASS_IDENTITY_QUERY);
        setSidIdentityQuery(SID_IDENTITY_QUERY);
        this.aclCache = aclCache;
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
    public MutableAcl getOrCreateObjectIdentity(final AbstractSecuredEntity securedEntity,
                                                final boolean reload) {

        final ObjectIdentity identity = new ObjectIdentityImpl(securedEntity.getClass(), securedEntity.getId());
        if (reload) {
            clearCacheIncludingChildren(identity);
        }

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

    @Transactional(propagation = Propagation.REQUIRED)
    public MutableAcl getOrCreateObjectIdentity(AbstractSecuredEntity securedEntity) {
        return getOrCreateObjectIdentity(securedEntity, false);
    }

    public Map<ObjectIdentity, Acl> getObjectIdentities(Set<AbstractSecuredEntity> securedEntities) {
        List<ObjectIdentity> objectIdentities = securedEntities.stream()
                .map(ObjectIdentityImpl::new)
                .collect(Collectors.toList());
        return readAclsById(objectIdentities);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteSidById(Long sidId) {
        jdbcTemplate.update(DELETE_ENTRIES_BY_SID_QUERY, sidId);
        final Integer ownerEntries = jdbcTemplate.queryForObject(
                LOAD_OWNER_ENTRIES_BY_SID_COUNT_QUERY, Integer.class, sidId);
        if (ownerEntries > 0) {
            log.debug("Sid {} in an owner of {} entity(s). Leaving ACL record.", sidId, ownerEntries);
            return;
        }
        jdbcTemplate.update(DELETE_SID_BY_ID_QUERY, sidId);
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
        try {
            ObjectIdentity identity = new ObjectIdentityImpl(securedEntity);
            Acl acl = readAclById(identity);
            Assert.isInstanceOf(MutableAcl.class, acl, messageHelper
                    .getMessage(MessageConstants.ERROR_MUTABLE_ACL_RETURN));
            return (MutableAcl) acl;
        } catch (NotFoundException e) {
            log.debug(e.getMessage());
            return null;
        }
    }

    @Override
    public Acl readAclById(ObjectIdentity object, List<Sid> sids)
            throws NotFoundException {
        Map<ObjectIdentity, Acl> map = readAclsById(Collections.singletonList(object), sids);
        if (!map.containsKey(object)) {
            throw new NotFoundException("There should have been an Acl entry for ObjectIdentity " + object);
        }
        return map.get(object);
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
        final MutableAcl aclFolder = getOrCreateObjectIdentity(entity, true);
        aclFolder.setOwner(createOrGetSid(owner, true));
        updateAcl(aclFolder);
    }

    public void putInCache(final MutableAcl acl) {
        aclCache.putInCache(acl);
    }

    // Copy of JdbcMutableAclService.clearCacheIncludingChildren
    private void clearCacheIncludingChildren(final ObjectIdentity objectIdentity) {
        Assert.notNull(objectIdentity, "ObjectIdentity required");
        List<ObjectIdentity> children = this.findChildren(objectIdentity);
        if (children != null) {
            Iterator var3 = children.iterator();

            while(var3.hasNext()) {
                ObjectIdentity child = (ObjectIdentity)var3.next();
                this.clearCacheIncludingChildren(child);
            }
        }

        this.aclCache.evictFromCache(objectIdentity);
    }

    public Integer loadEntriesBySidsCount(final Collection<Long> sidIds) {
        final String query = DaoHelper.replaceInClause(LOAD_ENTRIES_BY_SIDS_COUNT_QUERY, sidIds.size());
        return jdbcTemplate.queryForObject(query, sidIds.toArray(), Integer.class);
    }

    public List<AclEntitySummary> loadEntriesWithAuthoritySummary(final Long sidId) {
        return jdbcTemplate.query(LOAD_ENTRIES_SUMMARY_BY_SID_QUERY, new Long[]{sidId},
            (rs, rowNum) -> new AclEntitySummary(rs.getLong(1),
                                                 rs.getLong(2),
                                                 rs.getString(3)));
    }
}
