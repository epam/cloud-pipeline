/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.acl;

import org.springframework.security.acls.model.AclCache;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;

import java.io.Serializable;

/**
 * Stub for ACL cache
 */
public class DisabledAclCache implements AclCache {

    @Override
    public void evictFromCache(Serializable pk) {
        //do nothing
    }

    @Override
    public void evictFromCache(ObjectIdentity objectIdentity) {
        //do nothing
    }

    @Override
    public MutableAcl getFromCache(ObjectIdentity objectIdentity) {
        return null;
    }

    @Override
    public MutableAcl getFromCache(Serializable pk) {
        return null;
    }

    @Override
    public void putInCache(MutableAcl acl) {
        //do nothing
    }

    @Override
    public void clearCache() {
        //do nothing
    }
}
