/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

// What this file protects: the baseline decides whether `npm run lint` passes, so a wrong
// comparison either hides a real problem or blocks a clean branch.

const fs = require('fs');
const os = require('os');
const path = require('path');
const baselines = require('./lint-baseline');

const problem = (file, rule, line = 1) => ({
  file,
  rule,
  line,
  column: 1,
  text: `${rule} problem`
});

describe('compare', () => {
  it('passes a file whose problems match the baseline exactly', () => {
    const problems = [problem('a.js', 'max-len'), problem('a.js', 'max-len', 2)];
    const {newProblems} = baselines.compare(problems, {'a.js': {'max-len': 2}});
    expect(newProblems).toHaveLength(0);
  });

  it('fails a problem in a file the baseline does not mention', () => {
    const {newProblems} = baselines.compare([problem('new.js', 'semi')], {});
    expect(newProblems).toHaveLength(1);
    expect(newProblems[0].file).toBe('new.js');
  });

  it('fails a rule that exceeds its baselined count in an already-baselined file', () => {
    const problems = [
      problem('a.js', 'max-len', 1),
      problem('a.js', 'max-len', 2),
      problem('a.js', 'max-len', 3)
    ];
    const {newProblems} = baselines.compare(problems, {'a.js': {'max-len': 2}});
    // Which of the three is the new one cannot be known, so all of them are shown.
    expect(newProblems).toHaveLength(3);
  });

  it('fails a rule absent from a file\'s baseline even when other rules are baselined', () => {
    const problems = [problem('a.js', 'max-len'), problem('a.js', 'semi')];
    const {newProblems} = baselines.compare(problems, {'a.js': {'max-len': 1}});
    expect(newProblems.map(p => p.rule)).toEqual(['semi']);
  });

  it('never lets a parse error be covered by the baseline', () => {
    const problems = [problem('a.js', baselines.UNRULED)];
    // Even an explicit entry for it does not help: an unparsed file was not checked.
    const {newProblems} = baselines.compare(problems, {'a.js': {[baselines.UNRULED]: 5}});
    expect(newProblems).toHaveLength(1);
  });

  it('reports a file that improved as stale rather than as a failure', () => {
    const {newProblems, stale} = baselines.compare(
      [problem('a.js', 'max-len')],
      {'a.js': {'max-len': 3}}
    );
    expect(newProblems).toHaveLength(0);
    expect(stale).toEqual([{file: 'a.js', rule: 'max-len', was: 3, now: 1}]);
  });

  it('reports a baselined file that no longer has the problem at all', () => {
    const {newProblems, stale} = baselines.compare([], {'gone.js': {semi: 2}});
    expect(newProblems).toHaveLength(0);
    expect(stale).toEqual([{file: 'gone.js', rule: 'semi', was: 2, now: 0}]);
  });
});

describe('countByFileAndRule', () => {
  it('counts per file and per rule', () => {
    const counts = baselines.countByFileAndRule([
      problem('a.js', 'semi'),
      problem('a.js', 'semi', 2),
      problem('a.js', 'max-len'),
      problem('b.js', 'semi')
    ]);
    expect(counts).toEqual({'a.js': {semi: 2, 'max-len': 1}, 'b.js': {semi: 1}});
  });

  it('totals what it counted', () => {
    expect(baselines.total({'a.js': {semi: 2, 'max-len': 1}, 'b.js': {semi: 1}})).toBe(4);
  });
});

describe('readBaseline / writeBaseline', () => {
  let dir;

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lint-baseline-'));
  });

  it('treats a missing baseline as empty, so a first run still reports', () => {
    expect(baselines.readBaseline(path.join(dir, 'absent.json'))).toEqual({});
  });

  it('sorts files and rules, so an unrelated edit does not reshuffle the diff', () => {
    const file = path.join(dir, 'baseline.json');
    baselines.writeBaseline(file, {'b.js': {semi: 1}, 'a.js': {semi: 1, 'max-len': 2}});
    expect(fs.readFileSync(file, 'utf8')).toBe([
      '{',
      '  "a.js": {',
      '    "max-len": 2,',
      '    "semi": 1',
      '  },',
      '  "b.js": {',
      '    "semi": 1',
      '  }',
      '}',
      ''
    ].join('\n'));
  });

  it('names the file when the baseline is not valid JSON', () => {
    const file = path.join(dir, 'broken.json');
    fs.writeFileSync(file, '{not json');
    expect(() => baselines.readBaseline(file)).toThrow(/broken\.json is not valid JSON/);
  });

  it('round-trips what it wrote', () => {
    const file = path.join(dir, 'baseline.json');
    const counts = {'a.js': {'max-len': 2}};
    baselines.writeBaseline(file, counts);
    expect(baselines.readBaseline(file)).toEqual(counts);
  });
});

describe('report', () => {
  let logged;

  beforeEach(() => {
    logged = [];
    jest.spyOn(console, 'log').mockImplementation(line => logged.push(String(line)));
  });

  const run = (problems, baseline, extra = {}) => baselines.report(Object.assign({
    tool: 'eslint',
    baselineFile: path.resolve(__dirname, '../.eslintbaseline.json'),
    problems,
    baseline,
    updated: false
  }, extra));

  it('exits non-zero and prints the problem when something is not baselined', () => {
    expect(run([problem('new.js', 'semi')], {})).toBe(1);
    expect(logged.join('\n')).toContain('new.js');
    expect(logged.join('\n')).toContain('not covered by');
  });

  it('exits zero when everything is baselined', () => {
    expect(run([problem('a.js', 'semi')], {'a.js': {semi: 1}})).toBe(0);
    expect(logged.join('\n')).toContain('no new problems');
  });

  it('exits zero on a stale baseline, so paying down debt cannot break a build', () => {
    expect(run([], {'a.js': {semi: 1}})).toBe(0);
    expect(logged.join('\n')).toContain('out of date');
  });

  // `--all` passes an empty baseline, so the wording must not tell the reader to go and fix debt
  // the baseline deliberately records.
  it('does not ask for a fix when reporting everything', () => {
    expect(run([problem('a.js', 'semi')], {}, {all: true})).toBe(1);
    expect(logged.join('\n')).toContain('baselined debt included');
    expect(logged.join('\n')).not.toContain('may only shrink');
  });

  it('says there is nothing at all when reporting everything and there is nothing', () => {
    expect(run([], {}, {all: true})).toBe(0);
    expect(logged.join('\n')).toContain('no problems.');
    expect(logged.join('\n')).not.toContain('pre-existing');
  });
});
