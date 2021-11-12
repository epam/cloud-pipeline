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
import {Select, Radio} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';

import ThemeCard from './themeCard';
import styles from './appearance.css';

const RadioGroup = Radio.Group;

function AppearanceSettings ({themes}) {
  const {
    synchronizeWithSystem,
    singleTheme,
    systemDarkTheme,
    systemLightTheme} = themes;

  const systemModes = {day: 'light', night: 'dark'};

  const isSystemDarkMode = () => window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  const isCurrentSystemModeSelected = (mode) => (isSystemDarkMode() && mode === 'dark') || (!isSystemDarkMode() && mode === 'light');

  const onChangeSyncWithSystem = (value) => {
    themes.synchronizeWithSystem = value === 'auto';
    themes.applyTheme();
    themes.save();
  };
  const onChangeSingleTheme = (e) => {
    themes.singleTheme = e.target.value;
    themes.applyTheme();
    themes.save();
  };
  const onChangeLightTheme = (e) => {
    themes.systemLightTheme = e.target.value;
    themes.applyTheme();
    themes.save();
  };
  const onChangeDarkTheme = (e) => {
    themes.systemDarkTheme = e.target.value;
    themes.applyTheme();
    themes.save();
  };

  const renderSingleThemeCards = () => {
    return (
      <div className={styles.modesPanel}>
        <RadioGroup
          onChange={onChangeSingleTheme}
          className={classNames(styles.cardsGroup)}
          value={singleTheme}>
          {themes.themes.map(theme => (
            <ThemeCard
              key={theme.name}
              name={theme.name}
              identifier={theme.identifier}
              selected={theme.identifier === singleTheme}
            />
          ))}
        </RadioGroup>
      </div>
    );
  };

  const renderActiveLabel = (mode) => {
    if (isCurrentSystemModeSelected(mode)) {
      return (<span className={styles.status}>Active</span>);
    } else {
      return null;
    }
  };

  const renderThemesCards = (systemMode) => {
    const themeIsLight = systemMode === 'light';
    const isSelected = (themeId) => themeIsLight ? themeId === systemLightTheme : themeId === systemDarkTheme;
    return (
      <div className={styles.modesPanel}>
        <RadioGroup
          onChange={themeIsLight ? onChangeLightTheme : onChangeDarkTheme}
          className={classNames(styles.cardsGroup, styles.systemCardPanel)}
          value={themeIsLight ? systemLightTheme : systemDarkTheme}>
          {themes.themes.map(theme => (
            <ThemeCard
              key={theme.name}
              name={theme.name}
              identifier={theme.identifier}
              selected={isSelected(theme.identifier)}
            />
          ))}
        </RadioGroup>
      </div>
    );
  };

  const renderSyncModesPanels = () => (
    <div className={styles.systemModesPanels}>
      {Object.entries(systemModes).map(([name, value]) => (
        <div key={name} className={styles.systemModes}>
          <div className={classNames(
            styles.systemModesHeader,
            {[styles.selected]: isCurrentSystemModeSelected(value)}
          )}>
            <div>
              <h2>{name} theme</h2>
            </div>
            {renderActiveLabel(value)}
          </div>
          <h3 className={styles.description}>
            This theme will be active when your system is set to "{value} mode"
          </h3>
          {renderThemesCards(value)}
        </div>
      ))}
    </div>
  );
  return (
    <div className={styles.appearanceSettings}>
      <h2>Appearance</h2>
      <div className={styles.selectThemeModeContainer}>
        <Select
          size="large"
          value={synchronizeWithSystem ? 'auto' : 'single'}
          onChange={onChangeSyncWithSystem}

        >
          <Select.Option value="single">Single theme</Select.Option>
          <Select.Option value="auto">Sync with System</Select.Option>
        </Select>
        <span>CloudPipeline will use your selected theme</span>
      </div>
      {synchronizeWithSystem ? renderSyncModesPanels() : renderSingleThemeCards()}
    </div>
  );
}

export default inject('themes')(observer(AppearanceSettings));
