/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

const fs = require('fs');
const path = require('path');
const {CLIEngine} = require('eslint');

const eslintCLI = new CLIEngine({
  useEslintrc: true,
  fix: true
});

const eslintLine = `/* eslint-disable max-len */`;

const copyright = `/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */`;

function help () {
  console.log(
    `node ${path.basename(__filename)} theme-variables.less theme-identifier [destination-folder] [name] [base-theme]`
  );
}

const ignore = [
  /^icons-root$/
];

function generate (...args) {
  const [source, identifier, destinationPath = '.', name = identifier, baseTheme] = args;
  if (!source || !/\.less$/i.test(source)) {
    console.log('you should specify source file (.less)');
    help();
    process.exit(1);
  }
  if (!identifier) {
    console.log('you should specify theme identifier');
    help();
    process.exit(1);
  }
  const sourcePath = path.resolve(__dirname, source);
  const destination = path.resolve(__dirname, destinationPath);
  if (!fs.existsSync(sourcePath)) {
    console.log(sourcePath);
    console.log('source file does not exist');
    process.exit(1);
  }

  try {
    const content = fs.readFileSync(sourcePath).toString();
    const varRegExp = /^\s*@([^:]+)\s*:\s*(.*)\s*;\s*$/gm;
    let e = varRegExp.exec(content);
    const configuration = {};
    while (e) {
      let [, variable, value] = e;
      if (/^url\('@\{icons-root\}/i.test(value)) {
        value = value.replace('url(\'@{icons-root}', '@static_resource(\'icons');
      } else if (/^url\('@\{public-root\}/i.test(value)) {
        value = value.replace('url(\'@{public-root}', '@static_resource(\'');
      }
      if (!ignore.some(o => o.test(variable))) {
        configuration[`@${variable}`] = value;
      }
      e = varRegExp.exec(content);
    }
    let themeFileContents = `${copyright}\n\n${eslintLine}\n\nexport default {
  identifier: '${identifier}',
  name: '${name}',
  extends: ${baseTheme ? `'${baseTheme}'` : 'undefined'},
  predefined: true,
  configuration: ${JSON.stringify(configuration, undefined, ' ')}
};`;
    themeFileContents = eslintCLI.executeOnText(themeFileContents).results.pop().output;
    const destinationFolder = path.resolve(destination, identifier);
    fs.mkdirSync(destinationFolder, {recursive: true});
    fs.writeFileSync(path.resolve(destinationFolder, 'index.js'), Buffer.from(themeFileContents));
    console.log(identifier, 'theme created');
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}

module.exports = generate;
