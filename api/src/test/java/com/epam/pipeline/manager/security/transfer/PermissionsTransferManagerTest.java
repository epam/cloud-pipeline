package com.epam.pipeline.manager.security.transfer;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID_2;
import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PermissionsTransferManagerTest {

    private static final AbstractSecuredEntity SOURCE = SecurityCreatorUtils.getEntity(ID, AclClass.DATA_STORAGE);
    private static final AbstractSecuredEntity TARGET = SecurityCreatorUtils.getEntity(ID_2, AclClass.PIPELINE);
    private static final AclSecuredEntry ACL_SECURED_ENTRY = SecurityCreatorUtils.getAclSecuredEntry(SOURCE);

    private final GrantPermissionManager grantPermissionManager = mock(GrantPermissionManager.class);
    private final PermissionsTransferManager manager = new PermissionsTransferManager(grantPermissionManager);

    @Test
    public void transferShouldTransferPermissionsFromSourceToTarget() {
        doReturn(ACL_SECURED_ENTRY)
                .when(grantPermissionManager).getPermissions(SOURCE.getId(), SOURCE.getAclClass());

        manager.transfer(SOURCE, TARGET);

        for (final AclPermissionEntry expectedPermission : ACL_SECURED_ENTRY.getPermissions()) {
            verify(grantPermissionManager).setPermissions(argThat(matches(actualPermission ->
                    TARGET.getId().equals(actualPermission.getId())
                            && TARGET.getAclClass().equals(actualPermission.getAclClass())
                            && expectedPermission.getSid().getName().equals(actualPermission.getUserName())
                            && expectedPermission.getMask().equals(actualPermission.getMask()))));
        }
    }
}
