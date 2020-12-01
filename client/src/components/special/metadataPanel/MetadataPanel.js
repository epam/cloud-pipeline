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
// todo: move MetadataStyles file
import MetadataStyles from '../metadata/Metadata.css';
import {Button, Icon, Input, message, Modal, Row} from 'antd';
import MetadataEntityDeleteKey from '../../../models/folderMetadata/MetadataEntityDeleteKey';
import MetadataEntityDelete from '../../../models/folderMetadata/MetadataEntityDelete';
import MetadataEntityUpdateKey from '../../../models/folderMetadata/MetadataEntityUpdateKey';
import MetadataEntitySave from '../../../models/folderMetadata/MetadataEntitySave';
import PropTypes from 'prop-types';

@inject((args, params) => ({
  currentItem: params.currentItem,
  entityId: params.currentItem && params.currentItem.rowKey
    ? params.currentItem.rowKey.value
    : null
}))
@observer
export default class MetadataPanel extends React.Component {
  static propTypes = {
    readOnly: PropTypes.bool,
    readOnlyKeys: PropTypes.array,
    columnNamesFn: PropTypes.func,
    classId: PropTypes.number,
    className: PropTypes.string,
    entityId: PropTypes.number,
    entityName: PropTypes.string,
    externalId: PropTypes.string,
    parentId: PropTypes.number,
    currentItem: PropTypes.object,
    onUpdateMetadata: PropTypes.func
  };

  static defaultProps = {
    readOnlyKeys: ['ID'],
    columnNamesFn: (o => o)
  };

  state = {
    editableKey: null,
    editableValue: null,
    editableText: null,
    addKey: null,
    undoItems: []
  };

  discardChanges = () => {
    this.setState({
      addKey: null,
      editableKey: null,
      editableValue: null,
      editableText: null
    });
  };

  onMetadataChange = (e) => {
    this.setState({editableText: e.target.value});
  };

  saveMetadata = (field, key, value, isUndo = false) => async () => {
    const editableText = this.state.editableText;
    const currentField = this.props.currentItem[key];
    const currentValue = currentField ? currentField.value : null;
    const newCurrentItem = {};

    if (field === 'value') {
      if (editableText === currentValue) {
        this.setState({
          addKey: null,
          editableKey: null,
          editableValue: null,
          editableText: null,
          undoItems: this.state.undoItems
        });
        return;
      }
      currentField.value = this.state.editableText;

      const data = {};
      data[key] = currentField;

      const metadataRequest = new MetadataEntityUpdateKey();
      const metadataEntity = {
        data: data,
        entityId: this.props.entityId,
        parentId: this.props.parentId
      };
      await metadataRequest.send(metadataEntity);
      if (metadataRequest.error) {
        message.error(metadataRequest.error, 5);
        currentField.value = currentValue;
      } else {
        this.props.onUpdateMetadata();
        if (!isUndo) {
          this.state.undoItems.push({field, key, value, editableText});
        }
      }
    } else {
      if (this.state.addKey) {
        if (!this.state.addKey.key ||
          !this.state.addKey.key.length ||
          !this.state.addKey.key.trim().length) {
          message.error('Enter key', 5);
          return;
        }
        if (currentField) {
          message.error(`Key '${this.state.addKey.key}' already exists.`, 5);
          return;
        }
        if (!this.state.addKey.value || !this.state.addKey.value.length) {
          message.error('Enter value', 5);
          return;
        }

        newCurrentItem[key] = {
          type: 'string',
          value: value
        };
      } else {
        if (!editableText || !editableText.length || !editableText.trim().length) {
          message.error('Key should not be empty', 5);
          return;
        }

        newCurrentItem[editableText] = {
          type: this.props.currentItem[key].type,
          value: value
        };
      }

      for (let k in this.props.currentItem) {
        if (k !== 'rowKey' && (this.props.readOnlyKeys || []).indexOf(k) === -1 && k !== key) {
          newCurrentItem[k] = this.props.currentItem[k];
        }
      }

      const metadataRequest = new MetadataEntitySave();
      const metadataEntity = {
        data: newCurrentItem,
        classId: this.props.classId,
        className: this.props.className,
        entityId: this.props.entityId,
        entityName: this.props.entityName,
        externalId: this.props.externalId,
        parentId: this.props.parentId
      };
      await metadataRequest.send(metadataEntity);
      if (metadataRequest.error) {
        message.error(metadataRequest.error, 5);
      } else {
        this.props.onUpdateMetadata();
        if (!isUndo && !this.state.addKey) {
          this.state.undoItems.push({field, key, value, editableText});
        }
      }
    }

    this.setState({
      addKey: null,
      editableKey: null,
      editableValue: null,
      editableText: null,
      undoItems: this.state.undoItems
    });
  }

