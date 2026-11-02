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
import { EventEmitter } from 'node:events';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, it, mock } from 'node:test';
import { Server, utils } from 'ssh2';
import type { Client, Connection, SFTPWrapper } from 'ssh2';
import { provisionPasswordlessKey, readOwnerEnv } from '../keyProvisioning';
import { OWNER_ENV_PROBE_COMMAND } from '../remoteWorkspacePath';

class FakeStream extends EventEmitter {
  readonly stderr = new EventEmitter();
}

type ExecResult = { code: number; stdout?: string; stderr?: string } | Error;

/** An ssh2 client whose `exec` records the command and answers with `result`. */
function fakeClient(result: ExecResult, commands: string[]): Client {
  return {
    exec(cmd: string, callback: (err: Error | undefined, stream?: FakeStream) => void) {
      commands.push(cmd);
      if (result instanceof Error) {
        callback(result);
        return;
      }
      const stream = new FakeStream();
      callback(undefined, stream);
      setImmediate(() => {
        if (result.stdout) {
          stream.emit('data', Buffer.from(result.stdout));
        }
        if (result.stderr) {
          stream.stderr.emit('data', Buffer.from(result.stderr));
        }
        stream.emit('close', result.code);
      });
    },
  } as unknown as Client;
}

describe('readOwnerEnv', () => {
  let warn: ReturnType<typeof mock.method>;

  beforeEach(() => {
    warn = mock.method(console, 'warn', () => undefined);
  });

  afterEach(() => {
    mock.restoreAll();
  });

  it('reads OWNER and OWNER_HOME with the probe command', async () => {
    const commands: string[] = [];
    const client = fakeClient({ code: 0, stdout: 'CP_OWNER=U1037187\nCP_OWNER_HOME=/data/users/U1037187\n' }, commands);

    assert.deepEqual(await readOwnerEnv(client, 42), { owner: 'U1037187', ownerHome: '/data/users/U1037187' });
    assert.deepEqual(commands, [OWNER_ENV_PROBE_COMMAND]);
    assert.equal(warn.mock.callCount(), 0);
  });

  it('has no result, and warns, if the command fails', async () => {
    const client = fakeClient({ code: 1, stderr: 'sh: printf: not found' }, []);

    assert.equal(await readOwnerEnv(client, 42), undefined);
    assert.equal(warn.mock.callCount(), 1);
    assert.match(String(warn.mock.calls[0].arguments[0]), /run 42.*exit 1: sh: printf: not found/);
  });

  it('has no result, and warns, if the command cannot be run', async () => {
    const client = fakeClient(new Error('channel open failure'), []);

    assert.equal(await readOwnerEnv(client, 42), undefined);
    assert.equal(warn.mock.callCount(), 1);
    assert.match(String(warn.mock.calls[0].arguments[0]), /channel open failure/);
  });
});

const RUN_USER = 'U1037187';
const RUN_PASSWORD = 'U1037187';
const RUN_HOME = '/home/U1037187';
const { STATUS_CODE } = utils.sftp;

/** What the fake run answers to the probe of OWNER and OWNER_HOME. */
type ProbeReply = { code: number; stdout?: string };

interface FakeRun {
  port: number;
  commands: string[];
  close(): Promise<void>;
}

/** The files key provisioning reads from the run over SFTP. */
function runFile(filename: string): string | undefined {
  if (filename === '/etc/ssh/ssh_host_rsa_key.pub') {
    return 'ssh-rsa AAAAHOSTKEY root@run\n';
  }
  if (filename.startsWith(`${RUN_HOME}/.pipe/.keys/`)) {
    return filename.endsWith('.pub') ? 'ssh-rsa AAAAUSERKEY\n' : 'PRIVATE KEY\n';
  }
  return undefined;
}

function serveRunFiles(sftp: SFTPWrapper): void {
  const opened = new Map<string, Buffer>();
  let nextHandle = 0;
  sftp.on('OPEN', (reqId, filename) => {
    const text = runFile(filename);
    if (text === undefined) {
      sftp.status(reqId, STATUS_CODE.NO_SUCH_FILE);
      return;
    }
    const handle = Buffer.from(String(nextHandle++));
    opened.set(handle.toString(), Buffer.from(text));
    sftp.handle(reqId, handle);
  });
  sftp.on('FSTAT', (reqId, handle) => {
    const data = opened.get(handle.toString());
    if (data === undefined) {
      sftp.status(reqId, STATUS_CODE.FAILURE);
      return;
    }
    sftp.attrs(reqId, { mode: 0o100600, uid: 0, gid: 0, size: data.length, atime: 0, mtime: 0 });
  });
  sftp.on('READ', (reqId, handle, offset, len) => {
    const data = opened.get(handle.toString());
    if (data === undefined || offset >= data.length) {
      sftp.status(reqId, STATUS_CODE.EOF);
      return;
    }
    sftp.data(reqId, data.subarray(offset, offset + len));
  });
  sftp.on('CLOSE', (reqId, handle) => {
    opened.delete(handle.toString());
    sftp.status(reqId, STATUS_CODE.OK);
  });
}

