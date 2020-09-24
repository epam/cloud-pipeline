/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Icon, Input, Modal} from 'antd';
import styles from './SystemDictionaryForm.css';

function arraysAreEqual (array1, array2) {
  if (!array1 && !array2) {
    return true;
  }
  if (!array1 || !array2) {
    return false;
  }
  const set1 = new Set(array1);
  const set2 = new Set(array2);
  if (set1.size !== set2.size) {
    return false;
  }
  for (let el of set1) {
    if (!set2.has(el)) {
      return false;
    }
  }
  return true;
}

class SystemDictionaryForm extends React.Component {
  state = {
    name: undefined,
    initialName: undefined,
    items: [],
    initialItems: [],
    errors: {
      name: undefined,
      items: []
    }
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.name !== this.props.name || !arraysAreEqual(prevProps.items, this.props.items)) {
      this.updateState();
    }
  }

  get valid () {
    const {errors} = this.state;
    const {name: errorName, items: errorItems} = errors;
    return !errorName && !errorItems.find(Boolean);
  }

  get modified () {
    const {
      name,
      initialName,
      items,
      initialItems
    } = this.state;
    return name !== initialName || !arraysAreEqual(items, initialItems);
  }

  updateState = () => {
    const {name, items} = this.props;
    this.setState({
      name,
      initialName: name,
      items: (items || []).slice(),
      initialItems: (items || []).slice()
    }, this.afterChange);
  };

  validate = () => {
    const {
      name,
      items
    } = this.state;
    const errors = {
      name: undefined,
      items: items.map(item => undefined)
    };
    if (!name) {
      errors.name = 'Dictionary name is required';
    }
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      for (let j = i + 1; j < items.length; j++) {
        const test = items[j];
        if ((item || '').trim() === (test || '').trim()) {
          errors.items[i] = 'Duplicates are not allowed';
          errors.items[j] = 'Duplicates are not allowed';
        }
      }
      if (!item) {
        errors.items[i] = 'Value is required';
      }
    }
    return errors;
  };

  doValidation = (cb) => {
    this.setState({errors: this.validate()}, cb);
  };

  onChange = () => {
    const {onChange} = this.props;
    if (onChange) {
      const {name, items} = this.state;
      onChange(name, items, this.modified, this.valid);
    }
  };

  onSave = () => {
    const {onSave, isNew} = this.props;
    if (onSave && this.valid && this.modified) {
      const {name, initialName, items} = this.state;
      onSave(name, items, !isNew && initialName !== name ? initialName : undefined);
    }
  };

  afterChange = () => {
    this.doValidation(this.onChange);
  }

  onNameChanged = (e) => {
    this.setState({
      name: e.target.value
    }, this.afterChange);
  };

  onItemChanged = (index) => (e) => {
    const {items} = this.state;
    items[index] = e.target.value;
    this.setState({
      items: items.slice()
    }, this.afterChange);
  };

  onItemRemove = (index) => () => {
    const {items} = this.state;
    items.splice(index, 1);
    this.setState({
      items: items.slice()
    }, this.afterChange);
  };

  onItemAdd = () => {
    const {items} = this.state;
    items.push('');
    this.setState({
      items: items.slice()
    }, this.afterChange);
  };

  onDelete = () => {
    const {name, onDelete, isNew} = this.props;
    if (onDelete) {
      if (isNew) {
        onDelete();
      } else {
        Modal.confirm({
          title: `Are you sure you want to delete "${name}" dictionary?`,
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            onDelete(name);
          }
        });
      }
    }
  };

  render () {
    const {disabled, isNew} = this.props;
    const {
      name,
      items,
      errors
    } = this.state;
    const {name: nameError, items: itemsError} = errors;
    return (
      <div className={styles.container}>
        <div className={styles.row}>
          <span className={styles.label}>Name:</span>
          <Input
            disabled={disabled}
            style={{flex: 1}}
            value={name}
            onChange={this.onNameChanged}
          />
        </div>
        {
          nameError && (
            <div className={styles.error}>
              {nameError}
            </div>
          )
        }
        <div className={styles.row} style={{marginTop: 10}}>
          <span className={styles.label}>
            Items:
          </span>
        </div>
        <div className={styles.items}>
          {
            items.map((item, index) => (
              <div key={index}>
                <div className={styles.row}>
                  <Input
                    disabled={disabled}
                    style={{flex: 1, marginRight: 5}}
                    value={item}
                    onChange={this.onItemChanged(index)}
                  />
                  <Button
                    size="small"
                    type="danger"
                    onClick={this.onItemRemove(index)}
                  >
                    <Icon
                      type="delete"
                    />
                  </Button>
                </div>
                {
                  itemsError[index] && (
                    <div className={styles.error}>
                      {itemsError[index]}
                    </div>
                  )
                }
              </div>
            ))
          }
        </div>
        <div>
          <Button
            disabled={disabled}
            onClick={this.onItemAdd}
          >
            <Icon type="plus" />
            <span>Add value</span>
          </Button>
        </div>
        <div className={styles.actions}>
          <Button
            className={styles.action}
            disabled={disabled}
            onClick={this.onDelete}
            type={isNew ? 'default' : 'danger'}
          >
            {isNew ? 'Cancel' : 'Delete'}
          </Button>
          <div>
            {
              !isNew && (
                <Button
                  className={styles.action}
                  disabled={disabled || !this.modified}
                  onClick={this.updateState}
                >
                  Revert
                </Button>
              )
            }
            <Button
              className={styles.action}
              type="primary"
              disabled={disabled || !this.modified || !this.valid}
              onClick={this.onSave}
            >
              Save
            </Button>
          </div>
        </div>
      </div>
    );
  }
}

SystemDictionaryForm.propTypes = {
  disabled: PropTypes.bool,
  items: PropTypes.array,
  name: PropTypes.string,
  isNew: PropTypes.bool,
  onChange: PropTypes.func,
  onSave: PropTypes.func,
  onDelete: PropTypes.func
};

export default SystemDictionaryForm;
