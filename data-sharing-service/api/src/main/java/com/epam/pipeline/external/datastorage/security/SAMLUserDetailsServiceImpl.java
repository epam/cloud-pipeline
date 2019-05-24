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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;
import org.springframework.stereotype.Service;

import com.epam.pipeline.external.datastorage.exception.PipelineAuthenticationException;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;

@Service
public class SAMLUserDetailsServiceImpl implements SAMLUserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLUserDetailsServiceImpl.class);
    private static final String ATTRIBUTES_DELIMITER = "=";

    @Value("${saml.authorities.attribute.names: null}")
    private List<String> authorities;

    @Value("#{'${saml.user.attributes}'.split(',')}")
    private Set<String> samlAttributes;

    @Autowired
    private PipelineAuthManager pipelineAuthManager;

    @Override
    public UserContext loadUserBySAML(SAMLCredential credential) {
        String userName = credential.getNameID().getValue().toUpperCase();
        List<String> groups = readAuthorities(credential);
        Map<String, String> attributes = readAttributes(credential);

        UserContext userContext = new UserContext(userName);
        userContext.setGroups(groups);
        userContext.setAttributes(attributes);

        try {
            String token = pipelineAuthManager.getToken(credential);
            userContext.setToken(token);
        } catch (PipelineAuthenticationException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        }

        return userContext;
    }

    List<String> readAuthorities(SAMLCredential credential) {
        if (CollectionUtils.isEmpty(authorities)) {
            return Collections.emptyList();
        }
        List<String> grantedAuthorities = new ArrayList<>();
        authorities.stream().forEach(auth -> {
            if (StringUtils.isEmpty(auth)) {
                return;
            }
            String[] attributeValues = credential.getAttributeAsStringArray(auth);
            if (attributeValues != null && attributeValues.length > 0) {
                grantedAuthorities.addAll(
                    Arrays.stream(attributeValues)
                        .map(String::toUpperCase)
                        .collect(Collectors.toList()));
            }
        });
        return grantedAuthorities;
    }

    Map<String, String> readAttributes(SAMLCredential credential) {
        if (CollectionUtils.isEmpty(samlAttributes)) {
            return Collections.emptyMap();
        }
        Map<String, String> parsedAttributes = new HashMap<>();
        for (String attribute : samlAttributes) {
            if (attribute.contains(ATTRIBUTES_DELIMITER)) {
                String[] splittedRecord = attribute.split(ATTRIBUTES_DELIMITER);
                String key = splittedRecord[0];
                String value = splittedRecord[1];
                if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                    LOGGER.error("Can not parse saml user attributes property.");
                    continue;
                }
                String attributeValues = credential.getAttributeAsString(value);
                if (StringUtils.isNotEmpty(attributeValues)) {
                    parsedAttributes.put(key, attributeValues);
                }
            }
        }
        return parsedAttributes;
    }
}
