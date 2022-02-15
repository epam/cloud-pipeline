/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
  FILE_NAME_TEMPLATE as FILE_NAME,
  // eslint-disable-next-line
  FILE_EXTENSION_TEMPLATE as EXTENSION
} from './file-sharing-extensions';

/*
  Extension rules. Configure which files / folders should be
  added to the sharing list depending on the file extension.
  Specify array of objects:
  {
    extension: 'txt' / regular expression / array of extensions,
    relativeFolders: [<relative folders to share>]
    relativeFiles: [<relative files to share>]
  }

  For relative folders and files you MUST use forward slash (/) as a
  path delimiter.
  You can use ${FILE_NAME} and ${EXTENSION} placeholders in paths; this placeholders
  will be replaced with sharing file name (without extension) and extension.
 */

export default [
  {
    extension: /^(vsi|mrxs)$/i,
    relativeFolders: [`_${FILE_NAME}_`, '.wsiparser']
  },
  {
    extension: 'hcs',
    relativeFolders: [`.hcsparser/${FILE_NAME}`]
  }
];
