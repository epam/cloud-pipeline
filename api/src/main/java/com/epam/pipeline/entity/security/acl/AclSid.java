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

package com.epam.pipeline.entity.security.acl;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Sid;

@Data
@NoArgsConstructor
public class AclSid {
    private String name;
    private boolean isPrincipal;

    public AclSid(String name, boolean isPrincipal) {
        this.name = name;
        this.isPrincipal = isPrincipal;
    }

    public AclSid(Sid sid) {
        if (sid instanceof PrincipalSid) {
            this.name = ((PrincipalSid) sid).getPrincipal();
            this.isPrincipal = true;
        } else if (sid instanceof GrantedAuthoritySid) {
            this.name = ((GrantedAuthoritySid) sid).getGrantedAuthority();
            this.isPrincipal = false;
        } else {
            throw new IllegalArgumentException("Unsupported sid " + sid.getClass());
        }
    }
}
