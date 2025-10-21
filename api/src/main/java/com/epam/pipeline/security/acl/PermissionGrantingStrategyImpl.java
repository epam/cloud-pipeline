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

package com.epam.pipeline.security.acl;

import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.manager.security.PermissionsService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.AuditLogger;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.PermissionGrantingStrategy;
import org.springframework.security.acls.model.Sid;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class PermissionGrantingStrategyImpl implements PermissionGrantingStrategy {

    private static final GrantResult GRANT_RESULT_NOT_FOUND = new GrantResult(false, false);
    private static final Set<String> STORAGE_CLASSES = new HashSet<>(
            Arrays.asList(
                    S3bucketDataStorage.class.getName(),
                    GSBucketStorage.class.getName(),
                    AzureBlobStorage.class.getName(),
                    NFSDataStorage.class.getName())
    );

    @Autowired
    private PermissionsService permissionsService;
    private final transient AuditLogger auditLogger;

    /**
     * Creates an instance with the logger which will be used to record granting and
     * denial of requested permissions.
     */
    public PermissionGrantingStrategyImpl(AuditLogger auditLogger) {
        Assert.notNull(auditLogger, "auditLogger cannot be null");
        this.auditLogger = auditLogger;
    }

    /**
     * For tests
     */
    protected PermissionGrantingStrategyImpl(AuditLogger auditLogger, PermissionsService permissionsService) {
        Assert.notNull(auditLogger, "auditLogger cannot be null");
        this.permissionsService = permissionsService;
        this.auditLogger = auditLogger;
    }

    /**
     * Determines authorization. The order of the <code>permission</code> and
     * <code>sid</code> arguments is <em>extremely important</em>! The method will iterate
     * through each of the <code>permission</code>s in the order specified. For each
     * iteration, all of the <code>sid</code>s will be considered, order of the sids defined as:
     * PRINCIPAL -> GROUP -> ROLE, which gives possibility to finetune permissions quiet flexible.
     * For each type of the SID the following approach is applied:
     * Method will try to match all SIDs in group and check permissions for them.
     * Then, if any SID match deny result, deny will prevail. If no deny was found, either
     * allow will be returned (if any) as result for the group or empty result will be returned
     * if no match at all, then based on PRINCIPAL -> GROUP -> ROLE order final result will be checked.
     * Finally, if no permissions were found, process will be repeated for the parent, then for its parent and etc.
     * @param permission         the exact permissions to scan for (order is important)
     * @param sids               the exact SIDs to scan for (order is important)
     * @param administrativeMode if <code>true</code> denotes the query is for
     *                           administrative purposes and no auditing will be undertaken
     * @return <code>true</code> if one of the permissions has been granted,
     * <code>false</code> if one of the permissions has been specifically revoked
     * @throws NotFoundException if an exact ACE for one of the permission bit masks and
     *                           SID combination could not be found
     */
    public boolean isGranted(Acl acl, List<Permission> permission, List<Sid> sids,
            boolean administrativeMode) throws NotFoundException {

        final Map<SidType, List<Sid>> sidsByType = AclUtils.groupSidsByType(sids);

        if (sidsByType.get(SidType.PRINCIPAL).stream().anyMatch(sid -> acl.getOwner().equals(sid))) {
            return true;
        }

        //Storage special case
        if (STORAGE_CLASSES.contains(acl.getObjectIdentity().getType()) &&
                sidsByType.get(SidType.ROLE).stream().anyMatch(sid ->
                        sid.equals(new GrantedAuthoritySid(DefaultRoles.ROLE_STORAGE_ADMIN.getName())))) {
            return true;
        }

        final List<AccessControlEntry> aces = acl.getEntries();

        for (Permission p : permission) {
            final GrantResult grantResult = Stream.of(SidType.PRINCIPAL, SidType.GROUP, SidType.ROLE)
                    .map(sidsByType::get)
                    .map(sidGroup -> calculateGrantingResultForSidGroup(p, sidGroup, aces, administrativeMode))
                    .filter(GrantResult::isFound)
                    .findFirst().orElse(GRANT_RESULT_NOT_FOUND);

            if (grantResult.isFound()) {
                return grantResult.isGrant();
            }
        }

        // No matches have been found so far
        if (acl.isEntriesInheriting() && (acl.getParentAcl() != null)) {
            // We have a parent, so let them try to find a matching ACE
            return acl.getParentAcl().isGranted(permission, sids, false);
        } else {
            // We either have no parent, or we're the uppermost parent
            throw new NotFoundException(
                    "Unable to locate a matching ACE for passed permissions and SIDs");
        }
    }

    private GrantResult calculateGrantingResultForSidGroup(Permission p, List<Sid> sids, List<AccessControlEntry> aces,
                                                           boolean administrativeMode) {
        GrantResult granting = GRANT_RESULT_NOT_FOUND;
        for (Sid sid: sids) {
            GrantResult grantResult = calculateGrantingResultForSid(p, sid, aces, administrativeMode);
            if (grantResult.isFound()) {
                if (!grantResult.isGrant()) {
                    return grantResult;
                } else {
                    // only safe allowing result for now, since we still can find reject,
                    // and it has priority over allow for the same SidType
                    granting = grantResult;
                }
            }
        }
        return granting;
    }

    private GrantResult calculateGrantingResultForSid(Permission p, Sid sid, List<AccessControlEntry> aces,
                                                      boolean administrativeMode) {
        for (AccessControlEntry ace : aces) {
            if (ace.getSid().equals(sid) && permissionsService.containsPermission(ace.getPermission(), p)) {
                // Found a matching ACE, so its authorization decision will
                // prevail
                if (permissionsService.permissionIsNotDenied(ace, ace.getPermission(), p)) {
                    // Success
                    if (!administrativeMode) {
                        auditLogger.logIfNeeded(true, ace);
                    }
                    return new GrantResult(true, true);
                } else {
                    // Failure for this permission, so stop search
                    // We will see if they have a different permission
                    // (this permission is 100% rejected for this SID)
                    if (!administrativeMode) {
                        auditLogger.logIfNeeded(false, ace);
                    }
                    return new GrantResult(true, false);
                }
            }
        }
        return GRANT_RESULT_NOT_FOUND;
    }

    @Getter
    private static final class GrantResult {

        private final boolean found;
        private final boolean grant;

        private GrantResult(boolean found, boolean grant) {
            this.found = found;
            this.grant = grant;
        }
    }



}
