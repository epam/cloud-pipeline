export default function applicationAvailable(appInfo, user, settings) {
  if (!appInfo) {
    return true;
  }
  const {
    users = [],
    user: owner
  } = appInfo || {};
  if (settings && settings.isAnonymous && users.length === 0) {
    return false;
  }
  if (users.length === 0) {
    return true;
  }
  const userToTest = settings?.isAnonymous
    ? (settings.originalUser || user)
    : user;
  const allowAll = !!users.find(o => !o.principal && /^ROLE_ALL$/i.test(o.name));
  const availableForUsers = new Set(users.filter(o => o.principal).map(o => (o.name || '').toUpperCase()));
  const availableForRoles = new Set(users.filter(o => !o.principal).map(o => (o.name || '').toUpperCase()));
  const {
    roles = [],
    userName,
    admin
  } = userToTest || {};
  return admin ||
    allowAll ||
    (owner || '').toUpperCase() === (userName || '').toUpperCase() ||
    availableForUsers.has((userName || '').toUpperCase()) ||
    roles
      .map(o => (o.name || '').toUpperCase())
      .some(role => availableForRoles.has(role));
}
