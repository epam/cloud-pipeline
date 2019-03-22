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
  Modal,
  Button,
  Form,
  Row,
  Input,
  Select,
  Collapse,
  AutoComplete,
  Menu,
  Icon,
  Dropdown
} from 'antd';
import MetadataEntityFilter from '../../../../models/folderMetadata/MetadataEntityFilter';
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
    search: null
  };

  handleSubmit = (e) => {
    e.preventDefault();
    const valid = this.validate();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (valid && !err && this.state.customFields.filter(f => !!f.validation).length === 0) {
        const data = this.state.fields.filter(f => !!f.value).map(f => {
          return {
            name: f.name,
            type: f.reference ? `${f.type}:ID` : f.type,
            value: f.value
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
            type: f.reference ? `${f.type}:ID` : f.type,
            value: f.value
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

  onSelectEntityType = (id) => {
    this.props.form.setFieldsValue({entityClass: id});
    this.rebuildEntityTypeParameters(id);
  };

  rebuildEntityTypeParameters = (entityTypeId) => {
    const [entityTypeInfo] = (this.props.entityTypes || []).filter(e => `${e.metadataClass.id}` === `${entityTypeId}`);
    let fields = [];
    if (entityTypeInfo) {
      const onSearchFinished = (name, values) => {
        const fields = this.state.fields;
        const [field] = fields.filter(f => f.name === name);
        if (field) {
          field.dataSource = values;
          this.setState({fields});
        }
      };
      fields = (entityTypeInfo.fields || []).map(f => (
        {
          ...f,
          value: null,
          search: null,
          dataSource: [],
          onSearch: async (search) => {
            if (!f.reference) {
              return;
            }
            const request = new MetadataEntityFilter();
            await request.send({
              folderId: this.props.folderId,
              metadataClass: f.type,
              externalIdQueries: [search],
              page: 1,
              pageSize: 100000
            });
            if (request.loaded) {
              onSearchFinished(f.name, (request.value.elements || []).map(e => e.externalId));
            } else {
              onSearchFinished(f.name, []);
            }
          }
        })
      );
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
    if (field.reference) {
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
    trs.push(
      <tr
        key={`parameter_${index}`}>
        <td className={`${styles.parameterName} ${custom ? styles.custom : ''}`}>
          {
            custom
              ? (
              <Input
                placeholder="Parameter name"
                className={`${styles.nameInput} ${field.validation ? styles.validationError : ''}`}
                value={field.name}
                onChange={this.onChangeParameterName(parameterFilterFn)}
                style={{width: '100%'}} />
            )
              : <span>{field.name}: </span>
          }
        </td>
        <td>
          <Row type="flex" align="middle">
            <AutoComplete
              dataSource={field.dataSource}
              style={{flex: 1}}
              onSelect={this.onChangeReferenceValue(parameterFilterFn, custom)}
              onSearch={field.onSearch}
              placeholder={`Filter '${field.type}' instances`}
            />
            {
              custom && (
                <Button
                  size="small"
                  onClick={this.onRemoveParameter(f => f.identifier === field.identifier)}
                  type="danger"
                  style={{marginLeft: 5}}>
                  <Icon type="delete" />
                </Button>
              )
            }
          </Row>
        </td>
      </tr>
    );
    if (field.validation) {
      trs.push(
        <tr key={`parameter_${index}_validation`}>
          <td className={styles.validationError} style={{paddingRight: 5}}>
            {field.validation}
          </td>
          <td>{'\u00A0'}</td>
        </tr>
      );
    }
    return trs;
  };

  renderStringField = (field, index, custom = false) => {
    const parameterFilterFn = custom
      ? f => f.identifier === field.identifier
      : f => f.name === field.name;
    const trs = [];
    trs.push(
      <tr
        key={`parameter_${index}`}>
        <td className={`${styles.parameterName} ${custom ? styles.custom : ''}`}>
          {
            custom
              ? (
              <Input
                placeholder="Parameter name"
                className={`${styles.nameInput} ${field.validation ? styles.validationError : ''}`}
                value={field.name}
                onChange={this.onChangeParameterName(parameterFilterFn)}
                style={{width: '100%'}} />
            )
              : <span>{field.name}: </span>
          }
        </td>
        <td>
          <Row type="flex" align="middle">
            <Input
              style={{flex: 1}}
              value={field.value}
              placeholder="Parameter value"
              onChange={this.onChangeStringValue(parameterFilterFn, custom)} />
            {
              custom && (
                <Button
                  size="small"
                  onClick={this.onRemoveParameter(f => f.identifier === field.identifier)}
                  type="danger"
                  style={{marginLeft: 5}}>
                  <Icon type="delete" />
                </Button>
              )
            }
          </Row>
        </td>
      </tr>
    );
    if (field.validation) {
      trs.push(
        <tr key={`parameter_${index}_validation`}>
          <td className={styles.validationError} style={{paddingRight: 5}}>
            {field.validation}
          </td>
          <td>{'\u00A0'}</td>
        </tr>
      );
    }
    return trs;
  };

  onParameterSearchChanged = (e) => {
    this.setState({
      search: e.target.value
    });
  };

  filterFields = (f) => {
    return !this.state.search || f.name.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0;
  };

  newIdentifier = 0;

  renderAddParameterButton = () => {
    const onSelect = ({key}) => {
      const onSearchFinished = (identifier, values) => {
        const fields = this.state.customFields;
        const [field] = fields.filter(f => f.identifier === identifier);
        if (field) {
          field.dataSource = values;
          this.setState({customFields: fields});
        }
      };
      this.newIdentifier += 1;
      const identifier = this.newIdentifier;
      const field = {
        identifier,
        reference: key !== 'string',
        type: key,
        name: '',
        value: null,
        search: null,
        dataSource: [],
        onSearch: async (search) => {
          if (key === 'string') {
            return;
          }
          const request = new MetadataEntityFilter();
          await request.send({
            folderId: this.props.folderId,
            metadataClass: key,
            externalIdQueries: [search],
            page: 1,
            pageSize: 100000
          });
          if (request.loaded) {
            onSearchFinished(identifier, (request.value.elements || []).map(e => e.externalId));
          } else {
            onSearchFinished(identifier, []);
          }
        }
      };
      const customFields = this.state.customFields;
      customFields.push(field);
      this.setState({customFields});
    };
    const parameterTypeMenu = (
      <Menu onClick={onSelect}>
        <Menu.Item key="string">String parameter</Menu.Item>
        {
          this.entityTypes().map(e => {
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
            label="Instance ID">
            {getFieldDecorator('id', {
              rules: [{
                required: true,
                message: 'ID is required'
              }]
            })(
              <Input
                ref={!this.props.dataStorage ? this.initializeNameInput : null}
                disabled={this.props.pending} />
            )}
          </Form.Item>
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
                allowClear={true}
                optionFilterProp="children"
                onSelect={this.onSelectEntityType}
                filterOption={
                  (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
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
          {
            this.state.fields && this.state.fields.length > 0 &&
            <Collapse style={{marginBottom: 10}} bordered={false}>
              <Collapse.Panel header="Parameters">
                <Row type="flex" style={{marginBottom: 15}}>
                  <Input.Search
                    id="add-instance-search-input"
                    placeholder="Filter fields"
                    onChange={this.onParameterSearchChanged} />
                </Row>
                <table style={{width: '100%'}}>
                  <tbody>
                  {
                    this.state.fields
                      .filter(this.filterFields)
                      .map(this.renderField(false)).reduce((array, trs) => {
                        array.push(...trs);
                        return array;
                      }, [])
                  }
                  </tbody>
                </table>
              </Collapse.Panel>
            </Collapse>
          }
          <table style={{width: '100%'}}>
            <tbody>
            {
              this.state.customFields
                .map(this.renderField(true))
                .reduce((array, trs) => {
                  array.push(...trs);
                  return array;
                }, [])
            }
            </tbody>
          </table>
          {this.renderAddParameterButton()}
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