  onMetadataEditStarted = (field, key, value) => () => {
    if (this.props.readOnly) {
      return;
    }
    if (field === 'key') {
      this.setState({addKey: null, editableKey: key, editableValue: null, editableText: key});
    } else if (field === 'value') {
      this.setState({addKey: null, editableKey: null, editableValue: key, editableText: value});
    }
  }

  autoFocusInputRef = (input) => {
    if (input && input.refs && input.refs.input && input.refs.input.focus) {
      input.refs.input.focus();
    }
  }

  confirmDeleteMetadata = () => {
    Modal.confirm({
      title: 'Do you want to delete metadata?',
      style: {
        wordWrap: 'break-word'
      },
      content: null,
      okText: 'OK',
      cancelText: 'Cancel',
      onOk: async () => {
        const request = new MetadataEntityDelete(
          this.props.entityId
        );
        await request.fetch();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.props.onUpdateMetadata();
        }
      }
    });
  };

  confirmDeleteKey = (item) => {
    Modal.confirm({
      title: `Do you want to delete key "${item}"?`,
      style: {
        wordWrap: 'break-word'
      },
      content: null,
      okText: 'OK',
      cancelText: 'Cancel',
      onOk: async () => {
        const request = new MetadataEntityDeleteKey(
          this.props.entityId,
          item
        );
        await request.fetch();
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.props.onUpdateMetadata();

          this.state.undoItems = this.state.undoItems.filter(undoItem => {
            if (undoItem.field === 'key') {
              return undoItem.editableText !== item;
            } else {
              return undoItem.key !== item;
            }
          });
          this.setState({
            undoItems: this.state.undoItems
          });
        }
      }
    });
  }

  onCancelChanges = () => {
    if (this.state.undoItems.length > 0) {
      const lastItem = this.state.undoItems.pop();
      const key = lastItem.field === 'key' ? lastItem.editableText : lastItem.key;
      const value = lastItem.field === 'key' ? lastItem.value : lastItem.editableText;
      this.setState({
        editableText: lastItem.field === 'key' ? lastItem.key : lastItem.value,
        undoItems: this.state.undoItems
      }, () => {
        this.saveMetadata(lastItem.field, key, value, true)();
      });
    }
  }

  renderAddKeyRow = () => {
    if (this.state.addKey) {
      const addKeyCancelClicked = () => {
        this.setState({editableKey: null, editableValue: null, editableText: null, addKey: null});
      };

      const refKeyInput = (input) => {
        if (this.state.addKey &&
          !this.state.addKey.autofocused &&
          input && input.refs && input.refs.input && input.refs.input.focus) {
          input.refs.input.focus();
          const addKey = this.state.addKey;
          addKey.autofocused = true;
          this.setState({addKey});
        }
      };

      const onChange = (field) => (e) => {
        const addKey = this.state.addKey;
        addKey[field] = e.target.value;
        this.setState({addKey});
      };

      const onEnter = (e) => {
        e.stopPropagation();
        this.saveMetadata('key', this.state.addKey.key, this.state.addKey.value)();
        return false;
      };

      return [
        this.renderDivider('new key'),
        <tr className={MetadataStyles.newKeyRow} key="new key row">
          <td style={{textAlign: 'right', width: 80}}>
            Key:
          </td>
          <td colSpan={2}>
            <Input
              ref={refKeyInput}
              onKeyDown={(e) => {
                if (e.key && e.key === 'Escape') {
                  this.discardChanges();
                }
              }}
              value={this.state.addKey.key}
              onChange={onChange('key')}
              size="small" />
          </td>
        </tr>,
        <tr className={MetadataStyles.newKeyRow} key="new value row">
          <td style={{textAlign: 'right', width: 80}}>
            Value:
          </td>
          <td colSpan={2}>
            <Input
              onPressEnter={onEnter}
              onKeyDown={(e) => {
                if (e.key && e.key === 'Escape') {
                  this.discardChanges();
                }
              }}
              value={this.state.addKey.value}
              onChange={onChange('value')}
              size="small"
              type="textarea"
              autosize
            />
          </td>
        </tr>,
        <tr className={MetadataStyles.newKeyRow} key="new key title row">
          <td colSpan={3} style={{textAlign: 'right'}}>
            <Button
              id="add-metadata-item-button"
              size="small"
              type="primary"
              onClick={this.saveMetadata('key', this.state.addKey.key, this.state.addKey.value)}>
              <Icon type="check" /> Add
            </Button>
            <Button
              id="cancel-add-metadata-item-button"
              size="small"
              onClick={addKeyCancelClicked}>
              <Icon type="close" /> Cancel
            </Button>
          </td>
        </tr>
      ];
    } else {
      return undefined;
    }
  };

  renderTableHeader = () => {
    const addKeyClicked = () => {
      this.setState({
        editableKeyIndex: null,
        editableValueIndex: null,
        editableText: null,
        addKey: {
          key: '',
          value: ''
        }
      });
    };
    const renderActions = () => {
      const actions = [];
      if (this.state.undoItems.length > 0) {
        actions.push(
          <Button
            id="cancel-key-button"
            key="cancel button"
            size="small"
            onClick={this.onCancelChanges}
          >
            <Icon type="reload" /> Undo
          </Button>
        );
      }
      if (this.props.currentItem && !this.state.addKey) {
        actions.push(
          <Button
            id="add-key-button"
            key="add button"
            size="small"
            onClick={addKeyClicked}
          >
            <Icon type="plus" /> Add
          </Button>
        );
      }
      if (this.props.currentItem) {
        actions.push(
          <Button
            id="remove-all-keys-button"
            key="remove all keys button"
            size="small"
            type="danger"
            onClick={this.confirmDeleteMetadata}
          >
            <Icon type="delete" /> Remove all
          </Button>
        );
      }
      return actions;
    };

    if (!this.props.readOnly) {
      return (
        <thead className={MetadataStyles.metadataHeader}>
          <tr style={{}}>
            <td colSpan={6} style={{padding: 5, borderTop: '1px solid #ccc'}}>
              <Row type="flex" justify="space-between" align="middle">
                <div />
                <div>
                  {renderActions()}
                </div>
              </Row>
            </td>
          </tr>
        </thead>
      );
    }
    return undefined;
  }

  renderDivider = (key, span) => {
    return (
      <tr key={key} className={MetadataStyles.divider}>
        <td colSpan={span || 3}><div /></td>
      </tr>
    );
  };

  renderMetadataItem = (metadataItem) => {
    let valueElement = [];
    for (let key in metadataItem) {
      if (key !== 'rowKey') {
        let value = metadataItem[key].value;
        if (metadataItem[key].type.startsWith('Array')) {
          try {
            value = JSON.parse(value).map(v => <div key={`${key}_value_${v}`}>{v}</div>);
          } catch (___) {}
        }
        const inputOptions = (field, key, value) => {
          return {
            size: 'small',
            value: this.state.editableText,
            ref: this.autoFocusInputRef,
            onBlur: this.saveMetadata(field, key, value),
            onPressEnter: this.saveMetadata(field, key, value),
            onChange: this.onMetadataChange,
            onKeyDown: (e) => {
              if (e.key && e.key === 'Escape') {
                this.discardChanges();
              }
            }
          };
        };
        valueElement.push(this.renderDivider(`${key}_divider`, 6));
        if (this.state.editableKey === key && (this.props.readOnlyKeys || []).indexOf(key) === -1) {
          valueElement.push((
            <tr key={`${key}_key`} className={MetadataStyles.keyRowEdit}>
              <td colSpan={6}>
                <Input {...inputOptions('key', key, metadataItem[key].value)} />
              </td>
            </tr>
          ));
        } else {
          valueElement.push((
            <tr
              key={`${key}_key`}
              className={
                this.props.readOnly || (this.props.readOnlyKeys || []).indexOf(key) >= 0
                  ? MetadataStyles.readOnlyKeyRow
                  : MetadataStyles.keyRow
              }
            >
              <td
                id={`key-column-${key}`}
                colSpan={
                  this.props.readOnly || (this.props.readOnlyKeys || []).indexOf(key) >= 0
                    ? 6
                    : 5
                }
                className={MetadataStyles.key}
                style={{textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap'}}
                onClick={this.onMetadataEditStarted('key', key, value)}>
                {this.props.columnNamesFn(key)}
              </td>
              {
                this.props.readOnly || (this.props.readOnlyKeys || []).indexOf(key) >= 0
                  ? undefined
                  : (
                    <td style={{minWidth: 30, textAlign: 'right'}}>
                      <Button
                        id={`delete-metadata-key-${key}-button`}
                        type="danger"
                        size="small"
                        onClick={() => this.confirmDeleteKey(key)}>
                        <Icon type="delete" />
                      </Button>
                    </td>
                  )
              }
            </tr>
          ));
        }
        if (
          this.state.editableValue === key &&
          (this.props.readOnlyKeys || []).indexOf(key) === -1
        ) {
          valueElement.push((
            <tr key={`${key}_value`} className={MetadataStyles.valueRowEdit}>
              <td colSpan={6}>
                <Input
                  {...inputOptions('value', key, metadataItem[key].value)}
                  type="textarea"
                  autosize
                />
              </td>
            </tr>
          ));
        } else {
          valueElement.push((
            <tr
              key={`${key}_value`}
              className={
                this.props.readOnly ||
                (this.props.readOnlyKeys || []).indexOf(key) >= 0 ||
                metadataItem[key].type.startsWith('Array')
                  ? MetadataStyles.readOnlyValueRow
                  : MetadataStyles.valueRow
              }
            >
              <td
                id={`value-column-${key}`}
                colSpan={6}
                onClick={this.onMetadataEditStarted('value', key, metadataItem[key].value)}>
                {value}
              </td>
            </tr>
          ));
        }
      }
    }
    return valueElement;
  }

  renderEmptyPlaceholder = () => {
    if (!this.props.currentItem) {
      return (
        <tr style={{height: 40, color: '#777'}}>
          <td colSpan={3} style={{textAlign: 'center'}}>
            No item selected
          </td>
        </tr>
      );
    }
    return (
      <tr style={{height: 40, color: '#777'}}>
        <td colSpan={3} style={{textAlign: 'center'}}>
          No attributes set
        </td>
      </tr>
    );
  };

  render () {
    return (
      <div style={{flex: 1, display: 'flex', flexDirection: 'column'}}>
        <table key="header" style={{width: '100%'}}>
          {this.renderTableHeader()}
          <tbody>
            {this.renderAddKeyRow()}
          </tbody>
        </table>
        <div key="body" style={{width: '100%', flex: 1, overflowY: 'auto'}}>
          <table style={{width: '100%', tableLayout: 'fixed'}}>
            <tbody>
              {
                this.props.currentItem
                  ? this.renderMetadataItem(this.props.currentItem)
                  : this.renderEmptyPlaceholder()
              }
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.entityId !== this.props.entityId) {
      this.setState({
        undoItems: [],
        editableValue: null,
        editableKey: null
      });
    }
  };
}
