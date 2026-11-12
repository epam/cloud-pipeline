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
import {render, screen, change, keyDown, fn, stubApi, waitFor} from '@test';
import RunTaskLogs from './index';

const LOGS = [
  'plain line',
  '<b>not bold</b> & "quoted"',
  '\u001b[31mred line\u001b[0m',
  '\u001b]8;;https://example.com\u0007example link\u001b]8;;\u0007',
  'link \u001b]8;;http://example.com\u0007unterminated',
  'after the link'
].join('\n');

// A log that ends inside a color
const COLORED_END = 'plain first\n\u001b[31mred last';

const RED = {color: 'rgb(187, 0, 0)'};

// jsdom does not implement scrolling, and the component scrolls to the last line
const {scrollTo} = Element.prototype;

beforeEach(() => {
  Element.prototype.scrollTo = fn();
});

afterEach(() => {
  Element.prototype.scrollTo = scrollTo;
});

function renderLogs () {
  render(<RunTaskLogs logs={LOGS} showDate={false} />);
}

describe('RunTaskLogs', () => {
  it('shows the log text as text, not as HTML', () => {
    renderLogs();

    expect(screen.getByText('<b>not bold</b> & "quoted"')).toBeInTheDocument();
    expect(screen.queryByText('not bold')).toBeNull();
  });

  it('shows the colors of a line', () => {
    renderLogs();

    expect(screen.getByText('red line')).toHaveStyle(RED);
  });

  it('opens a hyperlink in a new tab', () => {
    renderLogs();

    const link = screen.getByRole('link', {name: 'example link'});
    expect(link).toHaveAttribute('href', 'https://example.com');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('shows the text of a hyperlink that is not http(s), with no link', () => {
    render(
      <RunTaskLogs
        logs={'\u001b]8;;file:///etc/hosts\u0007hosts\u001b]8;;\u0007 file link'}
        showDate={false}
      />
    );

    expect(screen.getByText('hosts file link')).toBeInTheDocument();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('shows the rest of an unterminated hyperlink as text, and the lines after it', () => {
    renderLogs();

    expect(screen.getByText('link ]8;;http://example.comunterminated')).toBeInTheDocument();
    expect(screen.getByText('after the link')).toBeInTheDocument();
  });

  it('does not carry the color at the end of a log over to another log view', () => {
    render(<RunTaskLogs logs={COLORED_END} showDate={false} />);
    render(<RunTaskLogs logs={COLORED_END} showDate={false} />);

    const firstLines = screen.getAllByText('plain first');
    expect(firstLines).toHaveLength(2);
    firstLines.forEach(line => expect(line).not.toHaveStyle(RED));
    screen.getAllByText('red last').forEach(line => expect(line).toHaveStyle(RED));
  });

  it('does not carry the color at the end of a run log over to the next load', async () => {
    stubApi({'/run/1/logs': [{date: '2026-11-12 10:00:00.000', logText: COLORED_END}]});

    render(<RunTaskLogs runId={1} autoUpdate={false} showDate={false} />);
    await screen.findByText('red last');
    render(<RunTaskLogs runId={1} autoUpdate={false} showDate={false} />);

    await waitFor(() => expect(screen.getAllByText('plain first')).toHaveLength(2));
    screen.getAllByText('plain first').forEach(line => expect(line).not.toHaveStyle(RED));
  });

  it('finds and marks a search string with quotes and angle brackets', async () => {
    renderLogs();

    await keyDown(window, {code: 'KeyF', ctrlKey: true});
    await change(screen.getByRole('textbox'), '</b> & "quoted"');

    expect(screen.getByText('1 of 1')).toBeInTheDocument();
    expect(screen.getByText('</b> & "quoted"'))
      .toHaveClass('cp-search-highlight-text', 'active');
  });
});
