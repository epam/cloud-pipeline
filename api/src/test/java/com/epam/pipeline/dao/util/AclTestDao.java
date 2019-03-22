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

package com.epam.pipeline.dao.util;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A helpful test DAO to store mock ACL data in database for security testing
 */
public class AclTestDao extends NamedParameterJdbcDaoSupport {
    private static final String SID_PARAM = "SID";

    private String sidSequenceName;
    private String objectIdentitySequenceName;
    private String classSequenceName;
    private String entrySequenceName;

    private String createAclSidQuery;
    private String createAclObjectIdentityQuery;
    private String loadAclClassQuery;
    private String loadAclSidQuery;
    private String loadAclObjectIdentityQuery;
    private String loadAclEntriesQuery;
    private String createAclClassQuery;
    private String createAclEntryQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public Pair<AclSid, AclObjectIdentity> createAclForObject(AbstractSecuredEntity entity) {
        Optional<AclSid> existingSid = loadAclSid(entity.getOwner());

        AclSid sid = existingSid.orElseGet(() -> {
            AclSid newSid = new AclSid(true, entity.getOwner());
            createAclSid(newSid);
            return newSid;
        });

        AclClass aclClass = new AclClass(entity.getClass().getCanonicalName());
        createAclClassIfNotPresent(aclClass);

        AclObjectIdentity identity = new AclObjectIdentity(sid, entity.getId(), aclClass.getId(), null, true);
        createObjectIdentity(identity);

        return new ImmutablePair<>(sid, identity);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void grantPermissions(AbstractSecuredEntity entity, String userName, List<AclPermission> permissions) {
        Optional<AclSid> existingSid = loadAclSid(userName);

        AclSid sid = existingSid.orElseGet(() -> {
            AclSid newSid = new AclSid(true, entity.getOwner());
            createAclSid(newSid);
            return newSid;
        });

        Optional<AclObjectIdentity> existingIdentity = loadAclObjectIdentity(entity.getId());
        AclObjectIdentity identity = existingIdentity.orElseGet(() -> createAclForObject(entity).getRight());

        int maxOrder = loadAclEntries(identity.getId()).stream()
            .map(AclEntry::getOrder)
            .max(Comparator.naturalOrder())
            .orElse(0) + 1;

        for (AclPermission p : permissions) {
            AclTestDao.AclEntry groupAclEntry = new AclTestDao.AclEntry(identity, maxOrder++, sid,
                                                                        p.getMask(), p.isGranting());
            createAclEntry(groupAclEntry);
        }

    }

    public Optional<AclSid> loadAclSid(String sid) {
        List<AclSid> sids = getJdbcTemplate().query(loadAclSidQuery, (rs, i) -> {
            AclSid s = new AclSid();

            s.setId(rs.getLong("ID"));
            s.setPrinciple(rs.getBoolean("PRINCIPAL"));
            s.setSid(rs.getString(SID_PARAM));

            return s;
        }, sid);
        return sids.isEmpty() ? Optional.empty() : Optional.of(sids.get(0));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAclSid(AclSid sid) {
        sid.id = daoHelper.createId(sidSequenceName);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ID", sid.id);
        params.addValue("PRINCIPAL", sid.principle);
        params.addValue(SID_PARAM, sid.sid);

        getNamedParameterJdbcTemplate().update(createAclSidQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createObjectIdentity(AclObjectIdentity objectIdentity) {
        objectIdentity.id = daoHelper.createId(objectIdentitySequenceName);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ID", objectIdentity.id);
        params.addValue("OBJECT_ID_CLASS", objectIdentity.classId);
        params.addValue("OBJECT_ID_IDENTITY", objectIdentity.objectId);
        params.addValue("PARENT_OBJECT", objectIdentity.parent != null ? objectIdentity.parent.id : null);
        params.addValue("OWNER_SID", objectIdentity.owner.id);
        params.addValue("ENTRIES_INHERITING", objectIdentity.inheriting);

        getNamedParameterJdbcTemplate().update(createAclObjectIdentityQuery, params);
    }

    public Optional<AclObjectIdentity> loadAclObjectIdentity(long objectId) {
        List<AclObjectIdentity> identities = getJdbcTemplate().query(loadAclObjectIdentityQuery, (rs, i) -> {
            AclObjectIdentity identity = new AclObjectIdentity(
                new AclSid(true, rs.getString("OWNER_SID")),
                rs.getLong("OBJECT_ID_IDENTITY"),
                rs.getLong("OBJECT_ID_CLASS"),
                null,
                rs.getBoolean("ENTRIES_INHERITING"));

            identity.setId(rs.getLong("ID"));
            return identity;
        }, objectId);
        return identities.isEmpty() ? Optional.empty() : Optional.of(identities.get(0));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAclClassIfNotPresent(AclClass aclClass) {
        List<Long> ids = getJdbcTemplate().query(loadAclClassQuery, new SingleColumnRowMapper<>(),
                                                 aclClass.getClassName());
        if (!ids.isEmpty()) {
            aclClass.setId(ids.get(0));
            return;
        }

        aclClass.setId(daoHelper.createId(classSequenceName));
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ID", aclClass.id);
        params.addValue("CLASS", aclClass.className);

        getNamedParameterJdbcTemplate().update(createAclClassQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAclEntry(AclEntry entry) {
        entry.setId(daoHelper.createId(entrySequenceName));

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ID", entry.id);
        params.addValue("ACL_OBJECT_IDENTITY", entry.objectIdentity.id);
        params.addValue("ACE_ORDER", entry.order);
        params.addValue(SID_PARAM, entry.sid.id);
        params.addValue("MASK", entry.mask);
        params.addValue("GRANTING", entry.granting);
        params.addValue("AUDIT_SUCCESS", entry.auditSuccess);
        params.addValue("AUDIT_FAILURE", entry.auditFailure);

        getNamedParameterJdbcTemplate().update(createAclEntryQuery, params);
    }

    public List<AclEntry> loadAclEntries(long aclObjectIdentityId) {
        return getJdbcTemplate().query(loadAclEntriesQuery, (rs, i) -> {
            AclObjectIdentity identity = new AclObjectIdentity();
            identity.setId(rs.getLong("ACL_OBJECT_IDENTITY"));
            AclSid sid = new AclSid();
            sid.setId(rs.getLong(SID_PARAM));
            return new AclEntry(identity,
                                rs.getInt("ACE_ORDER"),
                                sid,
                                rs.getInt("MASK"),
                                rs.getBoolean("GRANTING"));
        }, aclObjectIdentityId);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AclSid {
        private boolean principle;
        private String sid;
        private Long id;

        public AclSid(boolean principle, String sid) {
            this.principle = principle;
            this.sid = sid;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AclObjectIdentity {
        private Long id;
        private AclSid owner;
        private Long objectId;
        private Long classId;
        private AclObjectIdentity parent;
        private boolean inheriting;

        public AclObjectIdentity(AclSid owner, Long objectId, Long classId,
                                 AclObjectIdentity parent, boolean inheriting) {
            this.owner = owner;
            this.objectId = objectId;
            this.classId = classId;
            this.parent = parent;
            this.inheriting = inheriting;
        }
    }

    @Setter
    @Getter
    public static class AclClass {
        private Long id;
        private String className;

        public AclClass(String className) {
            this.className = className;
        }
    }

    @Getter
    @Setter
    public static class AclEntry {
        private Long id;
        private AclObjectIdentity objectIdentity;
        private Integer order;
        private AclSid sid;
        private Integer mask;
        private boolean granting;
        private boolean auditSuccess;
        private boolean auditFailure;

        public AclEntry(AclObjectIdentity objectIdentity, Integer order, AclSid sid, Integer mask, boolean granting) {
            this.objectIdentity = objectIdentity;
            this.order = order;
            this.sid = sid;
            this.mask = mask;
            this.granting = granting;
            this.auditSuccess = false;
            this.auditFailure = false;
        }
    }

    @Required
    public void setCreateAclSidQuery(String createAclSidQuery) {
        this.createAclSidQuery = createAclSidQuery;
    }

    @Required
    public void setCreateAclObjectIdentityQuery(String createAclObjectIdentityQuery) {
        this.createAclObjectIdentityQuery = createAclObjectIdentityQuery;
    }

    @Required
    public void setSidSequenceName(String sidSequenceName) {
        this.sidSequenceName = sidSequenceName;
    }

    @Required
    public void setObjectIdentitySequenceName(String objectIdentitySequenceName) {
        this.objectIdentitySequenceName = objectIdentitySequenceName;
    }

    @Required
    public void setLoadAclClassQuery(String loadAclClassQuery) {
        this.loadAclClassQuery = loadAclClassQuery;
    }

    @Required
    public void setCreateAclClassQuery(String createAclClassQuery) {
        this.createAclClassQuery = createAclClassQuery;
    }

    @Required
    public void setClassSequenceName(String classSequenceName) {
        this.classSequenceName = classSequenceName;
    }

    @Required
    public void setEntrySequenceName(String entrySequenceName) {
        this.entrySequenceName = entrySequenceName;
    }

    @Required
    public void setCreateAclEntryQuery(String createAclEntryQuery) {
        this.createAclEntryQuery = createAclEntryQuery;
    }

    @Required
    public void setLoadAclSidQuery(String loadAclSidQuery) {
        this.loadAclSidQuery = loadAclSidQuery;
    }

    @Required
    public void setLoadAclObjectIdentityQuery(String loadAclObjectIdentityQuery) {
        this.loadAclObjectIdentityQuery = loadAclObjectIdentityQuery;
    }

    @Required
    public void setLoadAclEntriesQuery(String loadAclEntriesQuery) {
        this.loadAclEntriesQuery = loadAclEntriesQuery;
    }
}
