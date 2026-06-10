export function getFormItemClassName(rootClass, key) {
  if (key) {
    return `${rootClass} ${key.replace(/\./g, '_')}`;
  }
  return rootClass;
}
