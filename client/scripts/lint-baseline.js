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

// Shared baseline handling for scripts/lint.js and scripts/stylelint.js.
//
// src/ carries a large amount of pre-existing lint debt. Rather than soften a rule or stop
// checking the files that carry it, the debt is recorded here and the run fails only on problems
// that are *not* recorded - so every rule keeps its full severity for new code, and for new code
// added to an old file.
//
// A baseline is a count per file per rule:
//
//   {"src/components/tools/Tool.js": {"max-len": 12, "indent": 2}}
//
// Counts rather than line numbers or message text, because those move on every unrelated edit and
// would make the baseline conflict constantly.

const fs = require('fs');
const path = require('path');

const NEWLINE = '\n';

// A problem the linter could not attribute to a rule - a parse error, or a rule crashing - is
// never baselined. It means the file was not really checked, so it has to fail.
const UNRULED = '<parse-error>';

function relative (absolutePath) {
  return path.relative(path.resolve(__dirname, '..'), absolutePath).split(path.sep).join('/');
}

function readBaseline (file) {
  if (!fs.existsSync(file)) {
    return {};
  }
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    throw new Error(`${relative(file)} is not valid JSON (${e.message})`);
  }
}

function writeBaseline (file, counts) {
  const sorted = {};
  Object.keys(counts).sort().forEach(f => {
    const rules = counts[f];
    sorted[f] = {};
    Object.keys(rules).sort().forEach(rule => {
      sorted[f][rule] = rules[rule];
    });
  });
  fs.writeFileSync(file, JSON.stringify(sorted, null, 2) + NEWLINE);
}

// problems: [{file, rule, line, column, text}]
function countByFileAndRule (problems) {
  const counts = {};
  problems.forEach(p => {
    counts[p.file] = counts[p.file] || {};
    counts[p.file][p.rule] = (counts[p.file][p.rule] || 0) + 1;
  });
  return counts;
}

// Returns the problems that are not covered by the baseline, plus the entries the baseline holds
// that reality no longer backs up.
function compare (problems, baseline) {
  const counts = countByFileAndRule(problems);
  const newProblems = [];
  const stale = [];

  Object.keys(counts).forEach(file => {
    const allowed = baseline[file] || {};
    Object.keys(counts[file]).forEach(rule => {
      const actual = counts[file][rule];
      const permitted = rule === UNRULED ? 0 : (allowed[rule] || 0);
      if (actual > permitted) {
        // Report every problem of that rule in that file: which of them is "the new one" cannot
        // be known, and showing all of them is what lets someone find it.
        problems
          .filter(p => p.file === file && p.rule === rule)
          .forEach(p => newProblems.push(p));
      }
    });
  });

  Object.keys(baseline).forEach(file => {
    Object.keys(baseline[file]).forEach(rule => {
      const actual = (counts[file] || {})[rule] || 0;
      if (actual < baseline[file][rule]) {
        stale.push({file, rule, was: baseline[file][rule], now: actual});
      }
    });
  });

  return {counts, newProblems, stale};
}

function total (counts) {
  return Object.keys(counts).reduce(
    (sum, file) => sum + Object.keys(counts[file]).reduce((s, rule) => s + counts[file][rule], 0),
    0
  );
}

// Prints an eslint-shaped report and returns the process exit code. `all` only changes the wording:
// the caller passes an empty baseline for it, so every problem comes back as uncovered.
function report (options) {
  const {tool, baselineFile, problems, baseline, updated, all} = options;
  const {counts, newProblems, stale} = compare(problems, baseline);
  const known = total(counts);

  if (updated) {
    console.log(
      `${tool}: recorded ${known} pre-existing problem(s) in ` +
      `${Object.keys(counts).length} file(s) into ${relative(baselineFile)}`
    );
    return 0;
  }

  if (newProblems.length) {
    const byFile = {};
    newProblems.forEach(p => {
      byFile[p.file] = byFile[p.file] || [];
      byFile[p.file].push(p);
    });
    Object.keys(byFile).sort().forEach(file => {
      console.log('');
      console.log(file);
      byFile[file]
        .sort((a, b) => (a.line - b.line) || (a.column - b.column))
        .forEach(p => {
          console.log(`  ${p.line}:${p.column}  error  ${p.text}  ${p.rule}`);
        });
    });
    console.log('');
    if (all) {
      console.log(
        `${tool}: ${newProblems.length} problem(s) in ${Object.keys(byFile).length} file(s), ` +
        `baselined debt included.`
      );
      return 1;
    }
    console.log(
      `${tool}: ${newProblems.length} problem(s) not covered by ${relative(baselineFile)}.`
    );
    console.log(
      `Fix them - the baseline records the debt that was already there, and it may only shrink. ` +
      `See "Lint" in AGENTS.md.`
    );
    return 1;
  }

  console.log(all
    ? `${tool}: no problems.`
    : `${tool}: no new problems (${known} pre-existing, recorded in the baseline).`);

  // Someone fixed something, or a baselined file moved. Say so, but do not fail: cleaning lint up
  // must never be the thing that breaks a build.
  if (stale.length) {
    console.log('');
    console.log(`${relative(baselineFile)} is out of date - ${stale.length} entr(ies) improved:`);
    stale.slice(0, 20).forEach(s => {
      console.log(`  ${s.file}  ${s.rule}  ${s.was} -> ${s.now}`);
    });
    if (stale.length > 20) {
      console.log(`  ... and ${stale.length - 20} more`);
    }
    const script = tool === 'stylelint' ? 'stylelint' : 'lint';
    console.log(`Run \`npm run ${script}:baseline\` to record it.`);
  }

  return 0;
}

module.exports = {
  UNRULED,
  compare,
  countByFileAndRule,
  readBaseline,
  relative,
  report,
  total,
  writeBaseline
};
