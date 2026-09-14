/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

// `npm run stylelint` - the same baseline treatment as scripts/lint.js, for the stylesheets.
// See the comment at the top of scripts/lint-baseline.js for what the baseline is and why.

process.on('unhandledRejection', err => {
  throw err;
});

const path = require('path');
const stylelint = require('stylelint');
const baselines = require('./lint-baseline');

// One pass per syntax: a single stylelint call takes one parser, and .less needs its own. The LESS
// under src/themes/ is the themable layer, so it is worth checking even though nothing did before.
const PASSES = [
  {files: 'src/**/*.css'},
  {files: 'src/**/*.less', syntax: 'less'}
];

const BASELINE_FILE = path.resolve(__dirname, '../.stylelintbaseline.json');

const update = process.argv.includes('--update');
// --all reports the baselined debt as well, by comparing against nothing.
const all = process.argv.includes('--all');

function collect () {
  return PASSES
    .reduce(
      (chain, pass) => chain.then(problems => stylelint
        .lint({files: pass.files, syntax: pass.syntax})
        .then(({results}) => {
          results.forEach(result => {
            const file = baselines.relative(result.source);
            // A stylesheet stylelint could not parse was not checked, so it must never be
            // baselined away - give it the unruled key, which the baseline refuses to cover.
            (result.warnings || []).forEach(warning => {
              const rule = warning.rule || baselines.UNRULED;
              problems.push({
                file,
                rule,
                line: warning.line || 0,
                column: warning.column || 0,
                // stylelint appends the rule name to the message; the report prints it separately
                text: warning.text.replace(new RegExp(`\\s*\\(${rule}\\)$`), '')
              });
            });
            (result.parseErrors || []).forEach(error => {
              problems.push({
                file,
                rule: baselines.UNRULED,
                line: error.line || 0,
                column: error.column || 0,
                text: `${error.text} (could not be parsed, so it was not checked)`
              });
            });
          });
          return problems;
        })),
      Promise.resolve([])
    );
}

collect().then(problems => {
  if (update) {
    baselines.writeBaseline(BASELINE_FILE, baselines.countByFileAndRule(problems));
  }

  process.exit(baselines.report({
    tool: 'stylelint',
    baselineFile: BASELINE_FILE,
    problems,
    baseline: update || all ? {} : baselines.readBaseline(BASELINE_FILE),
    updated: update,
    all
  }));
});
