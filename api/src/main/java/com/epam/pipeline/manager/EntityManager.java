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

package com.epam.pipeline.manager;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.google.common.base.Defaults;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

@Service
public class EntityManager {

    @Autowired
    private MessageHelper messageHelper;

    private Map<AclClass, SecuredEntityManager> managers;

    /**
     * This method allows to reset field values (default - for primitives, null - for others),
     * excluding fields, specified in the {@code fieldsToKeep}
     * @param obj object to modify
     * @param fieldsToKeep names of the fields, that shouldn't be modified
     * @return modified object
     */
    public static <T> T resetAllFieldsExcept(final T obj, final Collection<String> fieldsToKeep) {
        ReflectionUtils.doWithFields(obj.getClass(), field -> {
            if (!fieldsToKeep.contains(field.getName())
                && !Modifier.isStatic(field.getModifiers())) {
                final boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                field.set(obj, Defaults.defaultValue(field.getType()));
                field.setAccessible(isAccessible);
            }
        });
        return obj;
    }

    @Autowired
    public void setManagers(List<SecuredEntityManager> managers) {
        if (CollectionUtils.isEmpty(managers)) {
            this.managers = new EnumMap<>(AclClass.class);
        } else {
            this.managers = managers
                    .stream()
                    .collect(Collectors.toMap(
                            SecuredEntityManager::getSupportedClass, Function.identity()));
        }
    }

    public SecuredEntityManager getEntityManager(AclClass aclClass) {
        if (!managers.containsKey(aclClass)) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_CLASS_NOT_SUPPORTED, aclClass));
        }
        return managers.get(aclClass);
    }

    public SecuredEntityManager getEntityManager(AbstractSecuredEntity entity) {
        return getEntityManager(entity.getAclClass());
    }

    public AbstractSecuredEntity load(AclClass aclClass, Long id) {
        return getEntityManager(aclClass).load(id);
    }

    public AbstractSecuredEntity loadByNameOrId(AclClass aclClass, String identifier) {
        return getEntityManager(aclClass).loadByNameOrId(identifier);
    }

    public AbstractSecuredEntity changeOwner(AclClass aclClass, Long id, String owner) {
        return getEntityManager(aclClass).changeOwner(id, owner);
    }

    public Integer loadTotalCount(AclClass aclClass) {
        return getEntityManager(aclClass).loadTotalCount();
    }

    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(
            AclClass aclClass, Integer page, Integer pageSize) {
        return getEntityManager(aclClass).loadAllWithParents(page, pageSize);
    }

    public AbstractSecuredEntity loadEntityWithParents(final AclClass aclClass, final Long id) {
        return getEntityManager(aclClass).loadWithParents(id);
    }
}
