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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Select,
  Modal,
  message, Alert
} from 'antd';
import BashCode from '../../../special/bash-code';
import copyTextToClipboard from '../../../special/copy-text-to-clipboard';
import {getOS, OperationSystems} from '../../../../utils/OSDetection';

const SELECTION_PLACEHOLDERS = [
  {
    keys: ['STORAGE', 'STORAGE_ID', 'STORAGEID'],
    render: (item) => item.storageId
  },
  {
    keys: ['PATH'],
    render: (item) => item.path
  },
  {
    keys: ['NAME'],
    render: (item) => item.name
  }
];

@inject('preferences', 'dataStorages')
@observer
class SelectionDownloadCommand extends React.Component {
  state = {
    os: undefined
  };

  componentDidMount () {
    const {preferences, dataStorages} = this.props;
    preferences
      .fetchIfNeededOrWait()
      .then(() => this.setDefaultOperationSystem());
    dataStorages.fetchIfNeededOrWait();
  }

  @computed
  get commandConfiguration () {
    const {preferences} = this.props;
    if (preferences.loaded && preferences.facetedFilterDownload) {
      const {command} = preferences.facetedFilterDownload;
      return command || {};
    }
    return {};
  }

  get availableOperationSystems () {
    return Object.keys(this.commandConfiguration);
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return this.props.dataStorages.value || [];
    }
    return [];
  }

  get code () {
    const {items} = this.props;
    const currentConfiguration = this.getCurrentConfiguration();
    const {
      after,
      before,
      template
    } = currentConfiguration || {};
    const templateParts = (template || '')
      .split(/({.*?})/gi).filter(Boolean);
    const mapItemTemplate = (item) => {
      if (!template || templateParts.length === 0) {
        return undefined;
      }
      return templateParts.map((string) => {
        if (!/({.*?})/.test(string)) {
          return string;
        }
        const placeholder = string.slice(1, -1);
        if (placeholder.toLowerCase().startsWith('storage.')) {
          const storage = this.dataStorages
            .find(d => Number(d.id) === Number(item.storageId));
          const field = placeholder.split('.').pop();
          return storage && storage[field]
            ? storage[field]
            : '';
        }
        const placeholderInfo = SELECTION_PLACEHOLDERS
          .find(({keys}) => keys.includes(placeholder.toUpperCase()));
        if (placeholderInfo && placeholderInfo.render) {
          return placeholderInfo.render(item);
        }
      }).join('');
    };
    return [
      before,
      ...items.map(mapItemTemplate),
      after
    ].filter(Boolean).join('\n');
  };

  /**
   * @returns {{after: string, before: string, template: string}}
   */
  getCurrentConfiguration = () => {
    const {
      os
    } = this.state;
    if (os && this.commandConfiguration[os]) {
      return this.commandConfiguration[os];
    }
    const options = this.availableOperationSystems;
    if (options.length > 0) {
      return this.commandConfiguration[options[0]];
    }
    return {};
  };

  setDefaultOperationSystem = () => {
    const available = this.availableOperationSystems;
    const current = getOS();
    const findAvailableByName = (name) =>
      available.find((o) => o.toLowerCase().includes(name.toLowerCase()));
    const findLike = (...options) => {
      for (let i = 0; i < options.length; i += 1) {
        const found = findAvailableByName(options[i]);
        if (found) {
          return found;
        }
      }
      return available[0];
    };
    switch (current) {
      case OperationSystems.windows:
        this.setState({
          os: findLike('windows', 'win')
        });
        break;
      case OperationSystems.linux:
        this.setState({
          os: findLike('linux')
        });
        break;
      case OperationSystems.macOS:
        this.setState({
          os: findLike('macos', 'mac')
        });
        break;
      case OperationSystems.other:
      default:
        this.setState({
          os: available[0]
        });
        break;
    }
  };

  renderOperationSystemSelector = () => {
    const available = this.availableOperationSystems;
    if (available.length <= 1) {
      return null;
    }
    const onChange = (os) => this.setState({os});
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          marginBottom: 5
        }}
      >
        <span style={{marginRight: 5}}>
          Operation system:
        </span>
        <Select
          style={{width: 200}}
          value={this.state.os}
          onChange={onChange}
        >
          {
            available.map((os) => (
              <Select.Option key={os} value={os}>
                {os}
              </Select.Option>
            ))
          }
        </Select>
      </div>
    );
  };

  onClose = () => {
    const {onClose} = this.props;
    onClose && onClose();
  };

  onCopy = () => {
    copyTextToClipboard(this.code)
      .then(() => {
        message.info('Download command copied to clipboard', 3);
      }).catch((error) => {
        message.error(error.message, 3);
      });
  };

  render () {
    const currentConfiguration = this.getCurrentConfiguration();
    const {
      style,
      items,
      visible,
      skipped = 0,
      filtered = 0
    } = this.props;
    if (!items || !items.length || !currentConfiguration) {
      return null;
    }
    return (
      <Modal
        visible={visible}
        onCancel={this.onClose}
        title="Generate download command"
        width="50vw"
        footer={(
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'flex-end'
            }}
          >
            <Button
              onClick={this.onCopy}
            >
              COPY TO CLIPBOARD
            </Button>
            <Button
              onClick={this.onClose}
            >
              OK
            </Button>
          </div>
        )}
      >
        <div
          style={style}
        >
          {
            (skipped > 0 || filtered > 0) && (
              <Alert
                type="info"
                showIcon
                style={{marginBottom: 5}}
                message={(
                  <div>
                    {
                      skipped > 0 && (
                        <div>
                          {skipped} file{skipped === 1 ? ' is' : 's are'} not allowed
                          to be downloaded and therefore will be skipped
                        </div>
                      )
                    }
                    {
                      filtered > 0 && (
                        <div>
                          {filtered} file{filtered === 1 ? ' is' : 's are'} not allowed
                          to be downloaded using <b>pipe cli</b> due to the data storage type
                        </div>
                      )
                    }
                  </div>
                )}
              />
            )
          }
          {
            this.renderOperationSystemSelector()
          }
          <BashCode
            code={this.code}
            style={{maxHeight: '50vh', overflow: 'auto'}}
            breakLines
            nowrap
          />
        </div>
      </Modal>
    );
  }
}

SelectionDownloadCommand.propTypes = {
  items: PropTypes.arrayOf(PropTypes.shape({
    storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    path: PropTypes.string,
    name: PropTypes.string
  })),
  style: PropTypes.object,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  skipped: PropTypes.number,
  filtered: PropTypes.number
};

export default SelectionDownloadCommand;
