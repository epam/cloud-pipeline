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
import PropTypes from 'prop-types';
import {
  Select,
  Icon,
  Input
} from 'antd';
import styles from './AutoCompleteForParameter.css';

export default class AutoCompleteForParameter extends React.Component {

  static propTypes = {
    readOnly: PropTypes.bool,
    hideAutoComplete: PropTypes.bool,
    placeholder: PropTypes.string,
    parameterKey: PropTypes.string,
    value: PropTypes.string,
    onChange: PropTypes.func,
    currentMetadataEntity: PropTypes.array,
    currentProjectMetadata: PropTypes.object,
    rootEntityId: PropTypes.string,
    showWithButton: PropTypes.bool,
    buttonIcon: PropTypes.string,
    onButtonClick: PropTypes.func
  };

  state = {
    filteredEntityFields: [],
    value: undefined
  };

  getType = (name, matadataEntity) => {
    const [currentField] = matadataEntity.fields.filter(field => field.name === name);
    return currentField ? currentField.type : null;
  };

  handleSearch = (value) => {
    if (this.props.hideAutoComplete ||
      !value ||
      (value.indexOf('this.') !== 0 && value.indexOf('project.') !== 0)
    ) {
      this.setState({
        value: value,
        filteredEntityFields: []
      }, () => {
        this.handleOnChange();
      });
    } else {
      let filteredEntityFields = this.state.filteredEntityFields;
      const parseValue = value.split('.');

      if (value.indexOf('this.') === 0) {
        let [currentRootEntity] =
          this.props.currentMetadataEntity.filter(matadataEntity =>
            `${matadataEntity.metadataClass.id}` === this.props.rootEntityId
          );

        for (let i = 1; i < parseValue.length - 1; i++) {
          const type = this.getType(parseValue[i], currentRootEntity);
          [currentRootEntity] =
            this.props.currentMetadataEntity.filter(matadataEntity =>
              `${matadataEntity.metadataClass.name}` === type
            );
          if (!type) return;
        }

        filteredEntityFields =
          (currentRootEntity ? currentRootEntity.fields : [])
            .filter(field =>
              field.name.toLowerCase()
                .indexOf(parseValue[parseValue.length - 1].toLowerCase()) >= 0);
      } else {
        if (parseValue.length === 2) {
          filteredEntityFields =
            (this.props.currentProjectMetadata
              ? Object.keys(this.props.currentProjectMetadata) : [])
              .filter(metadata =>
                metadata.toLowerCase()
                  .indexOf(parseValue[parseValue.length - 1].toLowerCase()) >= 0)
              .map(metadata => {
                return {
                  name: metadata
                };
              });
        } else {
          filteredEntityFields = [];
        }
      }
      this.setState({
        value: value,
        filteredEntityFields: filteredEntityFields
      }, () => {
        this.handleOnChange();
      });
    }
  };

  onFocus = () => {
    this.handleSearch(this.state.value);
  };

  handleOnChange = () => {
    if (!this.props.onChange) {
      return;
    }
    this.props.onChange(this.state.value);
  };

  render () {
    return (
      <Input.Group compact style={{display: 'flex'}}>
        {
          !this.props.showWithButton &&
          <span
            className={styles.pathParameterAutoComplete}
            onClick={() =>
              !(this.props.readOnly) && this.props.onButtonClick &&
              this.props.onButtonClick(this.props.parameterKey, this.state.value)}
          >
            <div style={{padding: '5px', cursor: 'pointer'}}>
              <Icon type={this.props.buttonIcon} />
            </div>
          </span>
        }
        <Select
          disabled={this.props.readOnly}
          style={{width: '100%'}}
          size="large"
          placeholder="Value"
          mode="combobox"
          value={this.state.value}
          filterOption={false}
          onChange={this.handleSearch}
          onFocus={this.onFocus}
        >
          {
            this.state.filteredEntityFields.map(field => {
              let currentValue = field.name;
              if (this.state.value) {
                const parseValue = this.state.value.split('.');
                parseValue.pop();
                currentValue = parseValue.join('.') + '.' + field.name;
              }
              return (
                <Select.Option
                  key={field.name}
                  value={currentValue}>
                  {field.name}
                </Select.Option>
              );
            })
          }
        </Select>
      </Input.Group>
    );
  }

  componentDidMount () {
    this.setState({value: this.props.value});
  }

  componentWillReceiveProps (nextProps) {
    if ('value' in nextProps) {
      const value = nextProps.value;
      this.setState({value});
    }
  }
}
