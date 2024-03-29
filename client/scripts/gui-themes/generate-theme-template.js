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

const ignores = [
  /(^|\/|\\)animation.less$/
];

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

function removeComments (cssContent) {
  return cssContent.replace(/\/\*[\S\s]*\*\//gm, '');
}

function removeImports (cssContent) {
  return cssContent.replace(/^@import .*$/gm, '');
}

const themesDirectory = path.resolve(__dirname, '../../src/themes');

function attachThemePlaceholder (selector) {
  const trimmed = selector.trim();
  const e = /^\.theme-identifier(\..+|\s.+|$)/.exec(trimmed);
  if (e) {
    console.log('HERE', e[1]);
    return `@THEME${e[1]}`;
  }
  return `@THEME ${trimmed}`;
}

module.exports = function generateThemeTemplate () {
  const themeContents = removeComments(
    fs.readFileSync(path.resolve(themesDirectory, 'default.theme.less'))
      .toString()
  );
  const importRegExp = /@import ["'](.+)["'];/g;
  let importExec = importRegExp.exec(themeContents);
  const imports = [];
  while (importExec) {
    if (importExec) {
      if (!/\.less$/i.test(importExec[1])) {
        imports.push(importExec[1] + '.less');
      } else {
        imports.push(importExec[1]);
      }
    }
    importExec = importRegExp.exec(themeContents);
  }

  let resultedCss = '';

  const selectorsRegExp = /^[\s]*([^{]+)\s*({[^}]+})/m;

  for (const filename of imports) {
    const filePath = path.resolve(themesDirectory, filename);
    if (ignores.some(o => o.test(filePath))) {
      console.log('skipping file', filename, ' - ignored');
      continue;
    }
    if (fs.existsSync(filePath)) {
      console.log('merging file', filename);
      let content = removeImports(removeComments(fs.readFileSync(filePath).toString()));
      let modified = '';
      let e = selectorsRegExp.exec(content);
      while (e) {
        const selectors = (e[1] || '').split(',').map(attachThemePlaceholder).join(',\n');
        modified = modified.concat(
          selectors,
          ' ',
          e[2],
          '\n'
        );
        content = content.slice(e.index + e[0].length);
        e = selectorsRegExp.exec(content);
      }
      modified = modified.concat(content);
      resultedCss = resultedCss.concat(modified);
    } else {
      console.log('unknown import:', filename);
    }
  }

  const resultFilePath = path.resolve(themesDirectory, 'utilities/theme.less.template.js');

  const template = `${copyright}\n\nexport default \`\n${resultedCss}\`;\n`;

  console.log('writing resulted content to', resultFilePath);

  fs.writeFileSync(resultFilePath, Buffer.from(template));
};
