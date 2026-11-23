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

/**
 * Stands in for the `vscode` module, which exists only inside the editor, once
 * `registerVscodeStub` is loaded. Holds only the part of the API the tested code calls,
 * and records the commands it executes and the warnings it shows.
 */

export const executedCommands: unknown[][] = [];
export const shownWarnings: string[] = [];

export const Uri = {
  from(components: { scheme: string; authority?: string; path?: string }): object {
    return { ...components };
  },
};

export const commands = {
  async executeCommand(...args: unknown[]): Promise<undefined> {
    executedCommands.push(args);
    return undefined;
  },
};

export const window = {
  /** As in the editor, settles only when the user closes the warning: here, never. */
  showWarningMessage(message: string): Promise<undefined> {
    shownWarnings.push(message);
    return new Promise(() => undefined);
  },
};
