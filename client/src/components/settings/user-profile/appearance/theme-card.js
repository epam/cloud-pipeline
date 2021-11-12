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
import PropTypes from 'prop-types';
import {Radio, Icon} from 'antd';
import classNames from 'classnames';

import styles from './appearance.css';

function ThemeCard (props) {
  const {name, identifier, selected, onSelect} = props;
  return (
    <div
      className={
        classNames(
          styles.themeCard,
          identifier,
          'theme-preview',
          {selected},
        )
      }
      onClick={() => onSelect(identifier)}
    >
      <article
        className={
          classNames(
            styles.themeVisualization,
            'cp-theme-preview-layout',
            styles.previewArticle
          )
        }
      >
        <aside className={classNames('cp-theme-preview-navigation-panel', styles.previewAside)}>
          <div className="cp-theme-preview-navigation-menu-item">
            <img className={styles.previewLogo} src="logo.png" />
          </div>
          <div className="cp-theme-preview-navigation-menu-item selected"><Icon type="home" /></div>
          <div className="cp-theme-preview-navigation-menu-item"><Icon type="fork" /></div>
          <div className="cp-runs-menu-item active"><Icon type="play-circle" /></div>
        </aside>
        <div style={{flex: '1 1 auto', display: 'flex', flexDirection: 'column'}}>
          <div className={styles.previewTopContainer}>
            <div className={classNames(styles.previewText, 'cp-theme-preview-text')}>&nbsp;</div>
            <div className={styles.previewBtnContainer}>
              <div className={classNames('cp-theme-preview-button-primary', styles.previewBtn)}>&nbsp;</div>
              <div className={classNames('cp-theme-preview-button-danger', styles.previewBtn)}>&nbsp;</div>
              <div className={classNames('cp-theme-preview-button', styles.previewBtn)}>&nbsp;</div>
            </div>
          </div>
          <div className={styles.previewPanelsBox}>
            <main className={classNames('cp-theme-preview-panel', styles.previewMain)}>
              <section className={classNames(styles.previewText, 'cp-theme-preview-text')}>&nbsp;</section>
              <section className={classNames(styles.previewSection, 'cp-theme-priview-panel-card')}>
                <Icon style={{marginLeft: '5px'}} type="play-circle-o" className="cp-theme-priview-runs-table-icon-blue" /></section>
              <section className={classNames(styles.previewSection, 'cp-theme-priview-panel-card')}>
                <Icon style={{marginLeft: '5px'}} type="clock-circle-o" className="cp-theme-priview-runs-table-icon-yellow" /></section>
              <section className={classNames(styles.previewSection, 'cp-theme-priview-panel-card')}>
                <Icon style={{marginLeft: '5px'}} type="check-circle-o" className="cp-theme-priview-runs-table-icon-green" /></section>
            </main>
            <section className={classNames('cp-theme-preview-panel', styles.previewPanel)}>&nbsp;</section>
          </div>
        </div>
      </article>
      <div className={styles.actionContainer}>
        <Radio
          checked={selected}
          onChange={() => onSelect(identifier)}
        >
          <b>{name}</b>
        </Radio>
      </div>
    </div>
  );
}

ThemeCard.propTypes = {
  name: PropTypes.string,
  identifier: PropTypes.string,
  selected: PropTypes.bool,
  onSelect: PropTypes.func
};

export default ThemeCard;
