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

const LATEST_VERSION = 'latest';

/**
 * Mirrors the web UI default tag when opening Run → Run default: the tool's default version,
 * if the tool has it, else the latest version, if the tool has it, else the only version.
 */
export function defaultVersionTag(versionNames: string[], toolDefaultVersion?: string): string | undefined {
  if (toolDefaultVersion && versionNames.includes(toolDefaultVersion)) {
    return toolDefaultVersion;
  }
  if (versionNames.includes(LATEST_VERSION)) {
    return LATEST_VERSION;
  }
  if (versionNames.length === 1) {
    return versionNames[0];
  }
  return undefined;
}
