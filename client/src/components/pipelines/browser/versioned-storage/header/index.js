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
import {observer} from 'mobx-react';
import {
  Row,
  Col,
  Button,
  Icon
} from 'antd';
import localization from '../../../../../utils/localization';
import Breadcrumbs from '../../../../special/Breadcrumbs';
import roleModel from '../../../../../utils/roleModel';
import {ItemTypes} from '../../../model/treeStructureFunctions';
import styles from './header.css';

@localization.localizedComponent
@observer
class VersionedStorageHeader extends localization.LocalizedReactComponent {
  onRunClick = () => {
    const {actions = {}} = this.props;
    const {runVersionedStorage} = actions;
    runVersionedStorage && runVersionedStorage();
  };

  onGenerateReportClick = (event) => {
    const {actions = {}} = this.props;
    const {openGenerateReportDialog} = actions;
    openGenerateReportDialog && openGenerateReportDialog();
  };

  onHistoryBtnClick = () => {
    const {historyPanelOpen, actions = {}} = this.props;
    const {openHistoryPanel, closeHistoryPanel} = actions;
    if (historyPanelOpen) {
      return closeHistoryPanel && closeHistoryPanel();
    }
    return openHistoryPanel && openHistoryPanel();
  };

  onRenameStorage = (name) => {
    const {onRenameStorage} = this.props;
    onRenameStorage && onRenameStorage(name);
  };

  onSettingsClick = () => {
    const {actions} = this.props;
    actions && actions.openEditStorageDialog();
  };

  renderActions = () => {
    const {historyPanelOpen} = this.props;
    return (
      <Button
        id="display-attributes"
        style={{lineHeight: 1, marginRight: '5px'}}
        size="small"
        onClick={this.onHistoryBtnClick}
      >
        <Icon type="appstore" />
        {historyPanelOpen ? 'Hide history' : 'Show history'}
      </Button>
    );
  };

  render () {
    const {
      pipeline,
      pipelineId,
      readOnly
    } = this.props;
    if (!pipeline || !pipelineId) {
      return null;
    }
    return (
      <div className={styles.headerContainer}>
        <Row
          type="flex"
          justify="space-between"
          align="middle"
          style={{minHeight: 41}}
        >
          <Col className={styles.breadcrumbs}>
            <Breadcrumbs
              id={parseInt(pipelineId)}
              type={ItemTypes.versionedStorage}
              readOnlyEditableField={!roleModel.writeAllowed(pipeline.value) || readOnly}
              textEditableField={pipeline.value.name}
              onSaveEditableField={this.onRenameStorage}
              editStyleEditableField={{flex: 1}}
              icon="inbox"
              iconClassName="cp-versioned-storage"
              lock={pipeline.value.locked}
              subject={pipeline.value}
            />
          </Col>
          <Col className={styles.headerActions}>
            <Row type="flex" justify="end">
              {
                roleModel.executeAllowed(pipeline.value) && (
                  <Button
                    size="small"
                    type="primary"
                    onClick={this.onRunClick}
                    className={styles.controlBtn}
                    disabled={readOnly}
                  >
                    RUN
                  </Button>
                )
              }
              <Button
                size="small"
                type="primary"
                onClick={this.onGenerateReportClick}
                className={styles.controlBtn}
              >
                Generate report
              </Button>
              {this.renderActions()}
              {
                roleModel.isOwner(pipeline.value) && (
                  <Button
                    style={{lineHeight: 1}}
                    size="small"
                    onClick={this.onSettingsClick}
                    disabled={readOnly}
                  >
                    <Icon type="setting" />
                  </Button>
                )
              }
            </Row>
          </Col>
        </Row>
        <Row type="flex">
          {pipeline.value.description}
        </Row>
      </div>
    );
  }
}

VersionedStorageHeader.propTypes = {
  pipeline: PropTypes.object,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  onRenameStorage: PropTypes.func,
  actions: PropTypes.shape({
    openHistoryPanel: PropTypes.func,
    closeHistoryPanel: PropTypes.func,
    openEditStorageDialog: PropTypes.func,
    runVersionedStorage: PropTypes.func,
    openGenerateReportDialog: PropTypes.func
  }),
  readOnly: PropTypes.bool,
  controlsEnabled: PropTypes.bool,
  historyPanelOpen: PropTypes.bool
};

export default VersionedStorageHeader;
