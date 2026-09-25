import { Client, SFTPWrapper } from 'ssh2';

export type SettingsMap = Record<string, unknown>;

/** Where to merge the settings on the run: the server directory under the SSH user's home. */
export interface MachineSettingsRequest {
  /** `.vscode-server`, `.vscode-server-insiders` or `.cursor-server` */
  serverDirName: string;
  /** Settings to add when missing; a `null` value skips that key. */
  settings: SettingsMap;
}

/** SFTP status code for "no such file". */
const SFTP_NO_SUCH_FILE = 2;

function isPlainObject(v: unknown): v is SettingsMap {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function withoutSkippedEntries(value: SettingsMap): SettingsMap {
  return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== null && v !== undefined));
}

/**
 * Adds the `defaults` missing from a settings.json text and never changes a value already set.
 * For object values (e.g. `files.exclude`) only the missing entries are added.
 * Throws when the text is not a plain JSON object (e.g. it holds comments).
 */
export function mergeMissingSettings(
  existingText: string | undefined,
  defaults: SettingsMap
): { text: string; changed: boolean } {
  const raw = (existingText ?? '').trim();
  const parsed: unknown = raw ? JSON.parse(raw) : {};
  if (!isPlainObject(parsed)) {
    throw new Error('settings file does not hold a JSON object');
  }
  let changed = false;
  for (const [key, value] of Object.entries(defaults)) {
    if (value === null || value === undefined) {
      continue;
    }
    const current = parsed[key];
    if (current === undefined) {
      const added = isPlainObject(value) ? withoutSkippedEntries(value) : value;
      if (!isPlainObject(added) || Object.keys(added).length > 0) {
        parsed[key] = added;
        changed = true;
      }
    } else if (isPlainObject(current) && isPlainObject(value)) {
      for (const [entry, entryValue] of Object.entries(withoutSkippedEntries(value))) {
        if (!(entry in current)) {
          current[entry] = entryValue;
          changed = true;
        }
      }
    }
  }
  return { text: `${JSON.stringify(parsed, null, 4)}\n`, changed };
}

function openSftp(client: Client): Promise<SFTPWrapper> {
  return new Promise((resolve, reject) => {
    client.sftp((err, sftp) => (err ? reject(err) : resolve(sftp)));
  });
}

function readOptionalFile(sftp: SFTPWrapper, remotePath: string): Promise<string | undefined> {
  return new Promise((resolve, reject) => {
    sftp.readFile(remotePath, (err, buf) => {
      if (err) {
        if ((err as { code?: unknown }).code === SFTP_NO_SUCH_FILE) {
          resolve(undefined);
        } else {
          reject(err);
        }
      } else {
        resolve(buf.toString('utf8'));
      }
    });
  });
}

function ensureDir(sftp: SFTPWrapper, remotePath: string): Promise<void> {
  return new Promise((resolve, reject) => {
    sftp.mkdir(remotePath, (mkdirErr) => {
      if (!mkdirErr) {
        resolve();
        return;
      }
      sftp.stat(remotePath, (statErr, stats) => {
        if (!statErr && stats.isDirectory()) {
          resolve();
        } else {
          reject(mkdirErr);
        }
      });
    });
  });
}

function writeFile(sftp: SFTPWrapper, remotePath: string, text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    sftp.writeFile(remotePath, text, (err) => (err ? reject(err) : resolve()));
  });
}

/**
 * Merges settings into the editor server's machine settings (the "Remote [SSH]" settings tab),
 * `<remoteHome>/<serverDirName>/data/Machine/settings.json`, creating it when missing.
 * @returns the file path when it was written, undefined when nothing was missing.
 */
export async function applyRemoteMachineSettings(
  client: Client,
  remoteHome: string,
  request: MachineSettingsRequest
): Promise<string | undefined> {
  const serverDir = `${remoteHome.replace(/\/$/, '')}/${request.serverDirName}`;
  const machineDir = `${serverDir}/data/Machine`;
  const settingsPath = `${machineDir}/settings.json`;

  const sftp = await openSftp(client);
  try {
    const existing = await readOptionalFile(sftp, settingsPath);
    const merged = mergeMissingSettings(existing, request.settings);
    if (!merged.changed) {
      return undefined;
    }
    for (const dir of [serverDir, `${serverDir}/data`, machineDir]) {
      await ensureDir(sftp, dir);
    }
    await writeFile(sftp, settingsPath, merged.text);
    return settingsPath;
  } finally {
    sftp.end();
  }
}
