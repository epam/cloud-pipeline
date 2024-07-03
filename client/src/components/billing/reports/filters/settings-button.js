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

import React from 'react';
import Dropdown from 'rc-dropdown';
import Menu, {MenuItem, Divider} from 'rc-menu';
import {Button, Icon} from 'antd';
import {inject, observer} from 'mobx-react';
import roleModel from '../../../../utils/roleModel';
import {restoreLayoutConsumer} from '../layout';
import {DiscountsModal} from '../discounts';
import {exportStores, renderExportMenu, onExport} from '../export';
import BillingNavigation from '../../navigation';
import styles from './settings-button.css';

const MENU_ACTIONS = {
  configureDiscounts: 'configure discounts',
  restoreLayout: 'restore layout',
  export: 'export',
  quotas: 'quotas'
};

class SettingsButton extends React.Component {
  state = {
    configureDiscountsModalVisible: false
  };

  onOpenConfigureDiscountsModal = () => {
    this.setState({configureDiscountsModalVisible: true});
  };

  onCloseConfigureDiscountsModal = () => {
    this.setState({configureDiscountsModalVisible: false});
  };

  onRestoreClick = () => {
    const {layoutContext} = this.props;
    if (layoutContext) {
      layoutContext.restore();
    }
  }

  onMenuClick = (options = {}) => {
    const {key} = options;
    const [section, ...other] = key.split('-');
    const itemKey = other.join('-');
    switch (section) {
      case MENU_ACTIONS.restoreLayout:
        this.onRestoreClick();
        break;
      case MENU_ACTIONS.configureDiscounts:
        this.onOpenConfigureDiscountsModal();
        break;
      case MENU_ACTIONS.export:
        onExport(itemKey, this.props);
        break;
      case MENU_ACTIONS.quotas: {
        const {quotas} = this.props;
        if (quotas) {
          quotas.enabled = !quotas.enabled;
        }
      }
        break;
      default:
        break;
    }
  };

  getConfigureDiscountsButtonTitle = () => {
    const {discounts} = this.props;
    const parts = [];
    const round = a => Math.round(a * 100.0) / 100.0;
    if (discounts.compute !== 0) {
      parts.push((
        <span key="compute" className={styles.discountsDetails}>
          <b>{round(discounts.compute)}%</b>
          <span style={{whiteSpace: 'pre'}}> compute</span>
        </span>
      ));
    }
    if (discounts.storage !== 0) {
      parts.push((
        <span key="storage" className={styles.discountsDetails}>
          <b>{round(discounts.storage)}%</b>
          <span style={{whiteSpace: 'pre'}}> storage</span>
        </span>
      ));
    }
    return (
      <div>
        <div>
          Configure discounts
        </div>
        {
          parts.length > 0 && (
            <div
              style={{lineHeight: '12px', fontSize: 'smaller'}}
              className={styles.discounts}
            >
              {parts}
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const isBillingManager = roleModel.isManager.billing(this);
    const {configureDiscountsModalVisible} = this.state;
    const {layoutContext, filters, quotas} = this.props;
    const restoreLayoutDisabled = !layoutContext;
    const quotasEnabled = quotas?.enabled;
    return (
      <Dropdown
        trigger={['click']}
        overlay={(
          <div>
            <Menu
              mode="vertical"
              subMenuOpenDelay={0.2}
              subMenuCloseDelay={0.2}
              openAnimation="zoom"
              getPopupContainer={node => node.parentNode}
              onClick={this.onMenuClick}
              selectedKeys={[]}
            >
              {
                isBillingManager && (
                  <MenuItem
                    id="configure-discounts"
                    className="configure-discounts-button"
                    key={MENU_ACTIONS.configureDiscounts}
                  >
                    {this.getConfigureDiscountsButtonTitle()}
                  </MenuItem>
                )
              }
              <MenuItem
                id="restore-layout"
                disabled={restoreLayoutDisabled}
                className="restore-layout-button"
                key={MENU_ACTIONS.restoreLayout}
              >
                Restore layout
              </MenuItem>
              <Divider />
              <MenuItem
                id="toggle-quotas"
                className="toggle-quotas-button"
                key={MENU_ACTIONS.quotas}
              >
                {quotasEnabled ? 'Hide' : 'Show'} quotas
              </MenuItem>
              <Divider />
              {
                renderExportMenu(
                  filters,
                  {
                    exportKeyPrefix: MENU_ACTIONS.export
                  }
                )
              }
            </Menu>
          </div>
        )}
      >
        <Button
          style={{
            padding: '0px 8px'
          }}
        >
          <Icon
            type="setting"
            style={{fontSize: 'larger'}}
          />
          <DiscountsModal
            key="modal"
            visible={configureDiscountsModalVisible}
            onClose={this.onCloseConfigureDiscountsModal}
          />
        </Button>
      </Dropdown>
    );
  }
}

export default inject(
  'discounts',
  'authenticatedUserInfo',
  'quotas',
  ...exportStores
)(
  restoreLayoutConsumer(
    BillingNavigation.attach(
      observer(SettingsButton)
    )
  )
);
