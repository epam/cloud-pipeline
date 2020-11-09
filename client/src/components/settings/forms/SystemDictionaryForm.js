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
import {
  Button,
  Icon,
  Input,
  Modal
} from 'antd';
import SystemDictionaryLinksForm from './SystemDictionaryLinksForm';
import styles from './SystemDictionaryForm.css';

function linksAreEqual (linksA, linksB) {
  if (!linksA && !linksB) {
    return true;
  }
  if (!linksA || !linksB) {
    return false;
  }
  if (linksA.length !== linksB.length) {
    return false;
  }
  for (let i = 0; i < linksA.length; i++) {
    const linkA = linksA[i];
    const linkB = linksB.find(el => el.key === linkA.key && el.value === linkA.value);
    if (!linkB) {
      return false;
    }
  }
  return true;
}

function dictionariesAreEqual (dictionaryA, dictionaryB) {
  if (!dictionaryA && !dictionaryB) {
    return true;
  }
  if (!dictionaryA || !dictionaryB) {
    return false;
  }
  if (dictionaryA.length !== dictionaryB.length) {
    return false;
  }
  for (let i = 0; i < dictionaryA.length; i++) {
    const item = dictionaryA[i];
    const sameItem = dictionaryB.find(el => el.value === item.value);
    if (!sameItem) {
      return false;
    }
    const {links: linksA = []} = item;
    const {links: linksB = []} = sameItem;
    if (!linksAreEqual(linksA, linksB)) {
      return false;
    }
  }
  return true;
}

function mapValue (filter) {
  return function map (value) {
    const {
      autofill = true,
      value: linkValue,
      links = []
    } = value || {};
    return {
      autofill,
      value: linkValue,
      links: links.map(link => ({key: link.key, value: link.value})),
      filtered: !filter || (linkValue || '').toLowerCase().indexOf(filter.toLowerCase()) >= 0
    };
  };
}

class SystemDictionaryForm extends React.Component {
  state = {
    name: undefined,
    initialName: undefined,
    items: [],
    initialItems: [],
    errors: {
      name: undefined,
      itemsValidation: undefined,
      items: []
    },
    linksFormVisible: false,
    editableLinksIndex: undefined
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.name !== this.props.name ||
      !dictionariesAreEqual(prevProps.items, this.props.items)
    ) {
      this.updateState();
    } else if (this.props.filter !== prevProps.filter) {
      this.updateFilters();
    }
  }

  get valid () {
    const {errors} = this.state;
    const {name: errorName, items: errorItems, itemsValidation: itemsValidationError} = errors;
    return !errorName && !errorItems.find(Boolean) && !itemsValidationError;
  }

  get modified () {
    const {
      name,
      initialName,
      items,
      initialItems
    } = this.state;
    return name !== initialName || !dictionariesAreEqual(items, initialItems);
  }

  get dictionaries () {
    const {dictionaries, name, isNew} = this.props;
    return (dictionaries || []).filter(d => isNew || d.key !== name);
  }

  updateState = () => {
    const {name, items} = this.props;
    this.setState({
      name,
      initialName: name,
      items: (items || []).map(mapValue(this.props.filter)),
      initialItems: (items || []).map(mapValue(this.props.filter))
    }, this.afterChange);
  };

  updateFilters = () => {
    const {items} = this.state;
    this.setState({
      items: (items || []).map(mapValue(this.props.filter))
    });
  };

  validate = () => {
    const {
      name,
      items
    } = this.state;
    const errors = {
      name: undefined,
      items: items.map(item => undefined),
      itemsValidation: undefined
    };
    if (!name) {
      errors.name = 'Dictionary name is required';
    } else if (this.dictionaries.find(d => d.key === name)) {
      errors.name = `"${name}" dictionary already exists`;
    }
    if (!items || items.length === 0) {
      errors.itemsValidation = 'Dictionary must contain at least one item';
    }
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      for (let j = i + 1; j < items.length; j++) {
        const test = items[j];
        if ((item.value || '').trim() === (test.value || '').trim()) {
          errors.items[i] = 'Duplicates are not allowed';
          errors.items[j] = 'Duplicates are not allowed';
        }
      }
      if (!item.value) {
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
      const itemsProcessed = (items || [])
        .map((item) => {
          const {filtered, ...rest} = item;
          return rest;
        });
      onSave(name, itemsProcessed, !isNew && initialName !== name ? initialName : undefined);
    }
  };

  openLinksForm = (index) => {
    this.setState({linksFormVisible: true, editableLinksIndex: index});
  };

  closeLinksForm = () => {
    this.setState({linksFormVisible: false, editableLinksIndex: undefined});
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
    items[index].value = e.target.value;
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
    items.push({value: '', autofill: true, links: [], filtered: true});
    this.setState({
      items: items.slice()
    }, this.afterChange);
  };

  onChangeLinks = (links) => {
    const {items, editableLinksIndex} = this.state;
    items[editableLinksIndex || 0].links = (links || [])
      .map(link => ({key: link.key, value: link.value}));
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
    const {linksFormVisible, editableLinksIndex} = this.state;
    const {
      name,
      items,
      errors
    } = this.state;
    const {name: nameError, items: itemsError, itemsValidation: itemsValidationError} = errors;
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
        {
          itemsValidationError && (
            <div className={styles.error}>
              {itemsValidationError}
            </div>
          )
        }
        <div className={styles.items}>
          {
            items.map((item, index) => (
              <div
                key={index}
                className={`${styles.item} ${item.filtered ? '' : styles.hidden}`}
              >
                <div className={styles.row}>
                  <Input
                    className={`${styles.input} ${itemsError[index] ? styles.error : ''}`}
                    disabled={disabled}
                    style={{flex: 1, marginRight: 5}}
                    value={item.value}
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
                <div className={styles.link}>
                  {
                    (item.links || []).length === 0 && (
                      <span
                        className={styles.add}
                        onClick={() => this.openLinksForm(index)}
                      >
                        <Icon type="plus" />
                        Add linked dictionary item
                      </span>
                    )
                  }
                  {
                    (item.links || []).length > 0 && (
                      <div
                        className={styles.add}
                        onClick={() => this.openLinksForm(index)}
                      >
                        {
                          (item.links || []).map((link, index) => (
                            <div
                              key={`${link.key}-${link.value}-${index}`}
                              style={{
                                margin: '2px 0'
                              }}
                            >
                              <Icon type="link" />
                              {link.key}
                              <Icon type="arrow-right" />
                              {link.value}
                            </div>
                          ))
                        }
                      </div>
                    )
                  }
                </div>
              </div>
            ))
          }
        </div>
        <div style={{padding: 2}}>
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
        <SystemDictionaryLinksForm
          availableDictionaries={this.dictionaries}
          visible={linksFormVisible}
          onClose={this.closeLinksForm}
          value={
            linksFormVisible
              ? (items[editableLinksIndex || 0].links || [])
              : []
          }
          onChange={this.onChangeLinks}
        />
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
  onDelete: PropTypes.func,
  dictionaries: PropTypes.array,
  filter: PropTypes.string
};

export default SystemDictionaryForm;
