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
import {inject, observer} from 'mobx-react';
import GridLayout from 'react-grid-layout';
import HomePagePanel from './HomePagePanel';
import ConfigureHomePage from './ConfigureHomePage';
import {
  GridStyles,
  getPanelsLayout,
  setPanelsLayout
} from './layout';
import {Button, Icon, Row} from 'antd';
import PipelineRunFilter from '../../../models/pipelines/PipelineRunSingleFilter';
import PipelineRunServices from '../../../models/pipelines/PipelineRunServices';
import roleModel from '../../../utils/roleModel';
import LoadingView from '../../special/LoadingView';
import styles from './HomePage.css';
import 'react-resizable/css/styles.css';
import 'react-grid-layout/css/styles.css';
import '../../../staticStyles/HomePage.css';
import getStyle from '../../../utils/browserDependentStyle';
import moment from 'moment-timezone';

const PAGE_SIZE = 50;
const UPDATE_TIMEOUT = 15000;

@roleModel.authenticationInfo
@inject('myIssues', 'preferences')
@inject((stores, parameters) => {
  const myRunsSubFilter = {};
  if (parameters.authenticatedUserInfo.loaded) {
    myRunsSubFilter.owners = [parameters.authenticatedUserInfo.value.userName];
  }
  return {
    activeRuns: new PipelineRunFilter({
      page: 1,
      pageSize: PAGE_SIZE,
      userModified: false,
      statuses: ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'],
      ...myRunsSubFilter
    }, true),
    completedRuns: new PipelineRunFilter({
      page: 1,
      pageSize: PAGE_SIZE,
      userModified: false,
      statuses: ['STOPPED', 'FAILURE', 'SUCCESS'],
      ...myRunsSubFilter
    }, true),
    myIssues: stores.myIssues,
    services: new PipelineRunServices({
      page: 1,
      pageSize: PAGE_SIZE,
      userModified: false,
      statuses: ['RUNNING']
    }),
    displayInfo: stores.displayInfo
  };
})
@observer
export default class HomePage extends React.Component {

  state = {
    container: null,
    containerWidth: null,
    containerHeight: null,
    configureModalVisible: false
  };

  initializeContainer = (container) => {
    if (container) {
      this.setState({
        container,
        containerWidth: container.clientWidth || window.innerWidth,
        containerHeight: container.clientHeight || window.innerHeight
      });
    }
  };

  onPanelRemoved = (k) => {
    this.panels[k] = undefined;
    this.forceUpdate();
  };

  onLayoutChanged = (layout, update = false) => {
    setPanelsLayout(layout, false);
    if (update) {
      this.forceUpdate();
    }
  };

  openConfigureModal = () => {
    this.setState({
      configureModalVisible: true
    });
  };

  closeConfigureModal = () => {
    this.setState({
      configureModalVisible: false
    }, this.forceUpdatePanels);
  };

  panels = {};

  initializePanel = (key) => (panel) => {
    this.panels[key] = panel;
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    const panelsLayout = getPanelsLayout();
    return (
      <div
        ref={this.initializeContainer}
        className={styles.globalContainer}>
        <Row
          className={styles.stickyHeader}
          style={
            this.props.displayInfo.searchFormVisible
              ? getStyle({chrome: {left: 0}, edge: {filter: 'blur(5px)'}})
              : {}
          }
          type="flex"
          align="middle"
          justify="space-between">
          <h1>{this.props.preferences.deploymentName || ''} Dashboard</h1>
          <div className={styles.stickyHeaderBackground}>{'\u00A0'}</div>
          <Button onClick={this.openConfigureModal}>
            <Icon type="setting" />Configure
          </Button>
        </Row>
        <div
          style={{paddingTop: GridStyles.top}}
          className={styles.container}>
          {
            this.state.container &&
            <GridLayout
              className="layout"
              draggableHandle={`.${GridStyles.draggableHandle}`}
              layout={panelsLayout}
              cols={GridStyles.gridCols}
              width={this.state.containerWidth - GridStyles.scrollBarSize}
              margin={[GridStyles.panelMargin, GridStyles.panelMargin]}
              containerPadding={[0, 0]}
              rowHeight={GridStyles.rowHeight(this.state.containerHeight)}
              onDragStop={(layout) => this.onLayoutChanged(layout, true)}
              onLayoutChange={(layout) => this.onLayoutChanged(layout, false)}>
              {
                panelsLayout.map(item =>
                  <div key={item.i}>
                    <HomePagePanel
                      onInitialize={this.initializePanel(item.i)}
                      refresh={this.refresh}
                      router={this.props.router}
                      panelKey={item.i}
                      onPanelRemoved={this.onPanelRemoved}
                      closable={panelsLayout.length > 1}
                      completedRuns={this.props.completedRuns}
                      activeRuns={this.props.activeRuns}
                      services={this.props.services} />
                  </div>
                )
              }
            </GridLayout>
          }
        </div>
        <ConfigureHomePage
          visible={this.state.configureModalVisible}
          onCancel={this.closeConfigureModal}
          onSave={this.closeConfigureModal} />
      </div>
    );
  }

  updateInterval;

  refresh = async () => {
    let updateActiveRuns = false;
    let updateCompletedRuns = false;
    for (let key in this.panels) {
      if (this.panels.hasOwnProperty(key) && this.panels[key] && this.panels[key].contentPanel) {
        updateActiveRuns = updateActiveRuns ||
          this.panels[key].contentPanel.usesActiveRuns;
        updateCompletedRuns = updateCompletedRuns ||
          this.panels[key].contentPanel.usesCompletedRuns;
      }
    }
    updateActiveRuns && await this.props.activeRuns.filter();
    updateCompletedRuns && await this.props.completedRuns.filter();
    await this.props.services.filter();
    await this.props.myIssues.fetch();
    this.forceUpdatePanels();
  };

  forceUpdatePanels = () => {
    for (let key in this.panels) {
      if (this.panels.hasOwnProperty(key) && this.panels[key] && this.panels[key].update) {
        this.panels[key].update();
      }
    }
  };

  resizeHandlerTimeout;

  onWindowResized = () => {
    if (this.resizeHandlerTimeout) {
      clearTimeout(this.resizeHandlerTimeout);
    }
    this.resizeHandlerTimeout = setTimeout(() => {
      if (this.state.container) {
        this.setState({
          containerWidth: this.state.container.clientWidth || window.innerWidth,
          containerHeight: this.state.container.clientHeight || window.innerHeight
        });
      }
    }, 250);
  };

  componentDidMount () {
    this.updateInterval = setInterval(() => {
      this.refresh();
    }, UPDATE_TIMEOUT);
    window.addEventListener('resize', this.onWindowResized);
  }

  componentWillUnmount () {
    clearInterval(this.updateInterval);
    localStorage.setItem('LAST_VISITED', moment.utc().format('YYYY-MM-DD HH:mm:ss'));
    window.removeEventListener('resize', this.onWindowResized);
  }
}
