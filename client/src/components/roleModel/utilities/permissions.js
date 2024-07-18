import {message} from 'antd';
import GrantOwner from '../../../models/grant/GrantOwner';
import GrantPermission from '../../../models/grant/GrantPermission';
import GrantRemove from '../../../models/grant/GrantRemove';

function getSidHash (sid) {
  const {
    name,
    principal
  } = sid;
  return `${principal ? 'user' : 'group'}|${name}`;
}

function getPermissionHash (permission) {
  return `${getSidHash(permission.sid)}|${permission.mask}`;
}

export function getPermissionsHash (permissions = []) {
  return permissions.map(getPermissionHash).sort().join('\n');
}

export function permissionSidsEqual (sid1, sid2) {
  if (!sid1 && !sid2) {
    return true;
  }
  if (!sid1 || !sid2) {
    return false;
  }
  return sid1.name === sid2.name && sid1.principal === sid2.principal;
}

export function findPermissionBySidFn (sid) {
  return (permission) => permissionSidsEqual(permission.sid, sid);
}

export function findPermissionByPermission (permission) {
  if (!permission) {
    return () => false;
  }
  return (p) => permissionSidsEqual(p.sid, permission.sid);
}

export function filterRemovePermissionBySid (sid) {
  return (p) => !permissionSidsEqual(p.sid, sid);
}

export function filterRemovePermissionByPermission (permission) {
  if (!permission) {
    return () => true;
  }
  return (p) => !permissionSidsEqual(p.sid, permission.sid);
}

export function getPermissionChanges (state) {
  const {
    owner,
    originalOwner,
    permissions,
    originalPermissions
  } = state;
  const updateOwner = originalOwner !== owner && owner !== undefined;
  const removedPermissions = originalPermissions
    .filter(p => !permissions.some(findPermissionByPermission(p)));
  const updatedPermissions = permissions
    .filter(p => originalPermissions.some(findPermissionByPermission(p)));
  const createdPermissions = permissions
    .filter(p => !originalPermissions.some(findPermissionByPermission(p)));
  return {
    owner: updateOwner ? owner : undefined,
    removedPermissions,
    updatedPermissions,
    createdPermissions,
    changed: updateOwner ||
      removedPermissions.length > 0 ||
      updatedPermissions.length > 0 ||
      createdPermissions.length > 0
  };
}

async function applyOwnerChange (owner, objectIdentifier, objectType) {
  if (!owner || !objectIdentifier || !objectType) {
    return;
  }
  console.log(`Changing owner of ${objectType} #${objectIdentifier} to ${owner}`);
  const request = new GrantOwner(objectIdentifier, objectType.toUpperCase(), owner);
  await request.send({});
  if (request.error) {
    console.log(request.error);
    throw new Error(request.error);
  }
}

async function removePermission (permission, objectIdentifier, objectType) {
  if (!objectIdentifier || !objectType) {
    return;
  }
  const {
    sid
  } = permission;
  const {
    principal,
    name
  } = sid;
  // eslint-disable-next-line max-len
  console.log(`Removing ${name} ${principal ? 'user' : 'group'} permission of ${objectType} #${objectIdentifier}`);
  const request = new GrantRemove(
    objectIdentifier,
    objectType.toUpperCase(),
    name,
    principal
  );
  await request.fetch();
  if (request.error) {
    console.log(request.error);
    throw new Error(request.error);
  }
}

async function changePermission (permission, objectIdentifier, objectType) {
  if (!objectIdentifier || !objectType) {
    return;
  }
  const {
    sid,
    mask
  } = permission;
  const {
    principal,
    name
  } = sid;
  // eslint-disable-next-line max-len
  console.log(`Changing ${name} ${principal ? 'user' : 'group'} permission of ${objectType} #${objectIdentifier}, new mask: ${mask} (${Number(mask).toString(2).padStart(8, '0')})`);
  const request = new GrantPermission();
  await request.send({
    aclClass: objectType.toUpperCase(),
    id: objectIdentifier,
    mask,
    principal,
    userName: name
  });
  if (request.error) {
    console.log(request.error);
    throw new Error(request.error);
  }
}

async function processPermissions (permissions, objectIdentifier, objectType, processor) {
  if (!objectIdentifier || !objectType) {
    return;
  }
  const changePermissionByIndex = async (index = 0) => {
    if (index >= permissions.length) {
      return;
    }
    await processor(permissions[index], objectIdentifier, objectType);
    await changePermissionByIndex(index + 1);
  };
  await changePermissionByIndex();
}

async function removePermissions (permissions, objectIdentifier, objectType) {
  await processPermissions(permissions, objectIdentifier, objectType, removePermission);
}

async function changePermissions (permissions, objectIdentifier, objectType) {
  await processPermissions(permissions, objectIdentifier, objectType, changePermission);
}

export async function applyPermissionChanges (changes, objectIdentifier, objectType) {
  const {
    owner,
    removedPermissions,
    createdPermissions,
    updatedPermissions
  } = changes;
  const hide = message.loading('Updating permissions...', -1);
  try {
    await applyOwnerChange(owner, objectIdentifier, objectType);
    await changePermissions(
      [].concat(createdPermissions).concat(updatedPermissions),
      objectIdentifier,
      objectType
    );
    await removePermissions(removedPermissions, objectIdentifier, objectType);
    return true;
  } catch (error) {
    message.error(error.message, 5);
    return false;
  } finally {
    hide();
  }
}
