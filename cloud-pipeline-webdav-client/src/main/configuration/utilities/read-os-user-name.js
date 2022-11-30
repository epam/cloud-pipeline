const { exec } = require('child_process');

function readFromEnvironmentVariable() {
  return process.env.USER || process.env.USERNAME;
}

function executeScript(script) {
  return new Promise((resolve) => {
    exec(script, (error, stdout) => {
      if (error) {
        resolve(undefined);
      } else {
        resolve(stdout);
      }
    });
  });
}

function removeLineBreak(s) {
  if (s) {
    return s.replace(/[\r]?\n$/, '');
  }
  return s;
}

function getUserNameWithoutDomain(userName) {
  if (userName) {
    const domainNameExec = /^.*\\(.*)$/.exec(userName);
    const withoutDomain = domainNameExec ? domainNameExec[1] : userName;
    const emailExec = /^(.*)@.*$/.exec(withoutDomain);
    return (emailExec ? emailExec[1] : withoutDomain).toUpperCase();
  }
  return userName;
}

module.exports = async function getUserName() {
  let userName = readFromEnvironmentVariable();
  if (!userName) {
    if (/^win/i.test(process.platform)) {
      const winUserName = await executeScript('whoami');
      userName = removeLineBreak(winUserName);
    } else {
      const oUserName = await executeScript('id -un');
      userName = removeLineBreak(oUserName);
    }
  }
  return getUserNameWithoutDomain(userName);
};
