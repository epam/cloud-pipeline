/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {
  Button,
  Icon
} from 'antd';
import compareArrays from '../../../../utils/compareArrays';
import InputWrapper from './input-wrapper';
import styles from './sample-sheet-edit-form.css';

function getDataItemError (data) {
  // data:
  // {key, value}
  // or
  // [itemA, itemB, itemC, ...]
  return undefined;
}

class EditSection extends React.Component {
  state = {
    valid: true,
    title: {
      value: undefined,
      error: undefined
    },
    data: []
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.title !== this.props.title ||
      prevProps.data !== this.props.data ||
      !compareArrays(prevProps.otherSectionNames, this.props.otherSectionNames)
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      title,
      data = []
    } = this.props;
    this.setState({
      title: {value: title, error: undefined},
      data: data.map(item => ({value: item, error: undefined})),
      valid: true
    }, this.validate);
  };

  validate = () => {
    return new Promise((resolve) => {
      const {
        title,
        data = []
      } = this.state;
      const {
        value: name
      } = title;
      const {
        readOnly,
        titleReadOnly,
        otherSectionNames = []
      } = this.props;
      let titleError;
      if (!name || name.trim().length === 0) {
        titleError = 'Section name is required';
      } else if (!readOnly && !titleReadOnly) {
        titleError = otherSectionNames
          .filter(o => o && o.length)
          .map(o => new RegExp(`^[\\s]*${o}[\\s]*$`, 'i'))
          .find(o => o.test(name)) ? 'Duplicate section name' : undefined;
      }
      const newData = data.map(item => ({
        value: item.value,
        error: getDataItemError(item.value)
      }));
      const valid = !titleError && !newData.find(o => o.error);
      this.setState({
        title: {
          value: name,
          error: titleError
        },
        data: newData
      }, () => resolve(valid));
    });
  };

  validateAndSubmit = () => {
    this.validate()
      .then(valid => {
        const {
          onChange
        } = this.props;
        const {
          data,
          title
        } = this.state;
        const {value: name} = title;
        const sectionData = data.map(item => item.value);
        if (onChange) {
          onChange({name, data: sectionData});
        }
      });
  };

  onChangeTitle = (e) => {
    this.setState({
      title: {value: e.target.value}
    }, this.validateAndSubmit);
  };

  renderSectionItem = (item, index) => {
    const {value: data, error} = item;
    const {readOnly, editable} = this.props;
    let {key, value} = data;
    if (data && Array.isArray(data)) {
      [key, value] = data;
    }
    if (!editable && !key && !value) {
      return null;
    }
    const onChangeKeyValue = (field) => (e) => {
      const {data: oldData} = this.state;
      const newData = oldData.slice();
      newData.splice(index, 1, {value: {key, value, [field]: e.target.value}});
      this.setState({
        data: newData
      }, this.validateAndSubmit);
    };
    const onRemove = () => {
      const {data: oldData} = this.state;
      const newData = oldData.slice();
      newData.splice(index, 1);
      this.setState({
        data: newData
      }, this.validateAndSubmit);
    };
    return (
      <div
        className={styles.dataItem}
        key={`section-item-${index}`}
      >
        <InputWrapper
          className={
            classNames(
              styles.key,
              {
                'cp-error': error
              }
            )
          }
          disabled={readOnly}
          value={key}
          onChange={onChangeKeyValue('key')}
          editable={editable}
          isKey
        />
        <InputWrapper
          className={
            classNames(
              styles.value,
              {
                'cp-error': error
              }
            )
          }
          disabled={readOnly}
          value={value}
          onChange={onChangeKeyValue('value')}
          editable={editable}
        />
        {
          editable && (
            <Button
              disabled={readOnly}
              onClick={onRemove}
              type="danger"
            >
              <Icon type="delete" />
            </Button>
          )
        }
      </div>
    );
  };

  onAddSectionItem = () => {
    const {data = []} = this.state;
    const newData = [...data, {value: {key: '', value: ''}}];
    this.setState({
      data: newData
    }, this.validateAndSubmit);
  };

  render () {
    const {
      className,
      style,
      titleReadOnly,
      readOnly,
      removable,
      onRemove,
      editable
    } = this.props;
    const {
      title,
      data = []
    } = this.state;
    return (
      <div
        className={
          classNames(
            className,
            styles.section,
            'cp-divider',
            'bottom'
          )
        }
        style={style}
      >
        <div className={styles.title}>
          <InputWrapper
            className={
              classNames(
                styles.input,
                {
                  'cp-error': title.error
                }
              )
            }
            value={title.value}
            disabled={titleReadOnly || readOnly}
            onChange={this.onChangeTitle}
            editable={editable}
          />
          {
            removable && editable && (
              <Button
                disabled={readOnly}
                onClick={onRemove}
                type="danger"
              >
                <Icon type="delete" />
              </Button>
            )
          }
        </div>
        {
          data.map(this.renderSectionItem)
        }
        {
          editable && (
            <div
              className={
                classNames(
                  styles.dataItem,
                  styles.actions
                )
              }
            >
              <Button
                disabled={readOnly}
                onClick={this.onAddSectionItem}
                className={styles.addSectionItem}
              >
                <Icon type="plus" /> Add row
              </Button>
            </div>
          )
        }
      </div>
    );
  }
}

const KeyValuePropType = PropTypes.shape({
  key: PropTypes.string,
  value: PropTypes.string
});

EditSection.propTypes = {
  className: PropTypes.string,
  readOnly: PropTypes.bool,
  style: PropTypes.object,
  title: PropTypes.string,
  titleReadOnly: PropTypes.bool,
  data: PropTypes.arrayOf(
    PropTypes.oneOfType([
      KeyValuePropType,
      PropTypes.arrayOf(PropTypes.string)
    ])
  ),
  removable: PropTypes.bool,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  otherSectionNames: PropTypes.arrayOf(PropTypes.string),
  editable: PropTypes.bool
};

export default EditSection;
