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

import React from 'react';
import {inject, observer} from 'mobx-react';
import {Checkbox, Select} from 'antd';

function ThemesSelector (
  {
    themes
  }
) {
  console.log(themes);
  const onChangeSyncWithSystem = (e) => {
    themes.synchronizeWithSystem = e.target.checked;
    themes.applyTheme();
    themes.save();
  };
  const onChangeSingleTheme = (o) => {
    themes.singleTheme = o;
    themes.applyTheme();
    themes.save();
  };
  const onChangeLightTheme = (o) => {
    themes.systemLightTheme = o;
    themes.applyTheme();
    themes.save();
  };
  const onChangeDarkTheme = (o) => {
    themes.systemDarkTheme = o;
    themes.applyTheme();
    themes.save();
  };
  const Config = ({children, title}) => {
    const titleComponent = title
      ? (<span style={{marginRight: 5, fontWeight: 'bold'}}>{title}:</span>)
      : undefined;
    return (
      <div style={{display: 'inline-flex', alignItems: 'center', marginLeft: 5}}>
        {titleComponent}
        {children}
      </div>
    );
  };
  return (
    <div>
      <span
        style={{
          fontWeight: 'bold',
          fontSize: 'larger',
          marginLeft: 5,
          marginRight: 10
        }}
      >
        Appearance
      </span>
      <Config>
        <Checkbox
          checked={themes.synchronizeWithSystem}
          onChange={onChangeSyncWithSystem}
        >
          Sync with system
        </Checkbox>
      </Config>
      {
        themes.synchronizeWithSystem && (
          <Config title="Light">
            <Select
              style={{width: 200, marginLeft: 5}}
              value={themes.systemLightTheme}
              onChange={onChangeLightTheme}
            >
              {
                themes.themes.map(o => (
                  <Select.Option key={o.identifier} value={o.identifier}>
                    {o.name}
                  </Select.Option>
                ))
              }
            </Select>
          </Config>
        )
      }
      {
        themes.synchronizeWithSystem && (
          <Config title="Dark">
            <Select
              style={{width: 200, marginLeft: 5}}
              value={themes.systemDarkTheme}
              onChange={onChangeDarkTheme}
            >
              {
                themes.themes.map(o => (
                  <Select.Option key={o.identifier} value={o.identifier}>
                    {o.name}
                  </Select.Option>
                ))
              }
            </Select>
          </Config>
        )
      }
      {
        !themes.synchronizeWithSystem && (
          <Config title="Theme">
            <Select
              style={{width: 200, marginLeft: 5}}
              value={themes.singleTheme}
              onChange={onChangeSingleTheme}
            >
              {
                themes.themes.map(o => (
                  <Select.Option key={o.identifier} value={o.identifier}>
                    {o.name}
                  </Select.Option>
                ))
              }
            </Select>
          </Config>
        )
      }
    </div>
  );
}

export default inject('themes')(observer(ThemesSelector));
