import {useCallback, useEffect, useRef, useState} from 'react';
import type {CheckboxChangeEvent} from 'antd/es/checkbox';
import {useQuery} from '@tanstack/react-query';
import {rolesQueryOptions, usersInfoQueryOptions} from '../../../queries';
import {useAuthenticatedUser} from '../../../stores/users/hooks.ts';
import {
  findGroupsByPrefix,
  findUsersByPrefix,
  loadAllPermissions,
  loadGrant,
} from '../../../api/users/grants-api.ts';
import roleModel from '../../../utils/roleModel.jsx';
import {
  applyPermissionChanges,
  filterRemovePermissionBySid,
  findPermissionByPermission,
  findPermissionBySidFn,
  getPermissionChanges,
  getPermissionsHash,
  permissionSidsEqual,
} from '../../roleModel/utilities/permissions.js';
import compareSubObjects from '../../roleModel/utilities/compare-sub-objects.js';
import type {
  MaskRule,
  Permission,
  PermissionColumn,
  PermissionSid,
  PermissionsFormProps,
  PermissionType,
  SubObjectPermissions,
  SubObjectToCheck,
} from './types.ts';
import {PERMISSION_COLUMNS, PERMISSIONS} from './types.ts';
import type {UserInfo, RoleInfo} from '../../../@types/users.ts';

const ALL_ALLOWED_MASK = roleModel.buildPermissionsMask(1, 1, 1, 1, 1, 1);

function findMaskForSubject(
  config: number | MaskRule[] | undefined,
  subject: string,
  isPrincipal: boolean,
  defaultMask = 0,
): number {
  if (typeof config === 'number') {
    return config;
  }
  if (config && Array.isArray(config)) {
    const all = config.find((aMask) => /^all$/i.test(aMask.role));
    const rule = config.find(
      (aMask) => !isPrincipal && (subject || '').toLowerCase() === (aMask.role || '').toLowerCase(),
    );
    if (rule) return rule.mask;
    if (all) return all.mask;
  }
  return defaultMask;
}

function splitRoleName(name: string): string {
  if (name && name.toLowerCase().indexOf('role_') === 0) {
    return name.substring('role_'.length);
  }
  return name;
}

export type PermissionsFormController = ReturnType<typeof usePermissionsFormController>;

