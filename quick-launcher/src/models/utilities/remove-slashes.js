export default function removeExtraSlash (path, options = {}) {
  const {
    removeLeading = true,
    removeTrailing = true
  } = options;
  if (removeLeading && path.startsWith('/')) {
    path = path.slice(1);
  }
  if (removeTrailing && path.endsWith('/')) {
    path = path.slice(0, -1);
  }
  return path;
}
