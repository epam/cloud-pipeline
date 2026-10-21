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
import {render, screen, within, click, hover, fn} from '@test';
import AsyncTasksList from './async-tasks-list';

const SUCCESS_TASK = {
  id: 1,
  status: 'SUCCESS',
  params: {archiveName: 'archive-success', storageId: 7, paths: ['a/', 'b.txt']},
  created: '2026-10-21T13:13:02.274Z',
  finished: '2026-10-21T13:15:19.689Z'
};

const RUNNING_TASK = {
  id: 2,
  status: 'RUNNING',
  params: {archiveName: 'archive-running', storageId: 7, paths: ['c.txt']},
  created: '2026-10-21 13:15:19.689'
};

const FAILED_TASK = {
  id: 3,
  status: 'FAILED',
  errorMessage: 'Something went wrong',
  params: {archiveName: 'archive-failed', storageId: 7, paths: ['d.txt']},
  created: '2026-10-21T13:13:02.274Z',
  finished: '2026-10-21T13:16:00.000Z'
};

function rowFor (archiveName) {
  return screen.getByText(archiveName).closest('tr');
}

test('renders a row per task, found by the archive name', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(screen.getByText('archive-success')).toBeInTheDocument();
  expect(screen.getByText('archive-running')).toBeInTheDocument();
});

test('shows the Download button only for a SUCCESS task', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-success')).getByRole('button', {name: /Download/i}))
    .toBeInTheDocument();
  expect(within(rowFor('archive-running')).queryByRole('button', {name: /Download/i}))
    .not.toBeInTheDocument();
});

test('shows the Cancel button only for an active task', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  expect(within(rowFor('archive-running')).getByRole('button', {name: /Cancel/i}))
    .toBeInTheDocument();
  expect(within(rowFor('archive-success')).queryByRole('button', {name: /Cancel/i}))
    .not.toBeInTheDocument();
});

test('calls onDownload with the task after clicking Download', async () => {
  const onDownload = fn();
  render(<AsyncTasksList tasks={[SUCCESS_TASK]} onDownload={onDownload} />);

  await click(within(rowFor('archive-success')).getByRole('button', {name: /Download/i}));

  expect(onDownload).toHaveBeenCalledWith(SUCCESS_TASK);
});

test('calls onCancel with the task after clicking Cancel', async () => {
  const onCancel = fn();
  render(<AsyncTasksList tasks={[RUNNING_TASK]} onCancel={onCancel} />);

  await click(within(rowFor('archive-running')).getByRole('button', {name: /Cancel/i}));

  expect(onCancel).toHaveBeenCalledWith(RUNNING_TASK);
});

test('exposes a FAILED task\'s errorMessage through its tooltip', async () => {
  render(<AsyncTasksList tasks={[FAILED_TASK]} />);

  // Tooltip is rc-trigger based: its content only mounts once hovered (see
  // client/test/AGENTS.md, "mouseenter/mouseleave do not reach a component").
  await hover(screen.getByText('FAILED'));

  expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
});

test('a lowercase status still gets the FAILED CSS class and tooltip', async () => {
  const lowercaseFailedTask = {
    ...FAILED_TASK,
    id: 4,
    status: 'failed',
    params: {...FAILED_TASK.params, archiveName: 'archive-lowercase-failed'}
  };
  render(<AsyncTasksList tasks={[lowercaseFailedTask]} />);

  const statusText = screen.getByText('failed');
  expect(statusText).toHaveClass('cp-danger');

  await hover(statusText);

  expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
});

test('renders both API timestamp shapes through displayDate', () => {
  render(<AsyncTasksList tasks={[SUCCESS_TASK, RUNNING_TASK]} />);

  // SUCCESS_TASK.created is ISO ('2026-10-21T13:13:02.274Z'), RUNNING_TASK.created
  // is space-separated ('2026-10-21 13:15:19.689') — both formats the API sends.
  // displayDate formats both the same way, and never the raw string.
  expect(screen.queryByText('2026-10-21T13:13:02.274Z')).not.toBeInTheDocument();
  expect(screen.queryByText('2026-10-21 13:15:19.689')).not.toBeInTheDocument();
  expect(screen.getAllByText('2026-10-21 13:13:02').length).toBeGreaterThan(0);
  expect(screen.getAllByText('2026-10-21 13:15:19').length).toBeGreaterThan(0);
});
