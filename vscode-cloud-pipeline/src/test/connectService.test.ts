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
import { beforeEach, describe, it } from 'node:test';
import { openRemoteWindow } from '../connectService';
import type { KeyProvisionResult } from '../keyProvisioning';
import type { RemoteOwnerEnv } from '../remoteWorkspacePath';
import { executedCommands } from './support/vscode';

function keyResult(sshConfigUser: string, ownerEnv?: RemoteOwnerEnv): KeyProvisionResult {
  return {
    privateKeyPath: '/keys/vscode-cp-42',
    publicKeyPath: '/keys/vscode-cp-42.pub',
    sshConfigUser,
    hostRsaPubKeyBody: 'ssh-rsa AAAAHOSTKEY',
    ownerEnv,
  };
}

function openFolderCall(path: string): unknown[] {
  return ['vscode.openFolder', { scheme: 'vscode-remote', authority: 'ssh-remote+cp-run-42', path }, true];
}

describe('openRemoteWindow', () => {
  const ownerEnv = { owner: 'U1037187', ownerHome: '/data/users/U1037187' };

  beforeEach(() => {
    executedCommands.length = 0;
  });

  it('opens OWNER_HOME of the run owner in a new Remote-SSH window', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187', ownerEnv));
    assert.deepEqual(executedCommands, [openFolderCall('/data/users/U1037187')]);
  });

  it('opens /home/<user>, if OWNER_HOME could not be read', async () => {
    await openRemoteWindow('cp-run-42', keyResult('U1037187'));
    assert.deepEqual(executedCommands, [openFolderCall('/home/U1037187')]);
  });

  it('opens /root for root', async () => {
    await openRemoteWindow('cp-run-42', keyResult('root', ownerEnv));
    assert.deepEqual(executedCommands, [openFolderCall('/root')]);
  });
});
