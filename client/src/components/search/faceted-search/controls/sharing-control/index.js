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
import SharedItemInfo
from '../../../../pipelines/browser/forms/data-storage-item-sharing/SharedItemInfo';
import SelectionPreview from '../selection-preview';
import {
  filterDownloadableItems,
  itemSharingAvailable
} from '../../../utilities/elastic-item-utilities';

@inject('dataStorages', 'preferences')
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
      ...item,
      storageId: item.parentId
    }));
  }

  get shareableItems () {
    return this.items.filter(itemSharingAvailable);
  }

  get groupedItems () {
    const storages = [...new Set(this.shareableItems.map((item) => Number(item.storageId)))]
      .filter(id => !Number.isNaN(id));
    return storages.map((storageId) => ({
      storageId,
      items: this.shareableItems.filter((item) => Number(item.storageId) === storageId)
    }));
  }

  get downloadableItems () {
    const {
      preferences,
      notDownloadableStorages = []
    } = this.props;
    if (!preferences.loaded) {
      return [];
    }
    return filterDownloadableItems(
      this.items,
      preferences,
      notDownloadableStorages
    );
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
    this.setDropDownVisibility(false);
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
      this.setDropDownVisibility(false);
    }
  };

  closeSelectionPreview = () => {
    this.setState({
      itemsToShare: [],
      showSelectionPreview: false
    });
  };

  setDropDownVisibility = (visible) => {
    this.setState({dropDownVisible: visible});
  };

  clearSelection = () => {
    const {onClearSelection} = this.props;
    onClearSelection && onClearSelection();
    this.closeSelectionPreview();
    this.setDropDownVisibility(false);
  };

  onSelectionPreviewDownload = (items) => {
    this.onDownload((items || []).map(item => ({
      ...item,
      storageId: item.parentId
    })));
  };

  onDownload = (items) => {
    const {
      onDownload
    } = this.props;
    if (typeof onDownload === 'function') {
      onDownload(items);
    }
    this.setDropDownVisibility(false);
  };

  handleMenuClick = ({key}) => {
    if (key.startsWith('shareGroup')) {
      const storageId = key.split('|').pop();
      const group = this.groupedItems.find((group) => group.storageId === Number(storageId));
      if (group) {
        this.openShareStorageItemsDialog(group.items);
      }
    } else if (key === 'share') {
      this.openShareStorageItemsDialog(this.shareableItems);
    } else if (key === 'clear') {
      this.clearSelection();
    } else if (key === 'download') {
      this.onDownload(this.downloadableItems);
    } else if (key === 'show') {
      this.openSelectionPreview();
    }
  };

  renderMenuOverlay = () => {
    const {dataStorageSharingEnabled} = this.props;
    const isMultipleStorageItems = this.groupedItems.length > 1;
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
          {
            this.groupedItems.map((group) => (
              <MenuItem key={`shareGroup|${group.storageId}`}>
                Share <b>{group.items.length}</b> item{group.items.length > 1 ? 's' : ''}
                &nbsp;from <b>{getStorageName(group.storageId)}</b>
              </MenuItem>
            ))
          }
        </SubMenu>
      ) : (
        <MenuItem key="share">
          <b>Share</b> selected
        </MenuItem>
      );
    const skipDownloadCount = this.items.length - this.downloadableItems.length;
    return (
      <RcMenu
        onClick={this.handleMenuClick}
        selectedKeys={[]}
        subMenuOpenDelay={0.2}
        subMenuCloseDelay={0.2}
        openAnimation="zoom"
        getPopupContainer={node => node.parentNode}
      >
        {dataStorageSharingEnabled && this.shareableItems.length > 0 && shareSubMenu}
        {
          this.downloadableItems.length > 0 && (
            <MenuItem key="download">
              <b>Download</b> selected
              {
                skipDownloadCount > 0 && ` (${this.downloadableItems.length})`
              }
              {
                skipDownloadCount > 0 && (
                  <div
                    style={{lineHeight: '12px', fontSize: 'smaller'}}
                    className="cp-text-not-important"
                  >
                    {skipDownloadCount} file{skipDownloadCount === 1 ? ' is' : 's are'} not allowed
                    to be downloaded
                    <br />
                    and therefore will be skipped
                  </div>
                )
              }
            </MenuItem>
          )
        }
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
      extraColumns,
      size,
      notDownloadableStorages
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
            onVisibleChange={this.setDropDownVisibility}
          >
            <Button
              size={size}
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
          onDownload={this.onSelectionPreviewDownload}
          notDownloadableStorages={notDownloadableStorages}
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
  onClearSelection: PropTypes.func,
  size: PropTypes.string,
  onDownload: PropTypes.func,
  dataStorageSharingEnabled: PropTypes.bool,
  notDownloadableStorages: PropTypes.arrayOf(PropTypes.number)
};

export default SharingControl;
