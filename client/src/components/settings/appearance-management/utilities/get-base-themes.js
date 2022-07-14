/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

function isParentForTheme (theme, parent, themes = []) {
  if (!parent || !theme) {
    return false;
  }
  const {extends: baseThemeIdentifier} = theme;
  const {identifier} = parent;
  return identifier === baseThemeIdentifier ||
    isParentForTheme(
      theme,
      themes.find(o => o.identifier === baseThemeIdentifier),
      themes
    );
}

function isChildForTheme (theme, child, themes = []) {
  return isParentForTheme(child, theme, themes);
}

export default function getBaseThemes (themes, nestedTheme) {
  if (!nestedTheme || !nestedTheme.identifier) {
    return (themes || []);
  }
  return (themes || [])
    .filter(theme => theme.identifier !== nestedTheme.identifier &&
      (
        isParentForTheme(nestedTheme, theme, themes) ||
        !isChildForTheme(theme, nestedTheme, themes)
      )
    );
}
