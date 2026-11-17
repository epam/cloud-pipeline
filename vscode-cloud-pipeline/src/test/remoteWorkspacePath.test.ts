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

import { strict as assert } from 'node:assert';
import { execFileSync } from 'node:child_process';
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import {
  parseRunEnvProbe,
  RemoteRunEnv,
  remoteWorkspacePath,
  RUN_ENV_PROBE_COMMAND,
  workspaceDirNotOpenedReason,
} from '../remoteWorkspacePath';

const runEnv: RemoteRunEnv = {
  owner: 'U1037187',
  ownerHome: '/data/users/U1037187',
  workspaceDir: '',
  workspaceDirUsable: false,
};
const withWorkspace: RemoteRunEnv = { ...runEnv, workspaceDir: '/data/projects/p1', workspaceDirUsable: true };

describe('remoteWorkspacePath', () => {
  it('opens OWNER_HOME for the run owner', () => {
    assert.equal(remoteWorkspacePath('U1037187', runEnv), '/data/users/U1037187');
  });

  it('opens OWNER_HOME without a trailing slash', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...runEnv, ownerHome: '/data/users/U1037187/' }), '/data/users/U1037187');
  });

  it('opens /home/<user> for the run owner, if OWNER_HOME is not set', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...runEnv, ownerHome: '' }), '/home/U1037187');
    assert.equal(remoteWorkspacePath('U1037187'), '/home/U1037187');
  });

  it('opens /home/<user> for the run owner, if OWNER_HOME is not an absolute path', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...runEnv, ownerHome: 'users/U1037187' }), '/home/U1037187');
  });

  it('opens /home/<user> for a user who is not the run owner', () => {
    assert.equal(remoteWorkspacePath('U2000001', runEnv), '/home/U2000001');
  });

  it('opens /root for root, whatever OWNER_HOME is', () => {
    assert.equal(remoteWorkspacePath('root', runEnv), '/root');
    assert.equal(remoteWorkspacePath('root', { ...runEnv, owner: 'root', ownerHome: '/home/root' }), '/root');
    assert.equal(remoteWorkspacePath(''), '/root');
  });

  it('crops the user name by @', () => {
    assert.equal(remoteWorkspacePath('U1037187@example.com', runEnv), '/data/users/U1037187');
  });

  it('opens CP_VSCODE_WORKSPACE_DIR for the run owner, root and any other user', () => {
    assert.equal(remoteWorkspacePath('U1037187', withWorkspace), '/data/projects/p1');
    assert.equal(remoteWorkspacePath('root', withWorkspace), '/data/projects/p1');
    assert.equal(remoteWorkspacePath('U2000001', withWorkspace), '/data/projects/p1');
  });

  it('opens CP_VSCODE_WORKSPACE_DIR without a trailing slash', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...withWorkspace, workspaceDir: '/data/projects/p1/' }), '/data/projects/p1');
  });

  it('opens the default folder, if the SSH user cannot open CP_VSCODE_WORKSPACE_DIR', () => {
    const notUsable = { ...withWorkspace, workspaceDirUsable: false };
    assert.equal(remoteWorkspacePath('U1037187', notUsable), '/data/users/U1037187');
    assert.equal(remoteWorkspacePath('root', notUsable), '/root');
    assert.equal(remoteWorkspacePath('U2000001', notUsable), '/home/U2000001');
  });

  it('opens the default folder, if CP_VSCODE_WORKSPACE_DIR is not an absolute path', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...withWorkspace, workspaceDir: 'projects/p1' }), '/data/users/U1037187');
  });
});

