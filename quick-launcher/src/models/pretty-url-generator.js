import processString from './process-string';

function removeExtraSlash (string) {
  if (!string) {
    return string;
  }
  return string.replace(/\/[\/]+/g, '/').replace(/[\/]+$/, '');
}

export default function generate(domain, path, options) {
  const result = {};
  if (domain) {
    result.domain = removeExtraSlash(processString(domain, options));
  }
  if (path) {
    result.path = removeExtraSlash(processString(path, options));
  }
  return JSON.stringify(result);
}
