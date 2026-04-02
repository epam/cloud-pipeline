/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.external.datastorage.security;

import javax.xml.namespace.QName;

import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.springframework.security.saml.context.SAMLContextProviderImpl;
import org.springframework.security.saml.context.SAMLMessageContext;
import org.springframework.security.saml.metadata.ExtendedMetadata;

/**
 * A custom SAMLContextProvider implementation, that is able to sign messages with other key
 */
public class SAMLContextProviderCustomSingKey extends SAMLContextProviderImpl {
    private String signingKey;

    public SAMLContextProviderCustomSingKey(String signingKey) {
        this.signingKey = signingKey;
    }

    @Override
    protected void populateLocalEntity(SAMLMessageContext samlContext)
        throws MetadataProviderException {
        String localEntityId = samlContext.getLocalEntityId();
        QName localEntityRole = samlContext.getLocalEntityRole();

        if (localEntityId == null) {
            throw new MetadataProviderException("No hosted service provider is configured and no alias was selected");
        }

        EntityDescriptor entityDescriptor = metadata.getEntityDescriptor(localEntityId);
        RoleDescriptor
            roleDescriptor = metadata.getRole(localEntityId, localEntityRole, SAMLConstants.SAML20P_NS);
        ExtendedMetadata extendedMetadata = metadata.getExtendedMetadata(localEntityId);

        if (entityDescriptor == null || roleDescriptor == null) {
            throw new MetadataProviderException("Metadata for entity " + localEntityId +
                                                " and role " + localEntityRole + " wasn't found");
        }

        samlContext.setLocalEntityMetadata(entityDescriptor);
        samlContext.setLocalEntityRoleMetadata(roleDescriptor);
        samlContext.setLocalExtendedMetadata(extendedMetadata);

        if (extendedMetadata.getSigningKey() != null) {
            samlContext.setLocalSigningCredential(keyManager.getCredential(extendedMetadata.getSigningKey()));
        } else {
            samlContext.setLocalSigningCredential(keyManager.getCredential(signingKey));
        }
    }
}
