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
import {Alert, Button, Icon, Modal, Tree} from 'antd';
import {inject} from 'mobx-react';
import classNames from 'classnames';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import {
  generateTreeData,
  ItemTypes,
  getTreeItemByKey
} from '../../../model/treeStructureFunctions';
import HiddenObjects from '../../../../../utils/hidden-objects';
import SplitPanel from '../../../../special/splitPanel/split-panel';
import ConfigurationPayload from './configuration-payload';
import {getProjectEntityTypeByName} from './utilities/project-utilities';
import {ParameterName, ParameterRow, ParameterValue} from './parameters/parameter';
import {AutoCompleteInput} from './parameters/auto-complete-input';
import styles from './configuration-browser.css';

function filterEntriesFn () {
  return function filterEntries (entry) {
    return !!entry.rootEntityId;
  };
}

function filterConfigurationsFn () {
  const entriesFilter = filterEntriesFn();
  return function filterConfigurations (item, type) {
    if (type === ItemTypes.configuration) {
      return item.entries.filter(entriesFilter).length > 0;
    }
    return true;
  };
}

function findFirstConfiguration (items = []) {
  const config = items.find(o => o.type === ItemTypes.configuration);
  if (config) {
    return config;
  }
  const folders = items
    .filter(o => o.type === ItemTypes.folder && o.children !== undefined && o.children.length);
  for (let f = 0; f < folders.length; f++) {
    const folder = folders[f];
    const config = findFirstConfiguration(folder.children);
    if (config) {
      return config;
    }
  }
  return undefined;
}

function getSelectionFromConfiguration (config) {
  if (!config) {
    return {
      selection: [],
      entryName: undefined
    };
  }
  const {entries = []} = config;
  const filterEntries = filterEntriesFn();
  const filteredEntries = entries.filter(filterEntries);
  if (filteredEntries.length === 0) {
    return {
      selection: [],
      entryName: undefined
    };
  }
  const defaultEntry = filteredEntries.find(o => o.default) ||
    filteredEntries[0];
  return {
    selection: [config.key],
    entryName: defaultEntry.name
  };
}

class ConfigurationBrowser extends React.Component {
  state = {
    pending: false,
    error: undefined,
    folderStructure: [],
    selection: [],
    entryName: undefined,
    metadataClassFields: [],
    configurationPayload: undefined,
    expression: undefined,
    valid: true
  };

  get selectedConfiguration () {
    const {selection = [], folderStructure = []} = this.state;
    const selectedConfigurationKey = selection[0];
    if (selectedConfigurationKey) {
      return getTreeItemByKey(selectedConfigurationKey, folderStructure);
    }
    return undefined;
  }

  get selectedConfigurationEntries () {
    const {entries = []} = this.selectedConfiguration || {};
    const filter = filterEntriesFn();
    return entries.filter(filter);
  }

  get selectedConfigurationEntry () {
    const {entryName} = this.state;
    return this.selectedConfigurationEntries.find(o => o.name === entryName);
  }

  get selectedConfigurationID () {
    const selectedConfiguration = this.selectedConfiguration;
    if (selectedConfiguration) {
      const {
        id
      } = selectedConfiguration;
      return id && !Number.isNaN(Number(id)) ? Number(id) : undefined;
    }
    return undefined;
  }

