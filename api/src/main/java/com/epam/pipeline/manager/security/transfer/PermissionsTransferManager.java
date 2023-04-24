package com.epam.pipeline.manager.security.transfer;

import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.SecuredEntityTransferManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionsTransferManager implements SecuredEntityTransferManager {

    private final GrantPermissionManager grantPermissionManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void transfer(final AbstractSecuredEntity source, final AbstractSecuredEntity target) {
        final AclSecuredEntry sourcePermissions = grantPermissionManager.getPermissions(source.getId(),
                source.getAclClass());
        for (final AclPermissionEntry sourcePermission : sourcePermissions.getPermissions()) {
            final PermissionGrantVO targetPermissionGrantVO = new PermissionGrantVO();
            targetPermissionGrantVO.setId(target.getId());
            targetPermissionGrantVO.setAclClass(target.getAclClass());
            targetPermissionGrantVO.setUserName(sourcePermission.getSid().getName());
            targetPermissionGrantVO.setPrincipal(sourcePermission.getSid().isPrincipal());
            targetPermissionGrantVO.setMask(sourcePermission.getMask());
            grantPermissionManager.setPermissions(targetPermissionGrantVO);
        }
    }

}
