/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import PropTypes from 'prop-types';
import styles from './HomePage.css';
import {Row, Icon, Tooltip} from 'antd';
import {
  Panels,
  PanelIcons,
  PanelInfos,
  PanelTitles,
  GridStyles,
  removePanel} from './layout';
import {
  ActivitiesPanel,
  MyDataPanel,
  MyActiveRunsPanel,
  MyPipelinesPanel,
  MyProjectsPanel,
  MyServicesPanel,
  NotificationsPanel,
  PersonalToolsPanel,
  RecentlyCompletedRunsPanel
} from './panels';
import localization from '../../../utils/localization';

const PanelComponent = {
  [Panels.activities]: ActivitiesPanel,
  [Panels.data]: MyDataPanel,
  [Panels.services]: MyServicesPanel,
  [Panels.runs]: MyActiveRunsPanel,
  [Panels.notifications]: NotificationsPanel,
  [Panels.personalTools]: PersonalToolsPanel,
  [Panels.pipelines]: MyPipelinesPanel,
  [Panels.projects]: MyProjectsPanel,
  [Panels.recentlyCompletedRuns]: RecentlyCompletedRunsPanel
};

@localization.localizedComponent
export default class HomePagePanel extends localization.LocalizedReactComponent {

  static propTypes = {
    onInitialize: PropTypes.func,
    panelKey: PropTypes.string,
    onPanelRemoved: PropTypes.func,
    closable: PropTypes.bool,
    router: PropTypes.object,
    activeRuns: PropTypes.object,
    completedRuns: PropTypes.object,
    services: PropTypes.object,
    refresh: PropTypes.func
  };

  contentPanel;

  onCloseClicked = (e) => {
    if (e) {
      e.preventDefault();
    }
    removePanel(this.props.panelKey);
    this.props.onPanelRemoved && this.props.onPanelRemoved(this.props.panelKey);
  };

  renderHeader = () => {
    let title = PanelTitles[this.props.panelKey];
    if (typeof title === 'function') {
      title = title(this.localizedString);
    }
    let info = PanelInfos[this.props.panelKey];
    if (typeof info === 'function') {
      info = info(this.localizedString);
    }
    let icon;
    if (PanelIcons[this.props.panelKey]) {
      icon = (
        <Icon
          type={PanelIcons[this.props.panelKey]}
          style={{
            fontSize: 'larger',
            marginRight: 5
          }} />
      );
    }
    return (
      <Row
        type="flex"
        align="middle"
        justify="space-between">
        <Row
          className={GridStyles.draggableHandle}
          style={{flex: 1}}>
          <span className={styles.panelHeaderTitle}>
            {icon}{title}
          </span>
        </Row>
        <div className={styles.panelHeaderActions}>
          {
            info &&
            <Tooltip title={info} placement="left">
              <Icon type="question-circle" style={{fontSize: 'larger'}} />
            </Tooltip>
          }
          {
            this.props.closable &&
            <Icon
              type="close"
              onClick={this.onCloseClicked}
              style={{fontSize: 'larger'}}
              className={styles.panelHeaderCloseIcon} />
          }
        </div>
      </Row>
    );
  };

  initializeContent = (content) => {
    this.contentPanel = content;
  };

  render () {
    const Panel = PanelComponent[this.props.panelKey];
    return (
      <div key={this.props.panelKey} className={styles.panel}>
        {this.renderHeader()}
        <div className={styles.panelContent}>
          {
            Panel &&
            <Panel
              panelKey={this.props.panelKey}
              onInitialize={this.initializeContent}
              router={this.props.router}
              refresh={this.props.refresh}
              completedRuns={this.props.completedRuns}
              activeRuns={this.props.activeRuns}
              services={this.props.services} />
          }
        </div>
      </div>
    );
  }

  update () {
    this.contentPanel && this.contentPanel.update && this.contentPanel.update();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillUnmount () {
    this.props.onInitialize && this.props.onInitialize(null);
  }
}
