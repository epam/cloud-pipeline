import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import { Client, ConnectConfig } from 'ssh2';

function randomKeyName(runId: number): string {
  return `vscode-cp-${runId}-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
}

export interface KeyProvisionResult {
  privateKeyPath: string;
  publicKeyPath: string;
  /** SSH User field for Remote-SSH (first configured user) */
  sshConfigUser: string;
  /** One line OpenSSH format for host key (key type + base64), without [host]:port prefix */
  hostRsaPubKeyBody: string;
}

function execCommand(client: Client, cmd: string): Promise<{ code: number; stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    client.exec(cmd, (err, stream) => {
      if (err) {
        reject(err);
        return;
      }
      let stdout = '';
      let stderr = '';
      stream
        .on('close', (code: number) => resolve({ code: code ?? 0, stdout, stderr }))
        .on('data', (d: Buffer) => (stdout += d.toString()))
        .stderr.on('data', (d: Buffer) => (stderr += d.toString()));
    });
  });
}

function sftpReadFile(client: Client, remotePath: string): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    client.sftp((err, sftp) => {
      if (err) {
        reject(err);
        return;
      }
      sftp.readFile(remotePath, (e, buf) => {
        if (e) {
          reject(e);
        } else {
          resolve(buf);
        }
      });
    });
  });
}

/**
 * Provisions a throwaway RSA key on the run, adds to authorized_keys for sshUsers, downloads private key.
 * Mirrors pipe-cli generate_remote_openssh_and_putty_keys + copy keys (OpenSSH path only, no putty).
 */
export async function provisionPasswordlessKey(
  connect: ConnectConfig,
  runId: number,
  keysDir: string,
  authorizedUsers: string[],
  sshConfigUser: string
): Promise<KeyProvisionResult> {
  const keyName = randomKeyName(runId);
  const remoteKeysPath = '/root/.pipe/.keys';
  const remotePrivate = `${remoteKeysPath}/${keyName}`;
  const remotePublic = `${remotePrivate}.pub`;
  const localPrivate = path.join(keysDir, keyName);
  const localPublic = `${localPrivate}.pub`;

  const users = [...new Set(authorizedUsers)];
  if (users.length === 0) {
    throw new Error('No SSH users for key provisioning');
  }

  const script = `
set -e
mkdir -p "$(dirname "${remotePrivate}")"
ssh-keygen -t rsa -f "${remotePrivate}" -N "" -q
for authorized_user in ${users.map((u) => `"${u.replace(/"/g, '\\"')}"`).join(' ')}; do
  user_home_path="$(getent passwd "$authorized_user" | cut -d: -f6)"
  user_openssh_path="$user_home_path/.ssh"
  user_authorized_keys_path="$user_openssh_path/authorized_keys"
  mkdir -p "$user_openssh_path"
  touch "$user_authorized_keys_path"
  chown -R "$authorized_user:$authorized_user" "$user_openssh_path"
  chmod 700 "$user_openssh_path"
  chmod 600 "$user_authorized_keys_path"
  cat "${remotePublic}" >> "$user_authorized_keys_path"
done
`.trim();

  const client = new Client();
  await new Promise<void>((resolve, reject) => {
    client
      .on('ready', () => resolve())
      .on('error', reject)
      .connect(connect);
  });

  let hostRsaPubKeyBody = '';
  try {
    const { code, stdout, stderr } = await execCommand(client, script);
    if (code !== 0) {
      throw new Error(`Remote keygen failed (${code}): ${stderr || stdout}`);
    }

    const privBuf = await sftpReadFile(client, remotePrivate);
    const pubBuf = await sftpReadFile(client, remotePublic);
    const hostRsa = await sftpReadFile(client, '/etc/ssh/ssh_host_rsa_key.pub');
    fs.writeFileSync(localPrivate, privBuf, { mode: 0o600 });
    fs.writeFileSync(localPublic, pubBuf, { mode: 0o644 });
    const hostBody = hostRsa.toString('utf8').trim();
    const parts = hostBody.split(/\s+/);
    hostRsaPubKeyBody = parts.length >= 2 ? `${parts[0]} ${parts[1]}` : hostBody;
  } finally {
    client.end();
  }

  return {
    privateKeyPath: localPrivate,
    publicKeyPath: localPublic,
    sshConfigUser,
    hostRsaPubKeyBody,
  };
}
