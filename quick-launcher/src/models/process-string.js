export default function processString(string, options) {
  const keys = Object
    .keys(options || {})
    .filter(key => options[key] !== undefined);
  let result = string || '';
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const replaceRegExp = new RegExp(`\\[${key}\\]`, 'ig');
    result = result.replace(replaceRegExp, options[key] || '');
  }
  return result.replace(/\[host\]/ig, document.location.host);
}
