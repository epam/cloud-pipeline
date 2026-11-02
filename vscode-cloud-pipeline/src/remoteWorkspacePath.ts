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

/** The run owner and their home, as the run's environment (`/etc/cp_env.sh`) sets them. */
export interface RemoteOwnerEnv {
  owner: string;
  ownerHome: string;
}

const OWNER_PREFIX = 'CP_OWNER=';
const OWNER_HOME_PREFIX = 'CP_OWNER_HOME=';

/** The env file of the run, as `launch.sh` names it. */
const ENV_FILE = '"${CP_ENV_FILE_TO_SOURCE:-/etc/cp_env.sh}"';

/**
 * Prints `$OWNER` and `$OWNER_HOME` of the run, one per line. Each value has a prefix, and the
 * output starts with a new line, so any other output of the remote shell is not mistaken for them.
 * The shell of an SSH command reads the run's env file only through a `~/.bashrc` on some images
 * (e.g. Rocky Linux), and a home the platform creates has none. So the probe reads the file itself
 * when `$OWNER_HOME` is not set.
 */
export const OWNER_ENV_PROBE_COMMAND =
  `if [ -z "$OWNER_HOME" ] && [ -r ${ENV_FILE} ]; then . ${ENV_FILE} >/dev/null 2>&1; fi; ` +
  `printf '\\n${OWNER_PREFIX}%s\\n${OWNER_HOME_PREFIX}%s\\n' "$OWNER" "$OWNER_HOME"`;

/** Reads the output of {@link OWNER_ENV_PROBE_COMMAND}. A value it does not hold is empty. */
export function parseOwnerEnvProbe(stdout: string): RemoteOwnerEnv {
  const lines = stdout.split(/\r?\n/);
  const valueOf = (prefix: string): string => {
    const line = lines.find((l) => l.startsWith(prefix));
    return line === undefined ? '' : line.slice(prefix.length).trim();
  };
  return { owner: valueOf(OWNER_PREFIX), ownerHome: valueOf(OWNER_HOME_PREFIX) };
}

function absolutePath(value: string): string | undefined {
  const p = value.trim();
  if (!p.startsWith('/')) {
    return undefined;
  }
  return p.replace(/\/+$/, '') || '/';
}

/**
 * The folder the remote window opens for `sshUser`.
 * The run owner gets `$OWNER_HOME`, the home the platform set up for them on the run.
 * `root` and any other user get `/root` or `/home/<user>`, since `$OWNER_HOME` is not their home.
 * The same default applies when `$OWNER_HOME` is unknown or is not an absolute path.
 */
export function remoteWorkspacePath(sshUser: string, ownerEnv?: RemoteOwnerEnv): string {
  const user = (sshUser.trim() || 'root').split('@')[0];
  if (user === 'root') {
    return '/root';
  }
  const defaultHome = `/home/${user}`;
  if (!ownerEnv || ownerEnv.owner !== user) {
    return defaultHome;
  }
  return absolutePath(ownerEnv.ownerHome) ?? defaultHome;
}
