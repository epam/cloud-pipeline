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

/** The run parameter that names the folder to open on connect. */
export const WORKSPACE_DIR_PARAMETER = 'CP_VSCODE_WORKSPACE_DIR';

/** What the SSH user's session on the run tells about the folder to open: values of the run's env file, and a check. */
export interface RemoteRunEnv {
  /** `$OWNER`, the run owner */
  owner: string;
  /** `$OWNER_HOME`, the home the platform set up for the run owner */
  ownerHome: string;
  /** `$CP_VSCODE_WORKSPACE_DIR`, the folder the run asks to open, as it is (not trimmed); empty if it is not set */
  workspaceDir: string;
  /** Whether `workspaceDir` is a folder the SSH user can open, as the session checked it */
  workspaceDirUsable: boolean;
}

const OWNER_PREFIX = 'CP_OWNER=';
const OWNER_HOME_PREFIX = 'CP_OWNER_HOME=';
// Not the parameter's own name, so a startup file that prints the environment is not taken for the probe.
const WORKSPACE_DIR_PREFIX = 'CP_PROBE_WORKSPACE_DIR=';
const WORKSPACE_DIR_USABLE_PREFIX = 'CP_PROBE_WORKSPACE_DIR_USABLE=';

/** The env file of the run, as `launch.sh` names it. */
const ENV_FILE = '"${CP_ENV_FILE_TO_SOURCE:-/etc/cp_env.sh}"';
const WORKSPACE_DIR = `"$${WORKSPACE_DIR_PARAMETER}"`;

/**
 * Prints `$OWNER`, `$OWNER_HOME` and `$CP_VSCODE_WORKSPACE_DIR` of the run, one per line, and whether
 * the SSH user can open the folder in `$CP_VSCODE_WORKSPACE_DIR`. Each value has a prefix, and the
 * output starts with a new line, so any other output of the remote shell is not mistaken for them.
 * The shell of an SSH command reads the run's env file only through a `~/.bashrc` on some images
 * (e.g. Rocky Linux), and a home the platform creates has none. So the probe reads the file itself
 * when `$OWNER_HOME` is not set.
 */
export const RUN_ENV_PROBE_COMMAND =
  `if [ -z "$OWNER_HOME" ] && [ -r ${ENV_FILE} ]; then . ${ENV_FILE} >/dev/null 2>&1; fi; ` +
  `cp_workspace_dir_usable=0; ` +
  `if [ -n ${WORKSPACE_DIR} ] && [ -d ${WORKSPACE_DIR} ] && [ -r ${WORKSPACE_DIR} ] && [ -x ${WORKSPACE_DIR} ]; ` +
  `then cp_workspace_dir_usable=1; fi; ` +
  `printf '\\n${OWNER_PREFIX}%s\\n${OWNER_HOME_PREFIX}%s\\n${WORKSPACE_DIR_PREFIX}%s\\n${WORKSPACE_DIR_USABLE_PREFIX}%s\\n' ` +
  `"$OWNER" "$OWNER_HOME" ${WORKSPACE_DIR} "$cp_workspace_dir_usable"`;

/** Reads the output of {@link RUN_ENV_PROBE_COMMAND}. A value it does not hold is empty, or `false`. */
export function parseRunEnvProbe(stdout: string): RemoteRunEnv {
  const lines = stdout.split(/\r?\n/);
  const rawValueOf = (prefix: string): string => {
    const line = lines.find((l) => l.startsWith(prefix));
    return line === undefined ? '' : line.slice(prefix.length);
  };
  const valueOf = (prefix: string): string => rawValueOf(prefix).trim();
  return {
    owner: valueOf(OWNER_PREFIX),
    ownerHome: valueOf(OWNER_HOME_PREFIX),
    // Not trimmed: the probe checked the folder at this exact path.
    workspaceDir: rawValueOf(WORKSPACE_DIR_PREFIX),
    workspaceDirUsable: valueOf(WORKSPACE_DIR_USABLE_PREFIX) === '1',
  };
}

function absolutePath(value: string): string | undefined {
  if (!value.startsWith('/')) {
    return undefined;
  }
  return value.replace(/\/+$/, '') || '/';
}

/** `$CP_VSCODE_WORKSPACE_DIR`, if it is an absolute path to a folder the SSH user can open. */
function workspaceDirToOpen(runEnv?: RemoteRunEnv): string | undefined {
  return runEnv?.workspaceDirUsable ? absolutePath(runEnv.workspaceDir) : undefined;
}

/**
 * Why `$CP_VSCODE_WORKSPACE_DIR` is set, but not opened; undefined if it is not set, or is opened.
 * `requestedWorkspaceDir` is the run parameter as the API has it: it tells whether the run asked for a
 * folder, when its environment could not be read.
 */
export function workspaceDirNotOpenedReason(runEnv?: RemoteRunEnv, requestedWorkspaceDir?: string): string | undefined {
  if (!runEnv) {
    return requestedWorkspaceDir ? 'it could not be checked on the run' : undefined;
  }
  if (!runEnv.workspaceDir || workspaceDirToOpen(runEnv) !== undefined) {
    return undefined;
  }
  return absolutePath(runEnv.workspaceDir) === undefined
    ? 'it is not an absolute path'
    : 'it is not a folder this user can open';
}

/**
 * The folder the remote window opens for `sshUser`, the first of:
 * - `$CP_VSCODE_WORKSPACE_DIR`, the folder the run asks for, for any user who can open it;
 * - `$OWNER_HOME` for the run owner, the home the platform set up for them on the run;
 * - `/root` for `root`, and `/home/<user>` for any other user, since `$OWNER_HOME` is not their home.
 * `$OWNER_HOME` and `$CP_VSCODE_WORKSPACE_DIR` count only as absolute paths.
 */
export function remoteWorkspacePath(sshUser: string, runEnv?: RemoteRunEnv): string {
  const workspaceDir = workspaceDirToOpen(runEnv);
  if (workspaceDir !== undefined) {
    return workspaceDir;
  }
  const user = (sshUser.trim() || 'root').split('@')[0];
  if (user === 'root') {
    return '/root';
  }
  const defaultHome = `/home/${user}`;
  if (!runEnv || runEnv.owner !== user) {
    return defaultHome;
  }
  return absolutePath(runEnv.ownerHome) ?? defaultHome;
}
