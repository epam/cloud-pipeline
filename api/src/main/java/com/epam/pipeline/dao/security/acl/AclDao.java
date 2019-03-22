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

package com.epam.pipeline.dao.security.acl;

import java.util.List;

import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Sid;
import org.springframework.util.CollectionUtils;

public class AclDao extends NamedParameterJdbcDaoSupport {

    private String findSidByNameQuery;

    public Sid loadSidByName(String user) {
        List<Sid> result = getJdbcTemplate().query(findSidByNameQuery, SidParameters.getRowMapper(), user);
        if (CollectionUtils.isEmpty(result)) {
            return null;
        } else {
            return result.get(0);
        }
    }

    @Required
    public void setFindSidByNameQuery(String findSidByNameQuery) {
        this.findSidByNameQuery = findSidByNameQuery;
    }

    enum SidParameters {
        ID, 
        PRINCIPAL, 
        SID;

        private static RowMapper<Sid> getRowMapper() {
            return (rs, rowNum) -> {
                boolean isPrincipal = rs.getBoolean(PRINCIPAL.name());
                String name = rs.getString(SID.name());
                if (isPrincipal) {
                    return new PrincipalSid(name);
                } else {
                    return new GrantedAuthoritySid(name);
                }
            };
        }
    }
}
