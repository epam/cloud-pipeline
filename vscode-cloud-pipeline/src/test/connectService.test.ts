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

import './support/registerVscodeStub';
import { strict as assert } from 'node:assert';
import { afterEach, beforeEach, describe, it, mock } from 'node:test';
import { openRemoteWindow } from '../connectService';
import type { KeyProvisionResult } from '../keyProvisioning';
import type { RemoteRunEnv } from '../remoteWorkspacePath';
import { executedCommands, shownWarnings } from './support/vscode';

function keyResult(sshConfigUser: string, runEnv?: RemoteRunEnv): KeyProvisionResult {
  return {
    privateKeyPath: '/keys/vscode-cp-42',
    publicKeyPath: '/keys/vscode-cp-42.pub',
    sshConfigUser,
    hostRsaPubKeyBody: 'ssh-rsa AAAAHOSTKEY',
    runEnv,
  };
}

function openFolderCall(path: string): unknown[] {
  return ['vscode.openFolder', { scheme: 'vscode-remote', authority: 'ssh-remote+cp-run-42', path }, true];
}

describe('openRemoteWindow', () => {
  const runEnv: RemoteRunEnv = {
    owner: 'U1037187',
    ownerHome: '/data/users/U1037187',
    workspaceDir: '',
    workspaceDirUsable: false,
  };

  let warn: ReturnType<typeof mock.method>;

  beforeEach(() => {
    executedCommands.length = 0;
    shownWarnings.length = 0;
    warn = mock.method(console, 'warn', () => undefined);
  });

  afterEach(() => {
    mock.restoreAll();
  });

  it('opens OWNER_HOME of the run owner in a new Remote-SSH window', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187', runEnv), 42);
    assert.deepEqual(executedCommands, [openFolderCall('/data/users/U1037187')]);
    assert.deepEqual(shownWarnings, []);
  });

  it('opens /home/<user>, if OWNER_HOME could not be read', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187'), 42);
    assert.deepEqual(executedCommands, [openFolderCall('/home/U1037187')]);
  });

  it('opens /root for root', async () => {
    await openRemoteWindow('cp-run-42', keyResult('root', runEnv), 42);
    assert.deepEqual(executedCommands, [openFolderCall('/root')]);
  });

  it('opens CP_VSCODE_WORKSPACE_DIR, if the run asks for a folder the SSH user can open', async () => {
    await openRemoteWindow(
      'cp-run-42',
      keyResult('root', { ...runEnv, workspaceDir: '/data/projects/p1', workspaceDirUsable: true }),
      42
    );
    assert.deepEqual(executedCommands, [openFolderCall('/data/projects/p1')]);
    assert.deepEqual(shownWarnings, []);
    assert.equal(warn.mock.callCount(), 0);
  });

  it('opens the home folder, and says why, if the user cannot open CP_VSCODE_WORKSPACE_DIR', async () => {
    await openRemoteWindow(
      'cp-run-42',
      keyResult('U1037187', { ...runEnv, workspaceDir: '/data/private', workspaceDirUsable: false }),
      42
    );
    const message =
      'CP_VSCODE_WORKSPACE_DIR of run 42 (/data/private) is not opened for U1037187: ' +
      'it is not a folder this user can open. The home folder opens.';
    assert.deepEqual(executedCommands, [openFolderCall('/data/users/U1037187')]);
    assert.deepEqual(shownWarnings, [message]);
    assert.equal(warn.mock.callCount(), 1);
    assert.ok(String(warn.mock.calls[0].arguments[0]).endsWith(message));
  });

  it('opens the home folder, and says why, if CP_VSCODE_WORKSPACE_DIR could not be checked on the run', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187'), 42, '/data/projects/p1');
    assert.deepEqual(executedCommands, [openFolderCall('/home/U1037187')]);
    assert.deepEqual(shownWarnings, [
      'CP_VSCODE_WORKSPACE_DIR of run 42 (/data/projects/p1) is not opened for U1037187: ' +
        'it could not be checked on the run. The home folder opens.',
    ]);
  });

  it('shows no warning, if the run does not ask for a folder', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187'), 42);
    await openRemoteWindow('cp-run-42', keyResult('U1037187', runEnv), 42, '');
    assert.deepEqual(shownWarnings, []);
    assert.equal(warn.mock.callCount(), 0);
  });
});
