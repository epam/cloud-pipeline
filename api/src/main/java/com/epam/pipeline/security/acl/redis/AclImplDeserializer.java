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

import com.epam.pipeline.security.acl.AclPermissionFactory;
import com.epam.pipeline.security.acl.PermissionGrantingStrategyImpl;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.SneakyThrows;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.acls.domain.ConsoleAuditLogger;
import org.springframework.security.acls.domain.DefaultPermissionFactory;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.acls.model.Sid;

import java.io.IOException;
import java.util.Optional;

public class AclImplDeserializer extends StdDeserializer<AclImpl> {

    private final AclAuthorizationStrategy aclAuthorizationStrategy = new AllowAllAuthStrategy();
    private final PermissionGrantingStrategy permissionGrantingStrategy =
            new PermissionGrantingStrategyImpl(new ConsoleAuditLogger());
    private final DefaultPermissionFactory permissionFactory = new AclPermissionFactory();

    public AclImplDeserializer() {
        this(null);
    }

    public AclImplDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public AclImpl deserialize(final JsonParser jp, final DeserializationContext ctxt)
            throws IOException {
        return readAcl(jp.getCodec().readTree(jp));
    }

    @SneakyThrows
    private AclImpl readAcl(final JsonNode node) {
        final String type = node.get(AclImplFields.OBJ_TYPE).asText();
        final Long id = node.get(AclImplFields.OBJ_ID).asLong();
        final Long aclId = node.get(AclImplFields.ID).asLong();
        final boolean inherit = node.get(AclImplFields.INHERIT).asBoolean();
        final Sid sid = new PrincipalSid(node.get(AclImplFields.OWNER).asText());
        final AclImpl parent = Optional.ofNullable(node.get(AclImplFields.PARENT))
                .map(this::readAcl).orElse(null);
        final AclImpl acl = new AclImpl(new ObjectIdentityImpl(type, id), aclId,
                aclAuthorizationStrategy, permissionGrantingStrategy, parent,
                null, inherit, sid);
        Optional.ofNullable(node.get(AclImplFields.ACES))
                .ifPresent(aces -> {
                    aces.forEach(aceNode -> {
                        final Sid aceSid = readSid(aceNode);
                        final Permission permission = permissionFactory.buildFromMask(
                                aceNode.get(AclImplFields.ACE_MASK).asInt());
                        acl.insertAce(0, permission, aceSid,
                                aceNode.get(AclImplFields.ACE_GRANTING).asBoolean());
                    });
                });
        return acl;
    }

    private Sid readSid(final JsonNode node) {
        final boolean isPrincipal = node.get(AclImplFields.ACE_IS_PRINCIPAL).asBoolean();
        final String sid = node.get(AclImplFields.ACE_SID).asText();
        return isPrincipal ? new PrincipalSid(sid) : new GrantedAuthoritySid(sid);
    }
}
