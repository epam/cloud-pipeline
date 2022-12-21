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
import PropTypes from 'prop-types';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  Badge,
  Button,
  Icon
} from 'antd';
import RcMenu, {MenuItem, SubMenu, Divider as MenuDivider} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {SearchItemTypes} from '../../../../../models/search';
import SharedItemInfo
  from '../../../../pipelines/browser/forms/data-storage-item-sharing/SharedItemInfo';
import SelectionPreview from '../selection-preview';

function getItemType (item) {
  if (
    item.type === SearchItemTypes.s3File ||
    item.type === SearchItemTypes.azFile ||
    item.type === SearchItemTypes.gsFile
  ) {
    return 'file';
  }
  return item.type;
};

@inject('dataStorages')
@observer
class SharingControl extends React.Component {
  state = {
    itemsToShare: [],
    shareDialogVisible: false,
    showSelectionPreview: false,
    dropDownVisible: false
  };

  componentDidMount () {
    const {dataStorages} = this.props;
    dataStorages.fetchIfNeededOrWait();
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return this.props.dataStorages.value || [];
    }
    return [];
  }

  get items () {
    const {items} = this.props;
    return (items || []).map(item => ({
      storageId: item.parentId,
      path: item.path,
      name: item.name,
      type: getItemType(item)
    }));
  }

  get groupedItems () {
    return this.items.reduce((acc, item) => {
      if (!acc[item.storageId]) {
        acc[item.storageId] = [];
      }
      acc[item.storageId].push(item);
      return acc;
    }, {});
  }

  openShareStorageItemsDialog = (items) => {
    if (!items) {
      return;
    }
    this.setState({
      itemsToShare: items,
      shareDialogVisible: true,
      showSelectionPreview: false
    });
  };

  closeShareItemDialog = () => {
    return this.setState({
      itemsToShare: [],
      shareDialogVisible: false
    });
  };

  openSelectionPreview = () => {
    const {items} = this.props;
    if (items && items.length > 0) {
      this.setState({showSelectionPreview: true});
    }
  };

  closeSelectionPreview = () => {
    this.setState({
      itemsToShare: [],
      showSelectionPreview: false
    });
  };

  clearSelection = () => {
    const {onClearSelection} = this.props;
    onClearSelection && onClearSelection();
    this.closeSelectionPreview();
  };

  onDownload = (items) => {
    console.log('items to download: ', items);
  };

  handleMenuClick = ({key}) => {
    if (key.startsWith('shareGroup')) {
      const storageId = key.split('|').pop();
      this.openShareStorageItemsDialog(this.groupedItems[storageId]);
    } else if (key === 'share') {
      this.openShareStorageItemsDialog(this.items);
    } else if (key === 'clear') {
      this.clearSelection();
    } else if (key === 'download') {
      this.onDownload(this.items);
    } else if (key === 'show') {
      this.openSelectionPreview();
    }
  };

  renderMenuOverlay = () => {
    const {items} = this.props;
    const isMultipleStorageItems = [...new Set(items.map(item => item.parentId))].length > 1;
    const getStorageName = id => {
      const storage = this.dataStorages.find(d => Number(d.id) === Number(id));
      return storage && storage.name
        ? storage.name
        : id;
    };
    const shareSubMenu = isMultipleStorageItems
      ? (
        <SubMenu
          title={(
            <span>
              <b style={{marginRight: 3}}>
                Share
              </b>
              selected
            </span>
          )}
        >
          {Object.entries(this.groupedItems).map(([groupId, items]) => (
            <MenuItem key={`shareGroup|${groupId}`}>
              Share <b>{items.length}</b> item{items.length > 1 ? 's' : ''}
              &nbsp;from <b>{getStorageName(groupId)}</b>
            </MenuItem>
          ))}
        </SubMenu>
      ) : (
        <MenuItem key="share">
          <b>Share</b> selected
        </MenuItem>
      );
    return (
      <RcMenu
        onClick={this.handleMenuClick}
        selectedKeys={[]}
        subMenuOpenDelay={0.2}
        subMenuCloseDelay={0.2}
        openAnimation="zoom"
        getPopupContainer={node => node.parentNode}
      >
        {shareSubMenu}
        <MenuItem key="download">
          <b>Download</b> selected
        </MenuItem>
        <MenuItem key="show">
          <b>Display</b> selected
        </MenuItem>
        <MenuDivider />
        <MenuItem
          key="clear"
          className="cp-danger"
        >
          Clear selection
        </MenuItem>
      </RcMenu>
    );
  };

  render () {
    const {
      items,
      visible,
      extraColumns
    } = this.props;
    const {showSelectionPreview, dropDownVisible} = this.state;
    if (!items || !visible) {
      return null;
    }
    return (
      <div style={{margin: '0 5px'}}>
        <Badge count={(items || []).length} style={{zIndex: 999}}>
          <Dropdown
            overlay={<div>{this.renderMenuOverlay()}</div>}
            trigger={['click']}
            visible={dropDownVisible}
            onVisibleChange={(visible) => this.setState({
              dropDownVisible: visible
            })}
          >
            <Button
              size="large"
              style={{width: 35, padding: 0}}
            >
              <Icon type="export" />
            </Button>
          </Dropdown>
        </Badge>
        <SharedItemInfo
          visible={this.state.shareDialogVisible}
          shareItems={this.state.itemsToShare}
          close={this.closeShareItemDialog}
        />
        <SelectionPreview
          title="Selected files"
          visible={showSelectionPreview}
          extraColumns={extraColumns}
          items={items}
          onClose={this.closeSelectionPreview}
          onClear={this.clearSelection}
          onDownload={this.onDownload}
        />
      </div>
    );
  }
}

SharingControl.propTypes = {
  visible: PropTypes.bool,
  items: PropTypes.array,
  extraColumns: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    name: PropTypes.string
  })),
  onClearSelection: PropTypes.func
};

export default SharingControl;
