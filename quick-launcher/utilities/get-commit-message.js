module.exports = function getCommitMessage(commit) {
  try {
    return require('child_process')
      .execSync(`git log --format=%B -n 1 ${commit}`)
      .toString()
      .trim()
      .split(/\r?\n/)[0];
  } catch (_) {}
  return '';
}