  componentDidMount () {
    this.fetchFolderStructure();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.folderId !== this.props.folderId ||
      prevProps.metadataClassName !== this.props.metadataClassName
    ) {
      this.fetchFolderStructure();
    }
  }

  fetchFolderStructure = () => {
    const {
      folderId,
      folders,
      hiddenObjectsTreeFilter,
      metadataClassName
    } = this.props;
    if (!folderId || !folders) {
      this.setState({
        error: 'Folder not specified',
        pending: false,
        folderStructure: [],
        selection: [],
        configurationPayload: undefined,
        expression: undefined,
        valid: true,
        metadataClassFields: []
      });
      return;
    }
    this.setState({
      pending: true,
      error: undefined,
      configurationPayload: undefined,
      expression: undefined,
      valid: true
    }, async () => {
      const newState = {
        pending: false,
        selection: [],
        configurationPayload: undefined,
        expression: undefined,
        valid: true
      };
      try {
        const request = folders.loadWithoutMetadata(folderId);
        await request.fetch();
        if (request.error) {
          throw new Error(request.error);
        }
        const metadataClass = await getProjectEntityTypeByName(folderId, metadataClassName);
        const filterConfigurations = filterConfigurationsFn(
          metadataClass ? metadataClass.id : undefined
        );
        const folderStructure = generateTreeData(
          request.value || {},
          {
            types: [ItemTypes.configuration],
            filter: hiddenObjectsTreeFilter
              ? hiddenObjectsTreeFilter(filterConfigurations)
              : undefined
          }
        );
        const config = findFirstConfiguration(folderStructure);
        if (!config) {
          if (metadataClassName) {
            throw new Error(
              `Configurations with "${metadataClassName}" root entity not found`
            );
          }
          throw new Error('Configurations not found');
        }
        const {
          selection = [],
          entryName
        } = getSelectionFromConfiguration(config, metadataClass ? metadataClass.id : undefined);
        newState.selection = selection;
        newState.entryName = entryName;
        newState.folderStructure = folderStructure;
        newState.metadataClassFields = metadataClass ? metadataClass.fields.map(o => o.name) : [];
      } catch (e) {
        newState.error = e.message;
        newState.folderStructure = [];
        newState.metadataClassFields = [];
      } finally {
        this.setState(newState);
      }
    });
  };

  renderModalContent = () => {
    const {
      error
    } = this.state;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    return (
      <SplitPanel>
        {this.renderConfigurationsTree()}
        {this.renderConfiguration()}
      </SplitPanel>
    );
  };

  renderConfigurationsTree = () => {
    const {
      folderStructure
    } = this.state;
    const renderTreeItem = (item) => {
      let icon;
      switch (item.type) {
        case ItemTypes.folder:
          if (item.isProject || (item.objectMetadata && item.objectMetadata.type &&
            (item.objectMetadata.type.value || '').toLowerCase() === 'project')) {
            icon = 'solution';
          } else {
            icon = 'folder';
          }
          break;
        case ItemTypes.configuration: icon = 'setting'; break;
      }
      let name = item.name;
      return (
        <span
          id={`configurations-library-tree-node-${item.key}-name`}
        >
          {
            icon && (
              <Icon
                type={icon}
                style={{marginRight: 5}}
              />
            )
          }
          <span className="name">{name}</span>
        </span>
      );
    };
    const generateTreeItems = (items) => {
      return (items || [])
        .map(item => {
          if (item.isLeaf) {
            return (
              <Tree.TreeNode
                className={
                  classNames(
                    `configurations-library-tree-node-${item.key}`,
                    styles.treeItem
                  )
                }
                title={renderTreeItem(item)}
                key={item.key}
                isLeaf={item.isLeaf}
              />
            );
          }
          return (
            <Tree.TreeNode
              className={
                classNames(
                  `configurations-library-tree-node-${item.key}`,
                  styles.treeItem
                )
              }
              title={renderTreeItem(item)}
              key={item.key}
              isLeaf={item.isLeaf}
            >
              {generateTreeItems(item.children)}
            </Tree.TreeNode>
          );
        });
    };
    const {selection = []} = this.state;
    const onSelect = (keys = []) => {
      if (keys.length === 0 || keys.find(o => /^folder/i.test(o))) {
        return;
      }
      const [firstKey] = keys;
      const configuration = getTreeItemByKey(firstKey, folderStructure);
      const {
        selection: newSelection = [],
        entryName
      } = getSelectionFromConfiguration(configuration);
      this.setState({selection: newSelection, entryName});
    };
    return (
      <SplitPanel.Pane
        className={styles.treePanel}
        key="configurations-tree"
        defaultSize={200}
      >
        <Tree
          checkStrictly
          onSelect={onSelect}
          selectedKeys={selection}
        >
          {generateTreeItems(folderStructure)}
        </Tree>
      </SplitPanel.Pane>
    );
  };

  renderConfigurationHeader () {
    const configuration = this.selectedConfiguration;
    const entries = this.selectedConfigurationEntries;
    if (!configuration) {
      return null;
    }
    const {
      entryName
    } = this.state;
    const {
      name: configName
    } = configuration;
    const onChangeConfigName = ({key}) => this.setState({
      entryName: key
    });
    const menu = (
      <div>
        <Menu
          selectedKeys={[]}
          onSelect={onChangeConfigName}
        >
          {
            entries.map(anEntry => (
              <MenuItem key={anEntry.name} title={anEntry.name}>
                {anEntry.name}
              </MenuItem>
            ))
          }
        </Menu>
      </div>
    );
    let configNameComponent = (<span>{entryName}</span>);
    if (entries.length > 1) {
      configNameComponent = (
        <Dropdown
          overlay={menu}
          trigger={['click']}
        >
          <a className="cp-text">
            {entryName}
            <Icon type="down" />
          </a>
        </Dropdown>
      );
    }
    return (
      <div
        className={
          classNames(
            styles.header,
            'cp-divider',
            'bottom'
          )
        }
        style={{
          marginBottom: 5,
          paddingBottom: 10
        }}
      >
        <b>{configName}</b>
        <span>{' - '}</span>
        {configNameComponent}
      </div>
    );
  };

  renderExpression = () => {
    const {
      expression,
      metadataClassFields
    } = this.state;
    const disabled = !this.selectedConfiguration;
    const onChange = newExpression => this.setState({
      expression: newExpression
    }, this.reportChanged);
    return (
      <ParameterRow
        className={
          classNames(
            styles.row,
            'cp-divider',
            'top'
          )
        }
        noPadding
        style={{
          marginTop: 5,
          paddingTop: 10
        }}
      >
        <ParameterName
          style={{
            padding: '4px 0',
            width: 'unset',
            minWidth: 'unset'
          }}
        >
          Define expression:
        </ParameterName>
        <ParameterValue>
          <AutoCompleteInput
            disabled={disabled}
            style={{width: '100%'}}
            value={expression}
            onChange={onChange}
            autoCompleteOptions={{
              this: metadataClassFields
            }}
          />
        </ParameterValue>
      </ParameterRow>
    );
  };

  renderConfiguration = () => {
    const {
      folderId
    } = this.props;
    const {
      entryName
    } = this.state;
    const onChange = (payload, valid) => this.setState({
      configurationPayload: payload,
      valid
    });
    return (
      <SplitPanel.Pane
        className={styles.panel}
        key="configuration"
      >
        {this.renderConfigurationHeader()}
        <ConfigurationPayload
          rootEntityDisabled
          className={styles.configuration}
          configurationId={this.selectedConfigurationID}
          folderId={folderId}
          entryName={entryName}
          onChange={onChange}
        />
        {this.renderExpression()}
      </SplitPanel.Pane>
    );
  };

  onSelect = () => {
    const {
      configurationPayload,
      expression
    } = this.state;
    const {
      onSelect
    } = this.props;
    if (typeof onSelect === 'function') {
      onSelect(configurationPayload, expression);
    }
  };

  render () {
    const {
      className,
      visible,
      onCancel
    } = this.props;
    const {
      configurationPayload,
      valid
    } = this.state;
    return (
      <Modal
        className={className}
        visible={visible}
        onCancel={onCancel}
        title="Select configuration"
        width="80%"
        bodyStyle={{padding: 0}}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              id="configuration-browser-cancel"
              onClick={onCancel}
              style={{
                marginRight: 'auto'
              }}
            >
              CANCEL
            </Button>
            <Button
              id="configuration-browser-select"
              type="primary"
              disabled={!valid || !configurationPayload}
              onClick={this.onSelect}
            >
              SELECT
            </Button>
          </div>
        )}
      >
        {this.renderModalContent()}
      </Modal>
    );
  }
}

ConfigurationBrowser.propTypes = {
  folderId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  metadataClassName: PropTypes.string,
  className: PropTypes.string,
  onSelect: PropTypes.func,
  onCancel: PropTypes.func,
  visible: PropTypes.bool
};

export default inject('folders')(
  HiddenObjects.injectTreeFilter(ConfigurationBrowser)
);
