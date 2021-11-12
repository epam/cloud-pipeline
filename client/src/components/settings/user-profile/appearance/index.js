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
import {Select} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';

import ThemeCard from './theme-card';
import styles from './appearance.css';

function AppearanceSettings ({themes, preferences}) {
  const cloudPipelineAppName = preferences.deploymentName || 'Cloud Pipeline';
  const {
    synchronizeWithSystem,
    singleTheme,
    systemDarkTheme,
    systemLightTheme,
    isSystemDarkMode
  } = themes;

  const systemModes = {day: 'light', night: 'dark'};

  const isCurrentSystemModeSelected = (mode) => isSystemDarkMode === (mode === 'dark');

  const onChangeSyncWithSystem = (value) => {
    themes.synchronizeWithSystem = value === 'auto';
    themes.applyTheme();
    themes.save();
  };
  const onChangeSingleTheme = (identifier) => {
    themes.singleTheme = identifier;
    themes.applyTheme();
    themes.save();
  };
  const onChangeLightTheme = (identifier) => {
    themes.systemLightTheme = identifier;
    themes.applyTheme();
    themes.save();
  };
  const onChangeDarkTheme = (identifier) => {
    themes.systemDarkTheme = identifier;
    themes.applyTheme();
    themes.save();
  };

  const renderSingleThemeCards = () => {
    return (
      <div className={styles.modesPanel}>
        {
          themes.themes.map(theme => (
            <ThemeCard
              key={theme.name}
              name={theme.name}
              identifier={theme.identifier}
              selected={theme.identifier === singleTheme}
              onSelect={onChangeSingleTheme}
            />
          ))
        }
      </div>
    );
  };

  const renderActiveLabel = (mode) => {
    return (
      <span
        className={
          classNames(
            styles.status,
            'cp-themes-group-active-tag',
            {
              [styles.active]: isCurrentSystemModeSelected(mode)
            }
          )
        }
      >
        Active
      </span>
    );
  };

  const renderThemesCards = (systemMode) => {
    const themeIsLight = systemMode === 'light';
    const isSelected = (themeId) => themeIsLight
      ? themeId === systemLightTheme
      : themeId === systemDarkTheme;
    return (
      <div
        className={
          classNames(
            styles.modesPanel,
            styles.centered
          )
        }
      >
        {
          themes.themes.map(theme => (
            <ThemeCard
              key={theme.name}
              name={theme.name}
              identifier={theme.identifier}
              selected={isSelected(theme.identifier)}
              onSelect={themeIsLight ? onChangeLightTheme : onChangeDarkTheme}
            />
          ))
        }
      </div>
    );
  };

  const renderSyncModesPanels = () => (
    <div className={styles.systemModesPanels}>
      {Object.entries(systemModes).map(([name, value]) => (
        <div
          key={name}
          className={
            classNames(
              styles.systemModes,
              'cp-themes-group',
              {'selected': isCurrentSystemModeSelected(value)}
            )
          }
        >
          <div className={classNames(
            styles.systemModesHeader,
            'cp-themes-group-header'
          )}>
            <span className="cp-title">{name} theme</span>
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
      <div className={classNames(styles.header, 'cp-divider', 'bottom')}>
        <h2 className="cp-title">
          {cloudPipelineAppName} UI Theme Preferences
        </h2>
      </div>
      <div
        className={
          classNames(
            styles.block,
            'cp-divider',
            'bottom'
          )
        }
      >
        Choose how {cloudPipelineAppName} interface looks to you. <br />
        You can select a single theme from the list, or configure {cloudPipelineAppName} to
        synchronize with your system appearance preferences and
        automatically switch between day and night themes.
      </div>
      <div
        className={
          classNames(
            styles.block,
            styles.row
          )
        }
      >
        <span className={styles.title}>Mode:</span>
        <Select
          value={synchronizeWithSystem ? 'auto' : 'single'}
          onChange={onChangeSyncWithSystem}
          className={styles.modeSelector}
        >
          <Select.Option value="single">Single theme</Select.Option>
          <Select.Option value="auto">Synchronize with System</Select.Option>
        </Select>
      </div>
      {synchronizeWithSystem ? renderSyncModesPanels() : renderSingleThemeCards()}
    </div>
  );
}

export default inject('themes', 'preferences')(observer(AppearanceSettings));
