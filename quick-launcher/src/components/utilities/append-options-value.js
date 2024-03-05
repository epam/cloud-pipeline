export default function appendOptionsValue(options, path, value) {
  if (!path) {
    return options;
  }
  const [root, ...subPath] = path.split('.');
  if (subPath.length === 0) {
    options[root] = value;
    return options;
  }
  options[root] = appendOptionsValue(options[root] || {}, subPath.join('.'), value);
  return options;
}
