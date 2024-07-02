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
import java.util.Set;

public class PermissionGrantingStrategyImpl implements PermissionGrantingStrategy {

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
     * Determines authorization. The order of the <code>permission</code> and
     * <code>sid</code> arguments is <em>extremely important</em>! The method will iterate
     * through each of the <code>permission</code>s in the order specified. For each
     * iteration, all of the <code>sid</code>s will be considered, again in the order they
     * are presented. A search will then be performed for the first
     * {@link AccessControlEntry} object that directly matches that
     * <code>permission:sid</code> combination. When the <em>first full match</em> is
     * found (ie an ACE that has the SID currently being searched for and the exact
     * permission bit mask being search for), the grant or deny flag for that ACE will
     * prevail. If the ACE specifies to grant access, the method will return
     * <code>true</code>. If the ACE specifies to deny access, the loop will stop and the
     * next <code>permission</code> iteration will be performed. If each permission
     * indicates to deny access, the first deny ACE found will be considered the reason
     * for the failure (as it was the first match found, and is therefore the one most
     * logically requiring changes - although not always). If absolutely no matching ACE
     * was found at all for any permission, the parent ACL will be tried (provided that
     * there is a parent and {@link Acl#isEntriesInheriting()} is <code>true</code>. The
     * parent ACL will also scan its parent and so on. If ultimately no matching ACE is
     * found, a <code>NotFoundException</code> will be thrown and the caller will need to
     * decide how to handle the permission check. Similarly, if any of the SID arguments
     * presented to the method were not loaded by the ACL,
     * <code>UnloadedSidException</code> will be thrown.
     *
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

        if (sids.stream().anyMatch(sid -> acl.getOwner().equals(sid))) {
            return true;
        }

        //Storage special case
        if (STORAGE_CLASSES.contains(acl.getObjectIdentity().getType()) &&
                sids.stream().anyMatch(sid ->
                        sid.equals(new GrantedAuthoritySid(DefaultRoles.ROLE_STORAGE_ADMIN.getName())))) {
            return true;
        }

        final List<AccessControlEntry> aces = acl.getEntries();

        AccessControlEntry firstRejection = null;

        for (Permission p : permission) {
            for (Sid sid : sids) {
                // Attempt to find exact match for this permission mask and SID
                boolean scanNextSid = true;

                for (AccessControlEntry ace : aces) {

                    if (ace.getSid().equals(sid) && permissionsService.containsPermission(ace.getPermission(), p)) {
                        // Found a matching ACE, so its authorization decision will
                        // prevail
                        if (permissionsService.permissionIsNotDenied(ace, ace.getPermission(), p)) {
                            // Success
                            if (!administrativeMode) {
                                auditLogger.logIfNeeded(true, ace);
                            }

                            return true;
                        }

                        // Failure for this permission, so stop search
                        // We will see if they have a different permission
                        // (this permission is 100% rejected for this SID)
                        if (firstRejection == null) {
                            // Store first rejection for auditing reasons
                            firstRejection = ace;
                        }

                        scanNextSid = false; // helps break the loop

                        break; // exit aces loop
                    }
                }

                if (!scanNextSid) {
                    break; // exit SID for loop (now try next permission)
                }
            }
        }

        if (firstRejection != null) {
            // We found an ACE to reject the request at this point, as no
            // other ACEs were found that granted a different permission
            if (!administrativeMode) {
                auditLogger.logIfNeeded(false, firstRejection);
            }

            return false;
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



}
