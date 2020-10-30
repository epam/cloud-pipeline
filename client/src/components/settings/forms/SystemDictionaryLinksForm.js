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
  Modal,
  Icon,
  Select
} from 'antd';

class SystemDictionaryLinksForm extends React.Component {
  state = {
    addDictionary: undefined,
    addDictionaryValue: undefined
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.clear();
    }
  }

  clear = () => {
    this.setState({
      addDictionary: undefined,
      addDictionaryValue: undefined
    });
  };

  onAddLink = () => {
    const {onChange, value} = this.props;
    const {addDictionary: key, addDictionaryValue: dictValue} = this.state;
    const links = (value || []).concat({key, value: dictValue, autofill: true});
    onChange && onChange(links);
    this.clear();
  };

  renderAddLinkForm = () => {
    const {availableDictionaries, value} = this.props;
    const linkedDictionaries = new Set((value || []).map(link => link.key));
    const dictionaries = (availableDictionaries || [])
      .filter(dictionary => !linkedDictionaries.has(dictionary.key));
    const onChangeDictionary = (e) => {
      this.setState({
        addDictionary: e,
        addDictionaryValue: undefined
      });
    };
    const onChangeDictionaryValue = (e) => {
      this.setState({
        addDictionaryValue: e
      });
    };
    const {
      addDictionary,
      addDictionaryValue
    } = this.state;
    const selectedDictionary = dictionaries.find(d => d.key === addDictionary);
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center'
        }}
      >
        <span
          style={{
            margin: '0 5px'
          }}
        >
          Add new link:
        </span>
        <Select
          allowClear
          showSearch
          style={{
            flex: 1,
            margin: '0 5px',
            overflow: 'auto'
          }}
          value={addDictionary}
          onChange={onChangeDictionary}
          filterOption={
            (input, option) =>
              option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }
          placeholder="Select dictionary"
        >
          {
            dictionaries.map((dictionary) => (
              <Select.Option key={dictionary.key} value={dictionary.key}>
                {dictionary.key}
              </Select.Option>
            ))
          }
        </Select>
        <Select
          allowClear
          showSearch
          disabled={!selectedDictionary}
          style={{
            flex: 1,
            margin: '0 5px',
            overflow: 'auto'
          }}
          value={addDictionaryValue}
          onChange={onChangeDictionaryValue}
          filterOption={
            (input, option) =>
              option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }
          placeholder="Select dictionary value"
        >
          {
            (selectedDictionary?.values || []).map((item) => (
              <Select.Option key={item.value} value={item.value}>
                {item.value}
              </Select.Option>
            ))
          }
        </Select>
        <Button
          size="small"
          type="primary"
          style={{
            margin: '0 5px'
          }}
          disabled={!addDictionaryValue || !addDictionary}
          onClick={this.onAddLink}
        >
          Add
        </Button>
        <Button
          size="small"
          style={{
            margin: '0 5px'
          }}
          onClick={this.clear}
          disabled={!addDictionaryValue && !addDictionary}
        >
          Cancel
        </Button>
      </div>
    );
  };

  renderLink = (link, index) => {
    const {availableDictionaries, onChange, value} = this.props;
    const onChangeLink = (e) => {
      const links = (value || []).slice();
      const existingLinkIndex = links.findIndex(l => l.key === link.key);
      links.splice(existingLinkIndex, 1, ({...link, value: e}));
      onChange && onChange(links);
    };
    const onRemoveLink = () => {
      const links = (value || []).slice();
      const existingLinkIndex = links.findIndex(l => l.key === link.key);
      links.splice(existingLinkIndex, 1);
      onChange && onChange(links);
    };
    const selectedDictionary = availableDictionaries.find(d => d.key === link.key);
    if (!selectedDictionary) {
      return null;
    }
    return (
      <div
        key={`${link.key}-${link.value}-${index}`}
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          margin: '5px 0'
        }}
      >
        <span
          style={{
            margin: '0 5px',
            width: 125,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            textAlign: 'right'
          }}
        >
          {selectedDictionary.key}
        </span>
        <Icon type="caret-right" />
        <Select
          showSearch
          disabled={!selectedDictionary}
          style={{
            flex: 1,
            margin: '0 5px',
            overflow: 'auto'
          }}
          value={link.value}
          onChange={onChangeLink}
          filterOption={
            (input, option) =>
              option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }
          placeholder="Select dictionary value"
        >
          {
            (selectedDictionary?.values || []).map((item) => (
              <Select.Option key={item.value} value={item.value}>
                {item.value}
              </Select.Option>
            ))
          }
        </Select>
        <Button
          size="small"
          type="danger"
          style={{
            margin: '0 5px'
          }}
          onClick={onRemoveLink}
        >
          Remove
        </Button>
      </div>
    );
  };

  render () {
    const {visible, onClose, value} = this.props;
    const links = (value || []);
    links.sort((a, b) => {
      if (a.key > b.key) {
        return 1;
      }
      if (a.key < b.key) {
        return -1;
      }
      return 0;
    });
    return (
      <Modal
        visible={visible}
        title="Linked dictionaries"
        onCancel={onClose}
        footer={null}
        width="50%"
      >
        {
          links.map(this.renderLink)
        }
        {
          links.length > 0 && (
            <div
              style={{
                height: 1,
                width: '100%',
                borderBottom: '1px solid #ddd',
                margin: '5px 0'
              }}
            >
              {'\u00A0'}
            </div>
          )
        }
        {
          this.renderAddLinkForm()
        }
      </Modal>
    );
  }
}

SystemDictionaryLinksForm.propTypes = {
  availableDictionaries: PropTypes.array,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  onChange: PropTypes.func,
  value: PropTypes.array
};

export default SystemDictionaryLinksForm;