describe('workspaceDirNotOpenedReason', () => {
  it('has no reason, if CP_VSCODE_WORKSPACE_DIR is not set, or is opened', () => {
    assert.equal(workspaceDirNotOpenedReason(undefined), undefined);
    assert.equal(workspaceDirNotOpenedReason(runEnv), undefined);
    assert.equal(workspaceDirNotOpenedReason(withWorkspace), undefined);
  });

  it('says why CP_VSCODE_WORKSPACE_DIR is not opened', () => {
    assert.equal(workspaceDirNotOpenedReason({ ...withWorkspace, workspaceDir: 'projects/p1' }), 'it is not an absolute path');
    assert.equal(
      workspaceDirNotOpenedReason({ ...withWorkspace, workspaceDirUsable: false }),
      'it is not a folder this user can open'
    );
  });

  it('says that CP_VSCODE_WORKSPACE_DIR could not be checked, if the run asks for it, but its environment is unknown', () => {
    assert.equal(workspaceDirNotOpenedReason(undefined, '/data/projects/p1'), 'it could not be checked on the run');
    assert.equal(workspaceDirNotOpenedReason(undefined, ''), undefined);
  });

  it('trusts the environment of the run over the run parameter', () => {
    assert.equal(workspaceDirNotOpenedReason(withWorkspace, '/elsewhere'), undefined);
    assert.equal(workspaceDirNotOpenedReason(runEnv, '/data/projects/p1'), undefined);
  });
});

describe('parseRunEnvProbe', () => {
  it('reads OWNER, OWNER_HOME, CP_VSCODE_WORKSPACE_DIR and whether it can be opened', () => {
    assert.deepEqual(
      parseRunEnvProbe(
        'CP_OWNER=U1037187\nCP_OWNER_HOME=/data/users/U1037187\n' +
          'CP_PROBE_WORKSPACE_DIR=/data/projects/p1\nCP_PROBE_WORKSPACE_DIR_USABLE=1\n'
      ),
      withWorkspace
    );
  });

  it('ignores any other output of the remote shell, the environment it prints included', () => {
    assert.deepEqual(
      parseRunEnvProbe(
        'Welcome!\r\nCP_VSCODE_WORKSPACE_DIR=/elsewhere\r\nCP_OWNER=U1037187\r\nCP_OWNER_HOME=/data/users/U1037187\r\n' +
          'CP_PROBE_WORKSPACE_DIR=\r\nCP_PROBE_WORKSPACE_DIR_USABLE=0\r\n'
      ),
      runEnv
    );
  });

  it('has empty values, if the output does not hold them', () => {
    assert.deepEqual(parseRunEnvProbe(''), { owner: '', ownerHome: '', workspaceDir: '', workspaceDirUsable: false });
  });

  it('keeps CP_VSCODE_WORKSPACE_DIR as the probe checked it, spaces included', () => {
    const parsed = parseRunEnvProbe('CP_PROBE_WORKSPACE_DIR=/srv/ok \nCP_PROBE_WORKSPACE_DIR_USABLE=0\n');
    assert.equal(parsed.workspaceDir, '/srv/ok ');
    assert.equal(workspaceDirNotOpenedReason(parsed), 'it is not a folder this user can open');
  });
});