function execReply(command: string, probe: ProbeReply): ProbeReply {
  if (command.startsWith('getent passwd')) {
    return { code: 0, stdout: `${RUN_HOME}\n` };
  }
  if (command === OWNER_ENV_PROBE_COMMAND) {
    return probe;
  }
  return command.includes('ssh-keygen') ? { code: 0 } : { code: 127 };
}

/** An SSH server on 127.0.0.1 that plays a run: it answers the commands and SFTP reads of key provisioning. */
async function startFakeRun(probe: ProbeReply): Promise<FakeRun> {
  const commands: string[] = [];
  const connections: Connection[] = [];
  const server = new Server({ hostKeys: [utils.generateKeyPairSync('ed25519').private] }, (connection) => {
    connections.push(connection);
    connection.on('authentication', (ctx) => {
      if (ctx.method === 'password' && ctx.username === RUN_USER && ctx.password === RUN_PASSWORD) {
        ctx.accept();
      } else {
        ctx.reject(['password']);
      }
    });
    connection.on('ready', () => {
      connection.on('session', (acceptSession) => {
        const session = acceptSession();
        session.on('exec', (acceptExec, _reject, info) => {
          commands.push(info.command);
          const reply = execReply(info.command, probe);
          const stream = acceptExec();
          if (reply.stdout) {
            stream.write(reply.stdout);
          }
          stream.exit(reply.code);
          stream.end();
        });
        session.on('sftp', (acceptSftp) => serveRunFiles(acceptSftp()));
      });
    });
    connection.on('error', () => undefined);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    port: (server.address() as AddressInfo).port,
    commands,
    close: () =>
      new Promise<void>((resolve) => {
        connections.forEach((c) => c.end());
        server.close(() => resolve());
      }),
  };
}

describe('provisionPasswordlessKey', () => {
  let keysDir: string;
  let warn: ReturnType<typeof mock.method>;

  beforeEach(() => {
    keysDir = mkdtempSync(join(tmpdir(), 'cp-keys-'));
    warn = mock.method(console, 'warn', () => undefined);
  });

  afterEach(() => {
    mock.restoreAll();
    rmSync(keysDir, { recursive: true, force: true });
  });

  async function provision(probe: ProbeReply) {
    const run = await startFakeRun(probe);
    try {
      const result = await provisionPasswordlessKey(
        { host: '127.0.0.1', port: run.port, username: RUN_USER, password: RUN_PASSWORD, readyTimeout: 10000 },
        42,
        keysDir,
        [RUN_USER],
        RUN_USER
      );
      return { result, commands: run.commands };
    } finally {
      await run.close();
    }
  }

  it('returns OWNER and OWNER_HOME, read in the SSH session of the key provisioning', async () => {
    const { result, commands } = await provision({
      code: 0,
      stdout: 'CP_OWNER=U1037187\nCP_OWNER_HOME=/data/users/U1037187\n',
    });

    assert.deepEqual(result.ownerEnv, { owner: 'U1037187', ownerHome: '/data/users/U1037187' });
    assert.ok(commands.includes(OWNER_ENV_PROBE_COMMAND));
    assert.equal(result.sshConfigUser, RUN_USER);
    assert.equal(result.hostRsaPubKeyBody, 'ssh-rsa AAAAHOSTKEY');
    assert.equal(readFileSync(result.privateKeyPath, 'utf8'), 'PRIVATE KEY\n');
    assert.equal(warn.mock.callCount(), 0);
  });

  it('still provisions the key, with no OWNER_HOME, if the probe fails', async () => {
    const { result } = await provision({ code: 1 });

    assert.equal(result.ownerEnv, undefined);
    assert.equal(result.hostRsaPubKeyBody, 'ssh-rsa AAAAHOSTKEY');
    assert.equal(readFileSync(result.privateKeyPath, 'utf8'), 'PRIVATE KEY\n');
    assert.equal(warn.mock.callCount(), 1);
  });
});
