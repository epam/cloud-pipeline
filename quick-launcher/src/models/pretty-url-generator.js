import processString from './process-string';

function removeExtraSlash (string) {
  if (!string) {
    return string;
  }
  return string.replace(/\/[\/]+/g, '/').replace(/[\/]+$/, '');
}

export default function generate(domain, path, options) {
  const result = {};
  const {
    __validation__ = false
  } = options || {};
  if (domain) {
    result.domain = removeExtraSlash(processString(domain, options));
  }
  if (path) {
    result.path = removeExtraSlash(processString(path, options));
  }
  if (__validation__) {
    result.path = result.path ? `${result.path};validation` : 'validation';
  }
  return JSON.stringify(result);
}
