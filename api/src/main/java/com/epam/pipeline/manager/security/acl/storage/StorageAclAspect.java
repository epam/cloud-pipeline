package com.epam.pipeline.manager.security.acl.storage;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionProviderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.repository.datastorage.security.StoragePermissionRepository;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Aspect
@Component
@RequiredArgsConstructor
public class StorageAclAspect {

    public static final int READ_MASK = ((AclPermission) AclPermission.READ).getSimpleMask();

    private final GrantPermissionManager permissionManager;
    private final StoragePermissionProviderManager storagePermissionProviderManager;

    @Around("@annotation(com.epam.pipeline.manager.security.acl.storage.StorageAclList)")
    @Transactional(propagation = Propagation.REQUIRED)
    public List<AbstractDataStorage> filterAndSetMaskForStorages(ProceedingJoinPoint proceedingJoinPoint)
            throws Throwable {
        final Set<StoragePermissionRepository.Storage> readAllowedStorages =
                storagePermissionProviderManager.loadReadAllowedStorages();
        return listFrom(proceedingJoinPoint)
                .filter(AbstractDataStorage.class::isInstance)
                .map(AbstractDataStorage.class::cast)
                .map(storage -> withMask(storage, readAllowedStorages))
                .filter(this::isReadAllowed)
                .collect(Collectors.toList());
    }

    private AbstractDataStorage withMask(final AbstractDataStorage storage,
                                         final Set<StoragePermissionRepository.Storage> readAllowedStorages) {
        int mask = permissionManager.getPermissionsMask(storage, true, true);
        if (readAllowedStorages.contains(new StoragePermissionRepository.StorageImpl(
                storage.getId(), storage.getKind()))) {
            mask |= READ_MASK;
        }
        storage.setMask(mask);
        return storage;
    }

    private boolean isReadAllowed(final AbstractDataStorage storage) {
        return (storage.getMask() & READ_MASK) == READ_MASK;
    }

    @Around("@annotation(com.epam.pipeline.manager.security.acl.storage.StorageAclDelegateList)")
    @Transactional(propagation = Propagation.REQUIRED)
    public List<DataStorageWithShareMount> filterAndSetMaskForStorageDelegates(ProceedingJoinPoint proceedingJoinPoint)
            throws Throwable {
        final Set<StoragePermissionRepository.Storage> readAllowedStorages =
                storagePermissionProviderManager.loadReadAllowedStorages();
        return listFrom(proceedingJoinPoint)
                .filter(DataStorageWithShareMount.class::isInstance)
                .map(DataStorageWithShareMount.class::cast)
                .map(storage -> withMask(storage, readAllowedStorages))
                .filter(this::isReadAllowed)
                .collect(Collectors.toList());
    }

    private Stream<Object> listFrom(final ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        return Optional.ofNullable(proceedingJoinPoint.proceed())
                .filter(List.class::isInstance)
                .<List<Object>>map(List.class::cast)
                .map(List::stream)
                .orElseGet(Stream::empty);
    }

    private DataStorageWithShareMount withMask(final DataStorageWithShareMount storage,
                                               final Set<StoragePermissionRepository.Storage> readAllowedStorages) {
        storage.setStorage(withMask(storage.getStorage(), readAllowedStorages));
        return storage;
    }

    private boolean isReadAllowed(final DataStorageWithShareMount storage) {
        return isReadAllowed(storage.getStorage());
    }
}
