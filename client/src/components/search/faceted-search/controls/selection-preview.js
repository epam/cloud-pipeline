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
import {
  Alert,
  Checkbox,
  Button,
  Modal
} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import DocumentListPresentation from '../document-presentation/list';
import * as elasticItemUtilities from '../../utilities/elastic-item-utilities';
import SelectionInfo from './selection-info';
import styles from '../search-results.css';

@inject('preferences')
@observer
class SelectionPreview extends React.Component {
  state = {
    selection: [],
    removedItems: []
  };

  get actualSelection () {
    const {items = [], preferences} = this.props;
    const {removedItems = []} = this.state;
    const notRemoved = items.filter(o => !removedItems
      .find(elasticItemUtilities.filterMatchingItemsFn(o))
    );
    return elasticItemUtilities.filterDownloadableItems(notRemoved, preferences);
  }

  get selectionInfo () {
    return this.actualSelection.map(selection => ({
      storageId: selection.parentId,
      path: selection.path,
      name: selection.name
    }));
  }

  get notAllowedToDownload () {
    const {items = [], preferences} = this.props;
    return items.filter((item) => !elasticItemUtilities.itemIsDownloadable(item, preferences));
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {items = []} = this.props;
    this.setState({
      selection: items.slice(),
      removedItems: []
    });
  };

  itemIsSelected = (item) => {
    const {removedItems = []} = this.state;
    const findFn = elasticItemUtilities.filterMatchingItemsFn(item);
    return !removedItems.find(findFn);
  };

  toggleSelection = (item) => e => {
    const {removedItems = []} = this.state;
    const selected = this.itemIsSelected(item);
    if (e.target.checked && !selected) {
      const filterNonMatching = elasticItemUtilities.filterNonMatchingItemsFn(item);
      this.setState({
        removedItems: removedItems.filter(filterNonMatching)
      });
    } else if (!e.target.checked && selected) {
      this.setState({
        removedItems: [...removedItems, item]
      });
    }
  };

  onDownloadClicked = () => {
    const {onDownload} = this.props;
    if (typeof onDownload === 'function') {
      onDownload(this.actualSelection);
    }
  };

  render () {
    const {
      extraColumns = [],
      onClose,
      onClear,
      title = 'Selected documents',
      visible
    } = this.props;
    const {selection = []} = this.state;
    const skipped = this.notAllowedToDownload.length;
    return (
      <Modal
        visible={visible}
        onCancel={onClose}
        title={title}
        width="50vw"
        footer={(
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}
          >
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <div>
              <Button
                style={{
                  marginRight: 5
                }}
                onClick={onClear}
              >
                CLEAR SELECTION
              </Button>
              <Button
                disabled={this.actualSelection.length === 0}
                onClick={this.onDownloadClicked}
                type="primary"
              >
                DOWNLOAD
                {
                  skipped > 0 && ` (${this.actualSelection.length})`
                }
              </Button>
            </div>
          </div>
        )}
      >
        <div style={{maxHeight: '50vh', overflow: 'auto'}}>
          {
            skipped > 0 && (
              <Alert
                type="info"
                showIcon
                style={{marginBottom: 5}}
                message={(
                  <div>
                    {skipped} file{skipped === 1 ? ' is' : 's are'} not allowed
                    to be downloaded and therefore will be skipped
                  </div>
                )}
              />
            )
          }
          {
            selection.map(document => (
              <div
                key={`${document.elasticId}`}
                id={`search-result-item-${document.elasticId}`}
                className={
                  classNames(
                    styles.resultItem,
                    'cp-even-odd-element'
                  )
                }
                style={{cursor: 'default', margin: '2px 0'}}
              >
                <Checkbox
                  checked={this.itemIsSelected(document)}
                  onChange={this.toggleSelection(document)}
                  style={{marginRight: 5}}
                />
                <DocumentListPresentation
                  className={styles.title}
                  document={document}
                  extraColumns={extraColumns}
                />
              </div>
            ))
          }
          <SelectionInfo
            items={this.selectionInfo}
            style={{marginTop: 10}}
          />
        </div>
      </Modal>
    );
  }
}

SelectionPreview.propTypes = {
  extraColumns: PropTypes.array,
  items: PropTypes.array,
  onClose: PropTypes.func,
  onClear: PropTypes.func,
  onDownload: PropTypes.func,
  title: PropTypes.string,
  visible: PropTypes.bool
};

export default SelectionPreview;
