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

export const FILE_NAME_TEMPLATE = '<FILE_NAME>';
export const FILE_EXTENSION_TEMPLATE = '<FILE_EXTENSION>';

function parseFileInfo (filePath, delimiter = '/') {
  if (filePath) {
    const pathComponents = filePath.split(delimiter);
    const fileNameWithExtension = pathComponents.pop();
    const fileComponents = fileNameWithExtension.split('.');
    const extension = fileComponents.pop();
    const fileName = fileComponents.join('.');
    const parentFolder = pathComponents.join(delimiter);
    return {
      parentFolder,
      fileName,
      extension,
      fileNameWithExtension,
      delimiter
    };
  }
  return undefined;
}

function shareRelativeItems (relativeItems = [], appendix = undefined) {
  return (parseOptions) => {
    if (parseOptions) {
      const {
        parentFolder,
        fileName,
        extension,
        delimiter
      } = parseOptions;
      const FILE_NAME_REG_EXP = new RegExp(`${FILE_NAME_TEMPLATE}`, 'ig');
      const FILE_EXTENSION_REG_EXP = new RegExp(`${FILE_EXTENSION_TEMPLATE}`, 'ig');
      const relativeItemsParsed = relativeItems
        .map(o => o.replace(FILE_NAME_REG_EXP, fileName))
        .map(o => o.replace(FILE_EXTENSION_REG_EXP, extension))
        .map(o => o.replace(/[/]/g, delimiter))
        .map(o => o.split(delimiter).filter(o => o.length).join(delimiter));
      return relativeItemsParsed
        .map(relativeItem => [parentFolder, relativeItem, appendix]
          .filter(Boolean)
          .join(delimiter)
        );
    }
    return [];
  };
}

export function generateRule (ruleDescription) {
  const {
    extension,
    relativeFolders = [],
    relativeFiles = []
  } = ruleDescription;
  if (extension) {
    let testRegExp;
    if (typeof extension.test === 'function') {
      testRegExp = extension;
    } else {
      const prepare = o => o.startsWith('.') ? o.slice(1) : o;
      const extensions = (Array.isArray(extension) ? extension : [extension])
        .map(prepare);
      testRegExp = new RegExp(`^(${extensions.join('|')})$`, 'i');
    }
    return function extend (filePath, delimiter = '/') {
      const parsed = parseFileInfo(filePath, delimiter);
      const {extension: fileExtension} = parsed;
      if (testRegExp.test(fileExtension)) {
        return [
          ...shareRelativeItems(relativeFolders, '**')(parsed),
          ...shareRelativeItems(relativeFiles)(parsed)
        ];
      }
      return [];
    };
  }
  return function byPass () {
    return [];
  };
}
