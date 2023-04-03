/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.security.acl.redis;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;

import java.io.IOException;

public class AclImplSerializer extends StdSerializer<AclImpl> {

    public AclImplSerializer() {
        this(null);
    }

    public AclImplSerializer(final Class<AclImpl> t) {
        super(t);
    }

    @Override
    public void serialize(final AclImpl value,
                          final JsonGenerator gen,
                          final SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField(AclImplFields.OBJ_TYPE, value.getObjectIdentity().getType());
        gen.writeNumberField(AclImplFields.OBJ_ID, (Long) value.getObjectIdentity().getIdentifier());
        gen.writeNumberField(AclImplFields.ID, (Long) value.getId());
        gen.writeBooleanField(AclImplFields.INHERIT, value.isEntriesInheriting());
        gen.writeStringField(AclImplFields.OWNER, ((PrincipalSid) value.getOwner()).getPrincipal());
        if (value.getParentAcl() != null) {
            gen.writeObjectField(AclImplFields.PARENT, value.getParentAcl());
        }
        if (CollectionUtils.isNotEmpty(value.getEntries())) {
            gen.writeArrayFieldStart(AclImplFields.ACES);
            value.getEntries().forEach(ace -> writeAce(ace, gen));
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    @SneakyThrows
    private void writeAce(final AccessControlEntry ace,
                          final JsonGenerator gen) {
        gen.writeStartObject();
        gen.writeNumberField(AclImplFields.ACE_MASK, ace.getPermission().getMask());
        if (ace.getSid() instanceof PrincipalSid) {
            gen.writeBooleanField(AclImplFields.ACE_IS_PRINCIPAL, true);
            gen.writeStringField(AclImplFields.ACE_SID, ((PrincipalSid) ace.getSid()).getPrincipal());
        } else if (ace.getSid() instanceof GrantedAuthoritySid) {
            gen.writeBooleanField(AclImplFields.ACE_IS_PRINCIPAL, false);
            gen.writeStringField(AclImplFields.ACE_SID, ((GrantedAuthoritySid) ace.getSid()).getGrantedAuthority());
        }
        gen.writeBooleanField(AclImplFields.ACE_GRANTING, ace.isGranting());
        gen.writeEndObject();
    }
}
