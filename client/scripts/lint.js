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

// `npm run lint` - eslint over src/, test/ and scripts/, failing only on problems that are not
// recorded in .eslintbaseline.json. `npm run lint:all` shows everything including the recorded
// debt, and `npm run lint:baseline` re-records it.
//
// ESLint 5 has no baseline of its own (`--suppress-all` arrived in ESLint 9), hence this wrapper.

process.on('unhandledRejection', err => {
  throw err;
});

const fs = require('fs');
const path = require('path');
const {CLIEngine} = require('eslint');
const baselines = require('./lint-baseline');

// scripts/ is in here too: this file lives there, and tooling that decides whether a check passes
// should not be the one thing nothing checks.
const TARGETS = ['src', 'test', 'scripts'];
// .jsx is spelled out because ESLint 5 defaults to .js alone, which left every .jsx file here
// unchecked by anything.
const EXTENSIONS = ['.js', '.jsx'];
const BASELINE_FILE = path.resolve(__dirname, '../.eslintbaseline.json');

const update = process.argv.includes('--update');
// --all reports the baselined debt as well, by comparing against nothing.
const all = process.argv.includes('--all');

function filesUnder (dir, found) {
  fs.readdirSync(dir).forEach(entry => {
    const full = path.join(dir, entry);
    if (fs.statSync(full).isDirectory()) {
      filesUnder(full, found);
    } else if (EXTENSIONS.indexOf(path.extname(entry)) >= 0) {
      found.push(full);
    }
  });
  return found;
}

function collect () {
  const engine = new CLIEngine({extensions: EXTENSIONS});
  const problems = [];
  const root = path.resolve(__dirname, '..');
  const files = TARGETS
    .reduce((all, target) => filesUnder(path.join(root, target), all), [])
    .filter(file => !engine.isPathIgnored(file));

  // One file at a time: a rule crashing on a single file would otherwise take the whole run down
  // with it and report nothing at all, which is exactly the failure this script exists to prevent.
  files.forEach(filePath => {
    const file = baselines.relative(filePath);
    let results;
    try {
      results = engine.executeOnFiles([filePath]).results;
    } catch (e) {
      problems.push({
        file,
        rule: baselines.UNRULED,
        line: 0,
        column: 0,
        text: `${e.message.split('\n')[0]} (eslint crashed on this file, so it was not checked)`
      });
      return;
    }
    results.forEach(result => {
      result.messages.forEach(message => {
        problems.push({
          file,
          rule: message.ruleId || baselines.UNRULED,
          line: message.line || 0,
          column: message.column || 0,
          text: message.message
        });
      });
    });
  });

  return problems;
}

const problems = collect();

if (update) {
  baselines.writeBaseline(BASELINE_FILE, baselines.countByFileAndRule(problems));
}

process.exit(baselines.report({
  tool: 'eslint',
  baselineFile: BASELINE_FILE,
  problems,
  baseline: update || all ? {} : baselines.readBaseline(BASELINE_FILE),
  updated: update,
  all
}));
