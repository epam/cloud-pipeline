export default function combineUrl(root, uri) {
  if (!uri) {
    return root;
  }
  const rootCorrected = root && !root.endsWith('/')
    ? `${root}/`
    : (root || '');
  const uriCorrected = uri && !uri.startsWith('/')
    ? uri
    : uri.substr(1);
  return `${rootCorrected}${uriCorrected}`;
}