describe('RUN_ENV_PROBE_COMMAND', () => {
  const runProbe = (env: Record<string, string>): string =>
    execFileSync('sh', ['-c', RUN_ENV_PROBE_COMMAND], { env: { PATH: process.env.PATH ?? '', ...env } }).toString();
  const owner = { OWNER: 'U1037187', OWNER_HOME: '/data/users/U1037187' };

  it('prints OWNER and OWNER_HOME of the shell', () => {
    assert.deepEqual(parseRunEnvProbe(runProbe(owner)), runEnv);
  });

  it('prints empty values, if they are not set', () => {
    const missingEnvFile = join(tmpdir(), 'cp_env_missing.sh');
    assert.deepEqual(parseRunEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: missingEnvFile })), {
      owner: '',
      ownerHome: '',
      workspaceDir: '',
      workspaceDirUsable: false,
    });
  });

  describe('with CP_VSCODE_WORKSPACE_DIR', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cp-workspace-'));
    const folder = join(dir, 'project');
    const file = join(dir, 'file.txt');
    // A folder the user can only list, but not enter, and one the user can enter, but not list.
    const noAccess = { 'no': join(dir, 'no-access'), 'read-only': join(dir, 'read-only'), 'enter-only': join(dir, 'enter-only') };
    mkdirSync(folder);
    writeFileSync(file, 'not a folder\n');
    mkdirSync(noAccess['no']);
    chmodSync(noAccess['no'], 0o000);
    mkdirSync(noAccess['read-only']);
    chmodSync(noAccess['read-only'], 0o400);
    mkdirSync(noAccess['enter-only']);
    chmodSync(noAccess['enter-only'], 0o100);
    const isRoot = process.getuid?.() === 0;

    after(() => {
      Object.values(noAccess).forEach((d) => chmodSync(d, 0o700));
      rmSync(dir, { recursive: true, force: true });
    });

    const probe = (workspaceDir: string): RemoteRunEnv =>
      parseRunEnvProbe(runProbe({ ...owner, CP_VSCODE_WORKSPACE_DIR: workspaceDir }));

    it('prints it, and that it can be opened, for a folder', () => {
      assert.deepEqual(probe(folder), { ...runEnv, workspaceDir: folder, workspaceDirUsable: true });
    });

    it('prints that it cannot be opened, if there is no such folder', () => {
      const missing = join(dir, 'missing');
      assert.deepEqual(probe(missing), { ...runEnv, workspaceDir: missing, workspaceDirUsable: false });
    });

    it('prints that it cannot be opened, if it is a file', () => {
      assert.deepEqual(probe(file), { ...runEnv, workspaceDir: file, workspaceDirUsable: false });
    });

    for (const [access, path] of Object.entries(noAccess)) {
      it(`prints that it cannot be opened, if the user has ${access} access`, { skip: isRoot && 'root can open any folder' }, () => {
        assert.deepEqual(probe(path), { ...runEnv, workspaceDir: path, workspaceDirUsable: false });
      });
    }
  });

  describe('with the env file of the run', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cp-env-'));
    const envFile = join(dir, 'cp_env.sh');
    writeFileSync(
      envFile,
      `echo noise\nexport OWNER="U1037187"\nexport OWNER_HOME="/data/users/U1037187"\nexport CP_VSCODE_WORKSPACE_DIR="${dir}"\n`
    );
    const fromFile: RemoteRunEnv = { ...runEnv, workspaceDir: dir, workspaceDirUsable: true };

    after(() => rmSync(dir, { recursive: true, force: true }));

    it('reads OWNER, OWNER_HOME and CP_VSCODE_WORKSPACE_DIR from the file, if the shell has not', () => {
      assert.deepEqual(parseRunEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: envFile })), fromFile);
    });

    it('is not mistaken for the output of a shell startup file', () => {
      const startupFile = join(dir, 'startup.sh');
      writeFileSync(startupFile, "printf 'Welcome, no new line'\n");
      // No stdin: on a socket, as Node gives it by default, bash takes itself for an SSH command and skips BASH_ENV.
      const stdout = execFileSync('bash', ['-c', RUN_ENV_PROBE_COMMAND], {
        env: { PATH: process.env.PATH ?? '', BASH_ENV: startupFile, CP_ENV_FILE_TO_SOURCE: envFile },
        stdio: ['ignore', 'pipe', 'pipe'],
      }).toString();
      assert.match(stdout, /^Welcome, no new line/);
      assert.deepEqual(parseRunEnvProbe(stdout), fromFile);
    });

    it('keeps OWNER and OWNER_HOME the shell already has', () => {
      assert.deepEqual(
        parseRunEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: envFile, OWNER: 'U2000001', OWNER_HOME: '/home/U2000001' })),
        { ...runEnv, owner: 'U2000001', ownerHome: '/home/U2000001' }
      );
    });
  });
});
