/**
 * Adds leading & trailing slash to the path, i.e. "/a" -> "/a/", "b" -> "/b/"
 * @param {string} test
 * @param {{trailingSlash: boolean?, leadingSlash: boolean?, separator: string?}} options
 * @returns {string}
 */
module.exports = function correctDirectoryPath(test = '', options = {}) {
  const {
    trailingSlash = true,
    leadingSlash = true,
    separator = '/',
  } = options;
  if (test === separator) {
    return test;
  }
  let result = test;
  if (trailingSlash && !result.endsWith(separator)) {
    result = result.concat(separator);
  }
  if (leadingSlash && !result.startsWith(separator)) {
    result = separator.concat(result);
  }
  return result;
};
