export default function asArray(element) {
  if (typeof element === 'undefined') {
    return [];
  }
  if (Array.isArray(element)) {
    return element;
  }
  return [element];
}
