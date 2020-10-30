const fs = require('fs');
const path = require('path');
const axiosPackageJsonConfig = path.join(__dirname, '../node_modules/axios/package.json');
const linkKey = './lib/adapters/http.js';
const linkValue = './lib/adapters/xhr.js';

const packageJson = JSON.parse(fs.readFileSync(axiosPackageJsonConfig).toString());
if (
  packageJson.hasOwnProperty('browser') &&
  packageJson.browser.hasOwnProperty(linkKey) &&
  packageJson.browser[linkKey] === linkValue
) {
  delete packageJson.browser[linkKey];
  console.log('axios package.json: browser link', linkKey, 'to', linkValue, 'removed');
  const data = Buffer.from(JSON.stringify(packageJson, undefined, '  '));
  fs.writeFileSync(axiosPackageJsonConfig, data);
}
