export default function parseStoragePlaceholder (placeholder, user, currentUser) {
  let dataStorageId;
  if (placeholder) {
    dataStorageId = placeholder;
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
    ]
      .filter(o => o.replace);
    const rule = replacements.find(r => r.test.test(placeholder));
    if (rule) {
      dataStorageId = rule.replace;
    }
  }
  return dataStorageId;
}
