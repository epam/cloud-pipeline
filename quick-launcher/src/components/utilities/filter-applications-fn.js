function removeSlashes (string = '') {
  if (!string) {
    return '';
  }
  if (string.startsWith('/')) {
    string = string.slice(1);
  }
  if (string.endsWith('/')) {
    string = string.slice(0, -1);
  }
  return string.toLowerCase();
}

export default function filterAppFn (filter) {
  const path = removeSlashes(filter);
  return function (app) {
    return !filter ||
      (app.name || '').toLowerCase().includes(filter.toLowerCase()) ||
      (app.description || '').toLowerCase().includes(filter.toLowerCase()) ||
      (app.path && removeSlashes(app.path) === path) ||
      (app.info?.path && removeSlashes(app.info?.path) === path) ||
      (app.info?.user || '').toLowerCase().includes(filter.toLowerCase()) ||
      !!Object.values(app.info?.ownerInfo || {})
        .map(o => o.toLowerCase())
        .find(o => o.includes(filter.toLowerCase()));
  };
}
