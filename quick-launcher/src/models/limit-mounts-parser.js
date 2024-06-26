export default function parseLimitMounts(limitMounts, user, currentUser, otherPlaceholders = {}) {
  if (!limitMounts) {
    return {
      result: limitMounts
    };
  }
  const replacements = [
    {
      test: /^user_default_storage$/i,
      replace: user && user.defaultStorageId
        ? `${user.defaultStorageId}`
        : undefined
    },
    {
      test: /^current_user_default_storage$/i,
      replace: currentUser && currentUser.defaultStorageId
        ? `${currentUser.defaultStorageId}`
        : undefined
    },
    ...Object.entries(otherPlaceholders)
      .map(([placeholder, value]) => ({
        test: new RegExp(`^${placeholder}$`, 'i'),
        name: placeholder,
        log: true,
        replace: value
      }))
  ]
    .filter(Boolean);
  const replace = (o) => {
    const match = replacements.find(r => r.test.test(o));
    if (match) {
      return match.replace;
    }
    return o;
  }
  const logReplace = (o) => {
    const match = replacements.find(r => r.test.test(o));
    if (match && match.name && match.log && match.replace) {
      return {[match.name]: match.replace};
    }
    return false;
  }
  const limitMountsParts = limitMounts
    .split(',')
    .map(o => o.trim().toLowerCase());
  const parts = limitMountsParts
    .map(o => replace(o))
    .filter(Boolean);
  const logs = limitMountsParts
    .map(o => logReplace(o))
    .filter(Boolean)
    .reduce((res, cur) => ({...res, ...cur}), {});
  if (parts.length === 0) {
    return {
      result: 'none'
    };
  }
  return {
    result: parts.join(','),
    replacements: logs
  };
}
