module.exports = function getLastCommitSHA() {
  try {
    return require('child_process')
      .execSync('git rev-parse HEAD')
      .toString()
      .trim()
      .slice(0, 8);
  } catch (_) {}
  return '';
}
