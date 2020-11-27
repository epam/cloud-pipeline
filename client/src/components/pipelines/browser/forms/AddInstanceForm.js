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
import {observer} from 'mobx-react';
import {
  Checkbox,
  Modal,
  Button,
  Form,
  Row,
  Input,
  Select,
  Menu,
  Icon,
  Dropdown
} from 'antd';
import SelectMetadataItems from './SelectMetadataItems';
import compareArrays from '../../../../utils/compareArrays';
import styles from './AddInstanceForm.css';

@Form.create()
@observer
export default class AddInstanceForm extends React.Component {
  static propTypes = {
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    onCancel: PropTypes.func,
    onCreate: PropTypes.func,
    entityTypes: PropTypes.array,
    entityType: PropTypes.number,
    folderId: PropTypes.oneOfType([
      PropTypes.number,
      PropTypes.string
    ])
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  state = {
    fields: [],
    customFields: [],
    selectMetadataItems: undefined
  };

  handleSubmit = (e) => {
    e.preventDefault();
    const valid = this.validate();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (valid && !err && this.state.customFields.filter(f => !!f.validation).length === 0) {
        const mapType = (field) => {
          if (field.multiValue) {
            return `Array[${field.type}]`;
          } else if (field.reference) {
            return `${field.type}:ID`;
          }
          return field.type;
        };
        const mapValue = (field) => {
          if (field.multiValue) {
            return `[${(field.value || []).map(v => `"${v}"`).join(', ')}]`;
          }
          return field.value;
        };
        const data = this.state.fields.filter(f => !!f.value).map(f => {
          return {
            name: f.name,
            type: mapType(f),
            value: mapValue(f)
          };
        }).reduce((dataObj, field) => {
          dataObj[field.name] = {
            type: field.type,
            value: field.value
          };
          return dataObj;
        }, {});
        this.state.customFields.filter(f => !!f.value).map(f => {
          return {
            name: f.name,
            type: mapType(f),
            value: mapValue(f)
          };
        }).reduce((dataObj, field) => {
          dataObj[field.name] = {
            type: field.type,
            value: field.value
          };
          return dataObj;
        }, data);
        values.data = data;
        this.props.onCreate(values);
      }
    });
  };

  validate = () => {
    const customFields = this.state.customFields;
    let result = true;
    customFields.forEach(f => {
      f.validation = this.validateCustomParameterName(f.name, f.identifier);
      if (f.validation) {
        result = false;
      }
    });
    this.setState({customFields});
    return result;
  };

  entityTypes = () => {
    return (this.props.entityTypes || []).map(e => e.metadataClass);
  };

  ownEntityTypes = () => {
    return this.entityTypes().filter(t => !t.outOfProject);
  };

  onSelectEntityType = (id) => {
    this.props.form.setFieldsValue({entityClass: id});
    this.rebuildEntityTypeParameters(id);
  };

  rebuildEntityTypeParameters = (entityTypeId) => {
    const [entityTypeInfo] = (this.props.entityTypes || [])
      .filter(e => `${e.metadataClass.id}` === `${entityTypeId}`);
    let fields = [];
    if (entityTypeInfo) {
      fields = (entityTypeInfo.fields || []).map(f => ({...f, value: null}));
    }
    this.setState({fields, customFields: []});
  };

  validateCustomParameterName = (name, identifier) => {
    if (!name) {
      return 'Parameter name is required';
    }
    const [predefinedField] = this.state.fields
      .filter(f => f.name.toLowerCase() === name.toLowerCase());
    const [customField] = this.state.customFields
      .filter(f => f.identifier !== identifier && f.name.toLowerCase() === name.toLowerCase());
    if (predefinedField || customField) {
      return 'This parameter already exists';
    }
    return null;
  };

  onChangeStringValue = (parameterNameFn, custom = false) => (e) => {
    const fields = custom ? this.state.customFields : this.state.fields;
    const [field] = fields.filter(parameterNameFn);
    if (field) {
      field.value = e.target.value;
      if (custom) {
        this.setState({customFields: fields});
      } else {
        this.setState({fields});
      }
    }
  };

  onChangeParameterName = (parameterNameFn) => (e) => {
    const fields = this.state.customFields;
    const [field] = fields.filter(parameterNameFn);
    if (field) {
      field.name = e.target.value;
      field.validation = this.validateCustomParameterName(field.name, field.identifier);
      this.setState({customFields: fields});
    }
  };

  onChangeReferenceValue = (parameterNameFn, custom = false) => (value) => {
    const fields = custom ? this.state.customFields : this.state.fields;
    const [field] = fields.filter(parameterNameFn);
    if (field) {
      field.value = value;
      if (custom) {
        this.setState({customFields: fields});
      } else {
        this.setState({fields});
      }
    }
  };

  onChangeParameterMultiple = (parameterNameFn) => (e) => {
    const fields = this.state.customFields;
    const [field] = fields.filter(parameterNameFn);
    if (field) {
      if (e.target.checked) {
        field.value = field.value ? [field.value] : [];
      } else {
        field.value = undefined;
      }
      field.multiValue = e.target.checked;
      field.validation = this.validateCustomParameterName(field.name, field.identifier);
      this.setState({customFields: fields});
    }
  };

  onOpenMetadataItemSelection = (custom, parameterNameFn) => (e) => {
    if (e && e.target && typeof e.target.blur === 'function') {
      e.target.blur();
    }
    const fields = custom
      ? (this.state.customFields || [])
      : (this.state.fields || []);
    const field = fields.find(parameterNameFn);
    if (field) {
      this.setState({
        selectMetadataItems: {
          custom,
          type: field.type,
          multiple: field.multiValue,
          filter: parameterNameFn,
          value: field.multiValue ? (field.value || []) : [field.value].filter(Boolean)
        }
      });
    }
  };

  onCloseMetadataItemSelection = () => {
    this.setState({
      selectMetadataItems: undefined
    });
  };

  onSelectMetadataItems = (items) => {
    const {selectMetadataItems} = this.state;
    const {custom, filter, multiple} = selectMetadataItems;
    const fields = custom ? this.state.customFields : this.state.fields;
    const [field] = fields.filter(filter);
    if (field) {
      if (multiple) {
        field.value = items;
      } else {
        field.value = items && items.length === 1 ? items[0] : undefined;
      }
      if (custom) {
        this.setState({customFields: fields});
      } else {
        this.setState({fields});
      }
    }
    this.onCloseMetadataItemSelection();
  };

  onRemoveParameter = (parameterNameFn) => () => {
    const fields = this.state.customFields;
    const [field] = fields.filter(parameterNameFn);
    if (field) {
      const index = fields.indexOf(field);
      if (index >= 0) {
        fields.splice(index, 1);
        this.setState({customFields: fields});
      }
    }
  };

  renderField = (custom) => (field, index) => {
    if (field.reference || field.multiValue) {
      return this.renderReferenceField(field, index, custom);
    } else {
      return this.renderStringField(field, index, custom);
    }
  };

  renderReferenceField = (field, index, custom = false) => {
    const parameterFilterFn = custom
      ? f => f.identifier === field.identifier
      : f => f.name === field.name;
    const trs = [];
    const disabled = this.props.pending;
    trs.push(
      <div
        key={`parameter_${index}`}
        className={styles.parameterRow}
      >
        <div
          className={`${styles.parameterName} ${custom ? styles.custom : ''}`}
        >
          {
            custom
              ? (
                <Input
                  placeholder="Parameter name"
                  className={`${styles.nameInput} ${field.validation ? styles.validationError : ''}`}
                  value={field.name}
                  onChange={this.onChangeParameterName(parameterFilterFn)}
                  style={{width: '100%'}}
                  disabled={disabled}
                />
              )
              : <span>{field.name}: </span>
          }
        </div>
        <Input
          style={{flex: 1, lineHeight: 1, top: 0}}
          disabled={disabled}
          onFocus={this.onOpenMetadataItemSelection(custom, parameterFilterFn)}
          addonBefore={(
            <div
              className={styles.browseItemsButton}
              onClick={this.onOpenMetadataItemSelection(custom, parameterFilterFn)}
            >
              Browse
            </div>
          )}
          value={field.multiValue ? (field.value || []).join(', ') : field.value}
          placeholder={`Browse ${field.type} ${field.multiValue ? 'entities' : 'entity'}`}
        />
        {
          custom && (
            <Checkbox
              disabled={disabled}
              checked={field.multiValue}
              style={{marginLeft: 5}}
              onChange={this.onChangeParameterMultiple(parameterFilterFn)}
            >
              Multiple
            </Checkbox>
          )
        }
        {
          custom && (
            <Button
              disabled={disabled}
              size="small"
              onClick={this.onRemoveParameter(f => f.identifier === field.identifier)}
              type="danger"
              style={{marginLeft: 5}}
            >
              <Icon type="delete" />
            </Button>
          )
        }
      </div>
    );
    if (field.validation) {
      trs.push(
        <div
          key={`parameter_${index}_validation`}
          className={styles.validationError}
          style={{paddingRight: 5}}
        >
          {field.validation}
        </div>
      );
    }
    return trs;
  };

  renderStringField = (field, index, custom = false) => {
    const parameterFilterFn = custom
      ? f => f.identifier === field.identifier
      : f => f.name === field.name;
    const trs = [];
    const disabled = this.props.pending;
    trs.push(
      <div
        key={`parameter_${index}`}
        className={styles.parameterRow}
      >
        <div className={`${styles.parameterName} ${custom ? styles.custom : ''}`}>
          {
            custom
              ? (
                <Input
                  disabled={disabled}
                  placeholder="Parameter name"
                  className={`${styles.nameInput} ${field.validation ? styles.validationError : ''}`}
                  value={field.name}
                  onChange={this.onChangeParameterName(parameterFilterFn)}
                  style={{width: '100%'}}
                />
              )
              : <span>{field.name}: </span>
          }
        </div>
        <Input
          disabled={disabled}
          style={{flex: 1}}
          value={field.value}
          placeholder="Parameter value"
          onChange={this.onChangeStringValue(parameterFilterFn, custom)} />
        {
          custom && (
            <Button
              disabled={disabled}
              size="small"
              onClick={this.onRemoveParameter(f => f.identifier === field.identifier)}
              type="danger"
              style={{marginLeft: 5}}>
              <Icon type="delete" />
            </Button>
          )
        }
      </div>
    );
    if (field.validation) {
      trs.push(
        <div
          key={`parameter_${index}_validation`}
          className={styles.validationError}
          style={{paddingRight: 5}}
        >
          {field.validation}
        </div>
      );
    }
    return trs;
  };

  newIdentifier = 0;

  renderAddParameterButton = () => {
    const onSelect = ({key}) => {
      this.newIdentifier += 1;
      const identifier = this.newIdentifier;
      const field = {
        identifier,
        reference: key !== 'string',
        type: key,
        name: '',
        value: null,
        multiValue: false
      };
      const customFields = this.state.customFields;
      customFields.push(field);
      this.setState({customFields});
    };
    const parameterTypeMenu = (
      <Menu onClick={onSelect}>
        <Menu.Item key="string">String parameter</Menu.Item>
        {
          this.ownEntityTypes().map(e => {
            return (
              <Menu.Item key={e.name}>Link to '{e.name}' instance</Menu.Item>
            );
          })
        }
      </Menu>
    );
    return (
      <Row type="flex" justify="space-around" style={{marginTop: 10}}>
        <Button.Group>
          <Button
            disabled={this.props.pending}
            id="add-parameter-button"
            onClick={() => onSelect({key: 'string'})}>
            Add parameter
          </Button>
          <Dropdown overlay={parameterTypeMenu} placement="bottomRight" style={{minWidth: 200}}>
            <Button
              id="add-parameter-dropdown-button"
              disabled={this.props.pending}>
              <Icon type="down" />
            </Button>
          </Dropdown>
        </Button.Group>
      </Row>
    );
  };

  render () {
    const {resetFields, getFieldDecorator} = this.props.form;
    const onClose = () => {
      resetFields();
    };
    const footer = this.props.pending
      ? false
      : (
        <Row type="flex" justify="end" className={styles.actions}>
          <Button
            id="add-instance-form-cancel-button"
            onClick={this.props.onCancel}>Cancel</Button>
          <Button
            id="add-instance-form-create-button"
            type="primary"
            htmlType="submit"
            onClick={this.handleSubmit}>Create</Button>
        </Row>
      );
    return (
      <Modal
        visible={this.props.visible}
        closable={!this.props.pending}
        onCancel={this.props.onCancel}
        title="Add instance"
        style={{height: '80vh'}}
        width="60%"
        footer={footer}
        afterClose={onClose}>
        <Form id="add-instance-form">
          <Form.Item
            className={styles.formItem}
            {...this.formItemLayout}
            label="Instance type">
            {getFieldDecorator('entityClass', {
              rules: [{
                required: true,
                message: 'Instance type is required'
              }],
              initialValue: this.props.entityType !== undefined && this.props.entityType !== null
                ? `${this.props.entityType}` : null
            })(
              <Select
                disabled={this.props.pending}
                showSearch
                style={{width: '100%'}}
                allowClear
                optionFilterProp="children"
                onSelect={this.onSelectEntityType}
                filterOption={
                  (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }
              >
                {
                  this.entityTypes().map(t =>
                    <Select.Option
                      key={t.id}
                      value={`${t.id}`}>
                      {t.name}
                    </Select.Option>
                  )
                }
              </Select>
            )}
          </Form.Item>
          <Form.Item
            className={styles.formItem}
            {...this.formItemLayout}
            label="Instance ID">
            {getFieldDecorator('id')(
              <Input
                ref={!this.props.dataStorage ? this.initializeNameInput : null}
                disabled={this.props.pending}
              />
            )}
          </Form.Item>
          {
            this.state.fields && this.state.fields.length > 0 && this.state.fields
              .map(this.renderField(false)).reduce((array, trs) => {
                array.push(...trs);
                return array;
              }, [])
          }
          {
            this.state.customFields
              .map(this.renderField(true))
              .reduce((array, trs) => {
                array.push(...trs);
                return array;
              }, [])
          }
          {this.renderAddParameterButton()}
          <SelectMetadataItems
            visible={!!this.state.selectMetadataItems}
            type={
              this.state.selectMetadataItems
                ? this.state.selectMetadataItems.type
                : undefined
            }
            selection={
              this.state.selectMetadataItems
                ? this.state.selectMetadataItems.value
                : []
            }
            multiple={
              this.state.selectMetadataItems
                ? this.state.selectMetadataItems.multiple
                : false
            }
            onClose={this.onCloseMetadataItemSelection}
            onSave={this.onSelectMetadataItems}
            folderId={this.props.folderId}
          />
        </Form>
      </Modal>
    );
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  componentWillReceiveProps (nextProps) {
    const typesAreEquals = (type1, type2) => type1.metadataClass.id === type2.metadataClass.id;
    if (this.props.entityTypes !== nextProps.entityTypes ||
      !compareArrays(this.props.entityTypes, nextProps.entityTypes, typesAreEquals)) {
      this.rebuildEntityTypeParameters(nextProps.entityType);
    }
  }

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
      this.rebuildEntityTypeParameters(this.props.entityType);
    }
  }
}
