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
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import { OWNER_ENV_PROBE_COMMAND, parseOwnerEnvProbe, remoteWorkspacePath } from '../remoteWorkspacePath';

describe('remoteWorkspacePath', () => {
  const ownerEnv = { owner: 'U1037187', ownerHome: '/data/users/U1037187' };

  it('opens OWNER_HOME for the run owner', () => {
    assert.equal(remoteWorkspacePath('U1037187', ownerEnv), '/data/users/U1037187');
  });

  it('opens OWNER_HOME without a trailing slash', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...ownerEnv, ownerHome: '/data/users/U1037187/' }), '/data/users/U1037187');
  });

  it('opens /home/<user> for the run owner, if OWNER_HOME is not set', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...ownerEnv, ownerHome: '' }), '/home/U1037187');
    assert.equal(remoteWorkspacePath('U1037187'), '/home/U1037187');
  });

  it('opens /home/<user> for the run owner, if OWNER_HOME is not an absolute path', () => {
    assert.equal(remoteWorkspacePath('U1037187', { ...ownerEnv, ownerHome: 'users/U1037187' }), '/home/U1037187');
  });

  it('opens /home/<user> for a user who is not the run owner', () => {
    assert.equal(remoteWorkspacePath('U2000001', ownerEnv), '/home/U2000001');
  });

  it('opens /root for root, whatever OWNER_HOME is', () => {
    assert.equal(remoteWorkspacePath('root', ownerEnv), '/root');
    assert.equal(remoteWorkspacePath('root', { owner: 'root', ownerHome: '/home/root' }), '/root');
    assert.equal(remoteWorkspacePath(''), '/root');
  });

  it('crops the user name by @', () => {
    assert.equal(remoteWorkspacePath('U1037187@example.com', ownerEnv), '/data/users/U1037187');
  });
});

describe('parseOwnerEnvProbe', () => {
  it('reads OWNER and OWNER_HOME', () => {
    assert.deepEqual(
      parseOwnerEnvProbe('CP_OWNER=U1037187\nCP_OWNER_HOME=/data/users/U1037187\n'),
      { owner: 'U1037187', ownerHome: '/data/users/U1037187' }
    );
  });

  it('ignores any other output of the remote shell', () => {
    assert.deepEqual(
      parseOwnerEnvProbe('Welcome!\r\nCP_OWNER=U1037187\r\nCP_OWNER_HOME=/data/users/U1037187\r\n'),
      { owner: 'U1037187', ownerHome: '/data/users/U1037187' }
    );
  });

  it('has empty values, if the output does not hold them', () => {
    assert.deepEqual(parseOwnerEnvProbe(''), { owner: '', ownerHome: '' });
  });
});

describe('OWNER_ENV_PROBE_COMMAND', () => {
  const runProbe = (env: Record<string, string>): string =>
    execFileSync('sh', ['-c', OWNER_ENV_PROBE_COMMAND], { env: { PATH: process.env.PATH ?? '', ...env } }).toString();

  it('prints OWNER and OWNER_HOME of the shell', () => {
    assert.deepEqual(
      parseOwnerEnvProbe(runProbe({ OWNER: 'U1037187', OWNER_HOME: '/data/users/U1037187' })),
      { owner: 'U1037187', ownerHome: '/data/users/U1037187' }
    );
  });

  it('prints empty values, if they are not set', () => {
    const missingEnvFile = join(tmpdir(), 'cp_env_missing.sh');
    assert.deepEqual(parseOwnerEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: missingEnvFile })), { owner: '', ownerHome: '' });
  });

  describe('with the env file of the run', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cp-env-'));
    const envFile = join(dir, 'cp_env.sh');
    writeFileSync(envFile, 'echo noise\nexport OWNER="U1037187"\nexport OWNER_HOME="/data/users/U1037187"\n');

    after(() => rmSync(dir, { recursive: true, force: true }));

    it('reads OWNER and OWNER_HOME from the file, if the shell has not', () => {
      assert.deepEqual(
        parseOwnerEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: envFile })),
        { owner: 'U1037187', ownerHome: '/data/users/U1037187' }
      );
    });

    it('is not mistaken for the output of a shell startup file', () => {
      const startupFile = join(dir, 'startup.sh');
      writeFileSync(startupFile, "printf 'Welcome, no new line'\n");
      // No stdin: on a socket, as Node gives it by default, bash takes itself for an SSH command and skips BASH_ENV.
      const stdout = execFileSync('bash', ['-c', OWNER_ENV_PROBE_COMMAND], {
        env: { PATH: process.env.PATH ?? '', BASH_ENV: startupFile, CP_ENV_FILE_TO_SOURCE: envFile },
        stdio: ['ignore', 'pipe', 'pipe'],
      }).toString();
      assert.match(stdout, /^Welcome, no new line/);
      assert.deepEqual(parseOwnerEnvProbe(stdout), { owner: 'U1037187', ownerHome: '/data/users/U1037187' });
    });

    it('keeps OWNER and OWNER_HOME the shell already has', () => {
      assert.deepEqual(
        parseOwnerEnvProbe(runProbe({ CP_ENV_FILE_TO_SOURCE: envFile, OWNER: 'U2000001', OWNER_HOME: '/home/U2000001' })),
        { owner: 'U2000001', ownerHome: '/home/U2000001' }
      );
    });
  });
});
