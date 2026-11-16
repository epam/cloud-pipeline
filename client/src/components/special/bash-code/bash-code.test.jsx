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

import React from 'react';
import {render, screen, spyOn} from '@test';
import BashCode from './index';

// highlight.js warns about a deprecated call once per page, so this test runs first
it('highlights with no deprecated highlight.js call', () => {
  const log = spyOn(console, 'log');
  const warn = spyOn(console, 'warn');

  render(<BashCode code="echo hi" />);

  [log, warn].forEach(spy => {
    expect(spy).not.toHaveBeenCalledWith(expect.stringMatching(/deprecated/i));
  });
});

describe('BashCode', () => {
  it('highlights a bash command', () => {
    render(<BashCode code={'for f in *.txt; do echo "$f"; done'} />);

    expect(screen.getByText('for')).toHaveClass('hljs-keyword');
    expect(screen.getByText('echo')).toHaveClass('hljs-built_in');
  });

  it('shows a [URL] mark as a link', () => {
    render(<BashCode code="curl -O [URL]https://example.com/file.txt[/URL]" />);

    expect(screen.getByRole('link', {name: 'https://example.com/file.txt'}))
      .toHaveAttribute('href', 'https://example.com/file.txt');
  });
});
