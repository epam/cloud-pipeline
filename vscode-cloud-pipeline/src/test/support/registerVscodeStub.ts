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
 * Makes `require('vscode')` load the stub in `./vscode`, so a test can load a module that imports
 * `vscode`. Import it before that module. Each test file runs in a process of its own, so the
 * stub never reaches another file.
 */

import Module from 'module';
import * as path from 'path';

type Resolver = (request: string, ...rest: unknown[]) => string;

const moduleInternals = Module as unknown as { _resolveFilename: Resolver };
const resolve = moduleInternals._resolveFilename;
const stubPath = path.join(__dirname, 'vscode.js');

moduleInternals._resolveFilename = function (this: unknown, request: string, ...rest: unknown[]): string {
  return request === 'vscode' ? stubPath : resolve.call(this, request, ...rest);
};
