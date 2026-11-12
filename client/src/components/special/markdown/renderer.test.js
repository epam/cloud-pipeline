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

import {spyOn} from '@test';
import getMarkdownRenderer from './renderer';

const render = (markdown) => {
  const container = document.createElement('div');
  container.innerHTML = getMarkdownRenderer().render(markdown);
  return container;
};

// highlight.js warns about a deprecated call once per page, so this test runs first
it('highlights the code with no deprecated highlight.js call', () => {
  const log = spyOn(console, 'log');
  const warn = spyOn(console, 'warn');

  render('```python\nprint(1)\n```');

  [log, warn].forEach(spy => {
    expect(spy).not.toHaveBeenCalledWith(expect.stringMatching(/deprecated/i));
  });
});

describe('getMarkdownRenderer', () => {
  it('highlights a code block in the language it names', () => {
    const container = render('```python\ndef f(x):\n    return x\n```');

    expect(container.querySelector('.hljs-keyword').textContent).toBe('def');
    expect(container.querySelector('.hljs-title').textContent).toBe('f');
  });

  it('highlights a code block with no language as bash', () => {
    const container = render('```\necho done\n```');

    expect(container.querySelector('.hljs-built_in').textContent).toBe('echo');
  });
});