export function usePermissionsFormController(props: PermissionsFormProps) {
  const {
    objectIdentifier,
    objectType,
    readonly = false,
    defaultMask,
    enabledMask = ALL_ALLOWED_MASK,
    readOnlyRoles = [],
    subObjectsPermissionsMaskToCheck = 0,
    subObjectsToCheck,
    refreshPermissionsAfterUpdate = false,
    permissionsColumns = [PERMISSION_COLUMNS.allow, PERMISSION_COLUMNS.deny],
    availablePermissions = [PERMISSIONS.read, PERMISSIONS.write, PERMISSIONS.execute],
    editOwnerAvailable = false,
    showOwner = true,
  } = props;

  const {data: allUsers = [], isSuccess: allUsersLoaded} = useQuery(usersInfoQueryOptions());
  const {data: roles = []} = useQuery(rolesQueryOptions());
  const currentUser = useAuthenticatedUser();

  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [originalPermissions, setOriginalPermissions] = useState<Permission[]>([]);
  const [owner, setOwner] = useState<string | undefined>();
  const [originalOwner, setOriginalOwner] = useState<string | undefined>();
  const [ownerInput, setOwnerInput] = useState<string | undefined>();
  const [selectedPermission, setSelectedPermission] = useState<Permission | undefined>();
  const [subObjectsPermissions, setSubObjectsPermissions] = useState<SubObjectPermissions[]>([]);

  // Find user dialog
  const [findUserVisible, setFindUserVisible] = useState(false);
  const [selectedUser, setSelectedUser] = useState<string | undefined>();
  const [searchUserTouched, setSearchUserTouched] = useState(false);

  // Find group dialog
  const [findGroupVisible, setFindGroupVisible] = useState(false);
  const [groupSearchString, setGroupSearchString] = useState<string | undefined>();
  const [groupResults, setGroupResults] = useState<string[]>([]);
  const selectedGroupRef = useRef<string | undefined>(undefined);

  // Owner autocomplete
  const [fetchedUsers, setFetchedUsers] = useState<UserInfo[]>([]);
  const ownerFetchIdRef = useRef<object>({});

  const tokenRef = useRef<object>({});

  const permissionsChanged =
    getPermissionsHash(permissions) !== getPermissionsHash(originalPermissions) ||
    owner !== originalOwner;

  const permissionsAreReadOnly = (readOnlyRoles as string[]).some((r) => /^all$/i.test(r));

  const subjectIsReadOnly = useCallback(
    (subject: string, isPrincipal: boolean): boolean => {
      if (permissionsAreReadOnly) return true;
      if (isPrincipal) return false;
      return (readOnlyRoles as string[]).some((r) => r.toLowerCase() === subject.toLowerCase());
    },
    [permissionsAreReadOnly, readOnlyRoles],
  );

  const getDefaultMaskForSubject = useCallback(
    (subject: string, isPrincipal: boolean): number => {
      if (subjectIsReadOnly(subject, isPrincipal)) return 0;
      return findMaskForSubject(defaultMask, subject, isPrincipal, 0);
    },
    [defaultMask, subjectIsReadOnly],
  );

  const getEnabledMaskForSubject = useCallback(
    (subject: string, isPrincipal: boolean): number => {
      if (subjectIsReadOnly(subject, isPrincipal)) return 0;
      return findMaskForSubject(enabledMask, subject, isPrincipal, ALL_ALLOWED_MASK);
    },
    [enabledMask, subjectIsReadOnly],
  );

  const selectFirstPermission = useCallback((perms: Permission[]) => {
    if (perms.length > 0) {
      setSelectedPermission(perms[0]);
    }
  }, []);

  const loadPermissions = useCallback(() => {
    if (!objectIdentifier || !objectType) return;
    const token = {};
    tokenRef.current = token;
    setSelectedPermission(undefined);
    setPending(true);
    setError(undefined);
    setPermissions([]);
    setOriginalPermissions([]);
    setOriginalOwner(undefined);
    setOwner(undefined);
    setOwnerInput(undefined);

    (async () => {
      try {
        const result = await loadGrant(objectIdentifier, objectType);
        if (tokenRef.current !== token) return;
        const perms = (result.permissions || []).map((o) => ({...o}));
        const entityOwner = result.entity?.owner;
        setPermissions(perms);
        setOriginalPermissions(perms.map((o) => ({...o})));
        setOriginalOwner(entityOwner);
        setOwner(entityOwner);
        setPending(false);
        setError(undefined);
        selectFirstPermission(perms);
      } catch (err) {
        if (tokenRef.current !== token) return;
        setPending(false);
        setError(err instanceof Error ? err.message : 'Error fetching permissions');
      }
    })();
  }, [objectIdentifier, objectType, selectFirstPermission]);

  const fetchSubObjectsPermissions = useCallback(() => {
    if (!subObjectsToCheck?.length) {
      setSubObjectsPermissions([]);
      return;
    }
    setSubObjectsPermissions([]);
    const tasks = subObjectsToCheck.map(async (subObject): Promise<SubObjectPermissions> => {
      try {
        const result = await loadAllPermissions(subObject.entityId, subObject.entityClass);
        return {
          object: subObject,
          permissions: result.permissions ?? [],
          owner: result.entity?.owner,
        };
      } catch {
        return {object: subObject, permissions: []};
      }
    });
    Promise.all(tasks).then(setSubObjectsPermissions);
  }, [subObjectsToCheck]);

  useEffect(() => {
    loadPermissions();
    return () => {
      tokenRef.current = {};
    };
  }, [loadPermissions]);

  const prevSubObjectsRef = useRef<SubObjectToCheck[] | undefined>(undefined);
  useEffect(() => {
    if (!compareSubObjects(subObjectsToCheck, prevSubObjectsRef.current)) {
      fetchSubObjectsPermissions();
    }
    prevSubObjectsRef.current = subObjectsToCheck;
  }, [subObjectsToCheck, fetchSubObjectsPermissions]);

  const grantPermission = useCallback((name: string, principal: boolean, mask: number) => {
    setPermissions((prev) => {
      const sid: PermissionSid = {name, principal};
      const next = prev.slice();
      const idx = next.findIndex((p) => permissionSidsEqual(p.sid, sid));
      if (idx >= 0) {
        next.splice(idx, 1, {sid, mask});
      } else {
        next.push({sid, mask});
      }
      const sel = next.find(findPermissionBySidFn(sid));
      setSelectedPermission(sel);
      return next;
    });
  }, []);

  const removeUserOrGroup = useCallback(
    (item: Permission) => (event: React.MouseEvent) => {
      event.stopPropagation();
      setPermissions((prev) => {
        const next = prev.filter(filterRemovePermissionBySid(item.sid));
        setSelectedPermission((sel) => {
          if (!sel || permissionSidsEqual(sel.sid, item.sid)) {
            if (next.length > 0) return next[0];
            return undefined;
          }
          return sel;
        });
        return next;
      });
    },
    [],
  );

  const onAllowDenyValueChanged =
    (permissionMask: number, allowDenyMask: number, allowRead = false) =>
    (event: CheckboxChangeEvent) => {
      const clearMask = (1 | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5)) ^ permissionMask;
      const newValue = event.target.checked ? allowDenyMask : 0;
      setSelectedPermission((sel) => {
        if (!sel) return sel;
        let currentMask = (sel.mask & clearMask) | newValue;
        if (allowRead && event.target.checked) {
          currentMask = (currentMask & ((1 << 2) | (1 << 3) | (1 << 4) | (1 << 5))) | 1;
        }
        grantPermission(sel.sid.name, sel.sid.principal, currentMask);
        return {...sel, mask: currentMask};
      });
    };

  const revertChanges = useCallback(() => {
    const reverted = originalPermissions.map((o) => ({...o}));
    setPermissions(reverted);
    setOwner(originalOwner);
    setOwnerInput(undefined);
    setSelectedPermission((sel) =>
      sel ? reverted.find(findPermissionByPermission(sel)) : undefined,
    );
  }, [originalOwner, originalPermissions]);

  const applyChanges = useCallback(() => {
    const changes = getPermissionChanges({owner, originalOwner, permissions, originalPermissions});
    if (!changes.changed) return;
    setPending(true);
    (async () => {
      const success = await applyPermissionChanges(changes, objectIdentifier, objectType);
      setPending(false);
      if (success) {
        if (refreshPermissionsAfterUpdate) {
          loadPermissions();
        } else {
          const saved = permissions.map((o) => ({...o}));
          setOriginalPermissions(saved);
          setOriginalOwner(owner);
          setError(undefined);
          selectFirstPermission(saved);
        }
      }
    })();
  }, [
    owner,
    originalOwner,
    permissions,
    originalPermissions,
    objectIdentifier,
    objectType,
    refreshPermissionsAfterUpdate,
    loadPermissions,
    selectFirstPermission,
  ]);

  // Owner search
  const findOwnerUser = useCallback((value: string) => {
    const fetchId = {};
    ownerFetchIdRef.current = fetchId;
    setOwnerInput(value);
    (async () => {
      try {
        const users = await findUsersByPrefix(value);
        if (ownerFetchIdRef.current !== fetchId) return;
        setFetchedUsers(users || []);
      } catch {
        if (ownerFetchIdRef.current !== fetchId) return;
        setFetchedUsers([]);
      }
    })();
  }, []);

  const onOwnerSelect = useCallback(
    (key: string) => {
      const user = fetchedUsers.find((u) => `${u.id}` === `${key}`);
      if (user) {
        setOwnerInput(user.userName);
        setOwner(user.userName);
      }
    },
    [fetchedUsers],
  );

  // Group search
  const onGroupSearchChanged = useCallback(async (value: string) => {
    selectedGroupRef.current = value;
    setGroupSearchString(value);
    if (!value) {
      setGroupResults([]);
      return;
    }
    try {
      const groups = await findGroupsByPrefix(value);
      setGroupResults(groups || []);
    } catch {
      setGroupResults([]);
    }
  }, []);

  const findGroupDataSource = useCallback((): string[] => {
    const existingGroups = new Set(
      permissions.filter((p) => p.sid && !p.sid.principal).map((p) => p.sid.name),
    );
    const matchingRoles = groupSearchString
      ? roles
          .filter((r) => r.name.toLowerCase().includes(groupSearchString.toLowerCase()))
          .filter((r) => !existingGroups.has(r.name))
          .map((r) => (r.predefined ? r.name : splitRoleName(r.name)))
      : [];
    return [...matchingRoles, ...groupResults];
  }, [permissions, groupSearchString, roles, groupResults]);

  const onSelectUser = useCallback(() => {
    if (!selectedUser) return;
    grantPermission(selectedUser, true, getDefaultMaskForSubject(selectedUser, true));
    setFindUserVisible(false);
    setSelectedUser(undefined);
  }, [selectedUser, grantPermission, getDefaultMaskForSubject]);

  const onSelectGroup = useCallback(() => {
    const group = selectedGroupRef.current;
    if (!group) return;
    const role = roles.find((r) => !r.predefined && splitRoleName(r.name) === group);
    const roleName = role ? role.name : group;
    grantPermission(roleName, false, getDefaultMaskForSubject(roleName, false));
    setFindGroupVisible(false);
    setGroupSearchString(undefined);
    selectedGroupRef.current = undefined;
  }, [roles, grantPermission, getDefaultMaskForSubject]);

  const isAdminOrOwner =
    editOwnerAvailable || currentUser?.admin || originalOwner === currentUser?.userName;

  return {
    // data
    allUsers,
    allUsersLoaded,
    roles,
    currentUser,
    permissions,
    selectedPermission,
    owner,
    ownerInput,
    originalOwner,
    fetchedUsers,
    subObjectsPermissions,
    pending,
    error,
    permissionsChanged,
    isAdminOrOwner,
    // find-user dialog
    findUserVisible,
    selectedUser,
    searchUserTouched,
    // find-group dialog
    findGroupVisible,
    groupSearchString,
    selectedGroupRef,
    findGroupDataSource,
    // prop pass-throughs
    readonly,
    permissionsAreReadOnly,
    showOwner,
    permissionsColumns,
    availablePermissions,
    subObjectsPermissionsMaskToCheck,
    // handlers
    setSelectedPermission,
    setFindUserVisible,
    setSelectedUser,
    setSearchUserTouched,
    setFindGroupVisible,
    setOwnerInput,
    grantPermission,
    removeUserOrGroup,
    onAllowDenyValueChanged,
    revertChanges,
    applyChanges,
    findOwnerUser,
    onOwnerSelect,
    onGroupSearchChanged,
    onSelectUser,
    onSelectGroup,
    subjectIsReadOnly,
    getEnabledMaskForSubject,
    splitRoleName,
  };
}
