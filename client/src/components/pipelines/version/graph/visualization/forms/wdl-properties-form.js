/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
  Checkbox,
  Collapse,
  Icon,
  Input,
  message,
  Modal
} from 'antd';
import Dropdown from 'rc-dropdown';
import Menu, {SubMenu, MenuItem, Divider} from 'rc-menu';
import {
  ContextTypes,
  isCall,
  isTask,
  isWorkflow,
  WdlEvent
} from '../../../../../../utils/pipeline-builder';
import DropDownWrapper from '../../../../../special/dropdown-wrapper';
import extractEntityProperties from './utilities/extract-entity-properties';
import CodeEditor from '../../../../../special/CodeEditor';
import WdlRuntimeDocker from './form-items/wdl-runtime-docker';
import WdlRuntimeNode from './form-items/wdl-runtime-node';
import WdlParameter from './form-items/wdl-parameter';
import {
  addCall,
  addConditional,
  addScatter,
  getEntityNameOptions
} from './utilities/workflow-utilities';
import WdlIssues from './form-items/wdl-issues';
import styles from './wdl-properties-form.css';

function getEntityName (e) {
  const opts = getEntityNameOptions(e);
  if (!opts) {
    return null;
  }
  return (
    <span>
      {(opts.type || '').toLowerCase()}
      {
        opts.type && opts.name && ' '
      }
      {opts.name && (<b>{opts.name}</b>)}
    </span>
  );
}

class WdlPropertiesForm extends React.Component {
  state = {
    ...extractEntityProperties(),
    expandedKeys: [],
    onRemoveRequest: false,
    removeExecutable: false
  };

  componentDidMount () {
    this.changeEntity(this.props.entity);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.entity !== this.props.entity) {
      this.changeEntity(this.props.entity, prevProps.entity);
    }
  }

  componentWillUnmount () {
    this.changeEntity(undefined, this.props.entity);
  }

  changeEntity (newEntity, previousEntity) {
    if (previousEntity) {
      // unsubscribe from events
      previousEntity.off(WdlEvent.changed, this.onChangedEvent);
      previousEntity.off(WdlEvent.validation, this.onChangedEvent);
    }
    if (newEntity) {
      // subscribe on events
      newEntity.on(WdlEvent.changed, this.onChangedEvent);
      newEntity.on(WdlEvent.validation, this.onChangedEvent);
    }
    this.onEntityPropertiesChanged(true);
  }

  onChangedEvent = () => {
    this.onEntityPropertiesChanged();
  }

  onEntityPropertiesChanged = (entityChanged = false) => this.setState({
    ...extractEntityProperties(this.props.entity),
    onRemoveRequest: entityChanged ? false : this.state.onRemoveRequest,
    removeExecutable: entityChanged ? false : this.state.removeExecutable
  });

  setExpandedKeys = (keys) => this.setState({
    expandedKeys: keys
  });

  onRemove = () => {
    const {
      canRemoveEntity
    } = this.state;
    const {
      entity
    } = this.props;
    if (
      !entity ||
      isWorkflow(entity) ||
      !canRemoveEntity
    ) {
      return false;
    }
    this.setState({
      onRemoveRequest: true,
      removeExecutable: false
    });
  };

  onCancelRemove = () => {
    this.setState({
      onRemoveRequest: false,
      removeExecutable: false
    });
  };

  onConfirmRemove = () => {
    const {
      removeExecutable,
      canRemoveEntity
    } = this.state;
    const {
      entity,
      disabled,
      onRemoved
    } = this.props;
    if (!disabled && entity && canRemoveEntity && !isWorkflow(entity)) {
      const parts = [(
        <span key="main">
          {getEntityName(entity)}
        </span>
      )];
      if (removeExecutable && entity.executable) {
        parts.push((<span key="and">{' and '}</span>));
        parts.push((
          <span key="executable">
            {getEntityName(entity.executable)}
          </span>
        ));
      }
      const hide = message.loading(
        (
          <span style={{whiteSpace: 'pre'}}>
            {'Removing '}
            {parts}
            {'...'}
          </span>
        ),
        0
      );
      const removeAction = (action) => {
        if (action && typeof action.remove === 'function') {
          action.remove(action);
        }
      };
      try {
        removeAction(entity);
        if (removeExecutable) {
          removeAction(entity.executable);
        }
        if (typeof onRemoved === 'function') {
          onRemoved();
        }
      } catch (error) {
        message.error(error.message, 5);
      } finally {
        this.setState({
          onRemoveRequest: false,
          removeExecutable: false
        });
        hide();
      }
    }
  };

  createAction = (actionFn) => {
    const {
      onActionAdded
    } = this.props;
    try {
      const action = actionFn();
      if (typeof onActionAdded === 'function') {
        onActionAdded(action);
      }
    } catch (error) {
      console.warn(error.message);
      message.error(error.message, 5);
    }
  };

  onAddScatter = () => {
    const {
      entity
    } = this.props;
    this.createAction(() => addScatter(entity));
  };

  onAddConditional = () => {
    const {
      entity
    } = this.props;
    this.createAction(() => addConditional(entity));
  };

  onAddCall = (task) => {
    const {
      entity
    } = this.props;
    this.createAction(() => addCall(entity, task));
  };

  renderHeader = () => {
    const {
      disabled
    } = this.props;
    const {
      type,
      actionsDropdownVisible = false,
      canAddSubAction = false,
      canRemoveEntity,
      executables = []
    } = this.state;
    if (type) {
      const actionSelect = ({key}) => {
        this.setState({
          actionsDropdownVisible: false
        }, () => {
          switch (key) {
            case 'call':
              this.onAddCall();
              break;
            case 'scatter':
              this.onAddScatter();
              break;
            case 'conditional':
              this.onAddConditional();
              break;
            default: {
              const e = /^call_(.+)$/i.exec(key);
              if (e && e[1]) {
                const executable = executables.find((ex) => ex.name === e[1]);
                if (executable) {
                  this.onAddCall(executable);
                }
              }
            }
              break;
          }
        });
      };
      let actionButton;
      if (canAddSubAction && !disabled) {
        actionButton = (
          <DropDownWrapper
            key="create actions"
            visible={this.state.actionsDropdownVisible}
          >
            <Dropdown
              placement="bottomRight"
              trigger={['click']}
              visible={actionsDropdownVisible}
              onVisibleChange={visible => this.setState({actionsDropdownVisible: visible})}
              minOverlayWidthMatchTrigger={false}
              overlay={
                <div>
                  <Menu
                    mode="vertical"
                    selectedKeys={[]}
                    subMenuOpenDelay={0.2}
                    subMenuCloseDelay={0.2}
                    openAnimation="zoom"
                    getPopupContainer={node => node.parentNode}
                    onClick={actionSelect}
                    style={{width: 200, cursor: 'pointer'}}
                  >
                    {
                      (executables || []).length > 0 && (
                        <SubMenu
                          onTitleClick={() => actionSelect({key: 'call'})}
                          key="call-submenu"
                          title={(
                            <span>
                              Add <b>call</b>
                            </span>
                          )}
                          style={{width: 200, cursor: 'pointer'}}
                        >
                          <MenuItem key="call">
                            Add <b>new task</b> call
                          </MenuItem>
                          <Divider />
                          {
                            executables.map((executable) => (
                              <MenuItem key={`call_${executable.name}`}>
                                Add <b>{executable.name}</b> call
                              </MenuItem>
                            ))
                          }
                        </SubMenu>
                      )
                    }
                    {
                      (executables || []).length === 0 && (
                        <MenuItem key="call">
                          Add <b>call</b>
                        </MenuItem>
                      )
                    }
                    <MenuItem key="scatter">
                      Add <b>scatter</b>
                    </MenuItem>
                    <MenuItem key="conditional">
                      Add <b>conditional</b>
                    </MenuItem>
                  </Menu>
                </div>
              }
              key="actions"
            >
              <Button
                size="small"
                disabled={disabled}
              >
                Actions <Icon type="down" />
              </Button>
            </Dropdown>
          </DropDownWrapper>
        );
      }
      return (
        <div
          className={styles.propertiesRow}
        >
          <b>{type}</b>
          <div className={styles.actions}>
            {actionButton}
            {
              !disabled && canRemoveEntity && (
                <Button
                  type="danger"
                  size="small"
                  onClick={() => this.onRemove()}
                >
                  <Icon type="delete" /> Remove
                </Button>
              )
            }
          </div>
        </div>
      );
    }
    return null;
  };

  renderIssuesBlock = () => {
    const {
      issues = []
    } = this.state;
    return (
      <Collapse
        bordered={false}
        className="wdl-properties-collapse"
      >
        <Collapse.Panel
          key="issues"
          header={(<span>Issues {issues.length}</span>)}
        >
          <WdlIssues
            issues={issues}
            alert
            fullDescription
          />
        </Collapse.Panel>
      </Collapse>
    );
  };

  /**
   * @typedef {Object} InputPropertyConfig
   * @property {string} [title]
   * @property {string} property
   * @property {string} [propertyAvailable=`${property}Available`]
   * @property {string} [placeholder]
   * @property {object} [data=this.state]
   * @property {function} [setter]
   * @property {*[]} [issues=[]]
   */

  /**
   * @param config
   */
  renderInputProperty = (config) => {
    const {
      entity,
      disabled
    } = this.props;
    if (!entity || !config || !config.property) {
      return null;
    }
    const defaultSetter = (e, newValue) => {
      e[property] = newValue;
    };
    const {
      title,
      property,
      data = this.state,
      setter = defaultSetter,
      placeholder,
      propertyAvailable = `${property}Available`,
      issues = []
    } = config;
    const onChange = (event) => setter(entity, event.target.value);
    const {
      [property]: value,
      [propertyAvailable]: available
    } = data || {};
    if (!available) {
      return;
    }
    return (
      <div>
        <div
          className={styles.propertiesRow}
        >
          {
            title && (
              <div
                className={
                  classNames(
                    styles.propertyTitle,
                    {
                      'cp-error': issues.length > 0
                    }
                  )
                }
              >
                <span>{title}:</span>
              </div>
            )
          }
          <Input
            disabled={disabled}
            className={
              classNames(
                styles.propertyValue,
                {
                  'cp-error': issues.length > 0
                }
              )
            }
            placeholder={placeholder}
            value={value}
            onChange={onChange}
          />
        </div>
        <WdlIssues issues={issues} />
      </div>
    );
  };

  getDefaultPlaceholder = (placeholder) => {
    const {
      type
    } = this.state;
    if (type) {
      return `${type} ${(placeholder || '').toLowerCase()}`;
    }
    return placeholder;
  };

  renderName = () => {
    const {
      nameIssues = []
    } = this.state;
    return this.renderInputProperty({
      title: 'Name',
      placeholder: this.getDefaultPlaceholder('name'),
      property: 'name',
      issues: nameIssues
    });
  };

  renderAlias = () => {
    const {
      executableName,
      nameIssues = []
    } = this.state;
    return this.renderInputProperty({
      title: 'Alias',
      placeholder: executableName || this.getDefaultPlaceholder('alias'),
      property: 'alias',
      issues: nameIssues
    });
  };

  renderExecutable = () => {
    const {
      executableType = 'Executable'
    } = this.state;
    return this.renderInputProperty({
      title: executableType,
      placeholder: this.getDefaultPlaceholder('executable'),
      property: 'executableName'
    });
  };

  renderCondition = () => {
    const {
      issues = []
    } = this.state;
    return this.renderInputProperty({
      title: 'Condition',
      placeholder: 'Condition',
      property: 'expression',
      issues
    });
  };

  renderParametersBlock = (options = {}) => {
    const {
      entity,
      disabled
    } = this.props;
    const {
      available = true,
      title,
      parameters = [],
      editable = false,
      addTitle = 'Add',
      contextType = ContextTypes.input,
      owner = entity,
      key
    } = options;
    const {
      expandedKeys = []
    } = this.state;
    if (!available) {
      return null;
    }
    if (!editable && parameters.length === 0) {
      return null;
    }
    const addParameter = (parameterOwner) => {
      if (
        parameterOwner &&
        typeof parameterOwner.addParameters === 'function'
      ) {
        const aName = typeof parameterOwner.generateParameterName === 'function'
          ? parameterOwner.generateParameterName()
          : 'parameter';
        parameterOwner.addParameters([{
          name: aName,
          type: 'File'
        }], contextType);
      }
    };
    const onAddClick = (event) => {
      if (event) {
        event.stopPropagation();
        event.preventDefault();
      }
      if (!expandedKeys.includes(key)) {
        this.setState({
          expandedKeys: [...expandedKeys, key]
        });
      }
      if (isCall(owner)) {
        addParameter(owner.executable);
      } else {
        addParameter(owner);
      }
    };
    const header = parameters.length === 0 ? title : `${title} (${parameters.length})`;
    return (
      <Collapse.Panel
        key={key}
        header={(
          <div
            className={classNames(styles.headerRow)}
          >
            <span>{header}</span>
            {
              editable && !disabled && (
                <Button
                  size="small"
                  style={{marginLeft: 'auto'}}
                  onClick={onAddClick}
                >
                  ADD
                </Button>
              )
            }
          </div>
        )}
        className="cp-collapse-body-no-padding"
      >
        {
          parameters.map((parameter, idx, arr) => (
            <WdlParameter
              key={parameter.uuid}
              className={
                classNames(
                  'cp-even-odd-element',
                  {
                    'cp-divider': idx !== arr.length - 1,
                    'bottom': idx !== arr.length - 1
                  },
                  styles.parameter
                )
              }
              parameter={parameter}
              disabled={disabled}
              removable={editable}
            />
          ))
        }
        {
          editable && !disabled && (
            <div className={styles.propertiesRow}>
              <a onClick={onAddClick}>
                <Icon type="plus" /> {addTitle.toLowerCase()}
              </a>
            </div>
          )
        }
      </Collapse.Panel>
    );
  };

  renderParameters = () => {
    const {
      entity
    } = this.props;
    const {
      inputs = [],
      inputsAvailable = false,
      inputsEditable = false,
      declarations = [],
      declarationsAvailable = false,
      declarationsEditable = false,
      scatterItems = [],
      scatterItemsAvailable = false,
      outputs = [],
      outputsOwner = entity,
      outputsAvailable = false,
      outputsEditable = false,
      expandedKeys = []
    } = this.state;
    if (!entity) {
      return null;
    }
    return (
      <Collapse
        bordered={false}
        activeKey={expandedKeys}
        onChange={this.setExpandedKeys}
        className="wdl-properties-collapse"
      >
        {
          this.renderParametersBlock({
            available: scatterItemsAvailable,
            parameters: scatterItems,
            editable: false,
            title: 'Scatter item',
            key: 'scatter'
          })
        }
        {
          this.renderParametersBlock({
            available: inputsAvailable,
            parameters: inputs,
            editable: inputsEditable,
            title: 'Inputs',
            addTitle: 'add input',
            contextType: ContextTypes.input,
            key: 'inputs'
          })
        }
        {
          this.renderParametersBlock({
            available: declarationsAvailable,
            parameters: declarations,
            editable: declarationsEditable,
            title: 'Declarations',
            addTitle: 'add declaration',
            contextType: ContextTypes.declaration,
            key: 'declarations'
          })
        }
        {
          this.renderParametersBlock({
            available: outputsAvailable,
            parameters: outputs,
            editable: outputsEditable,
            title: 'Outputs',
            addTitle: 'Add output',
            contextType: ContextTypes.output,
            key: 'outputs',
            owner: outputsOwner
          })
        }
        {this.renderRuntimeAttributes()}
      </Collapse>
    );
  };

  renderRuntimeAttributes = () => {
    const {
      task,
      runtime = [],
      runtimeAttributesAvailable
    } = this.state;
    const {
      entity,
      allowedInstanceTypes,
      disabled
    } = this.props;
    if (
      entity &&
      task &&
      runtimeAttributesAvailable
    ) {
      const header = runtime.length === 0 ? 'Runtime' : `Runtime (${runtime.length})`;
      const onChangeRuntimeProperty = (property, value) => {
        task.setRuntime(property, value);
      };
      const onChangeRuntimeGenerator = (
        property,
        isEvent = true
      ) => (event) => onChangeRuntimeProperty(
        property,
        isEvent ? event.target.value : event
      );
      const onRemoveClick = (r) => {
        task.removeRuntime(r.property);
      };
      const addRuntime = (property) => {
        task.setRuntime(property);
      };
      const renderInput = (r) => {
        if (r.docker) {
          return (
            <WdlRuntimeDocker
              disabled={disabled}
              value={r.value}
              className={
                classNames(
                  styles.propertyValue,
                  {
                    'cp-error': !r.valid
                  }
                )
              }
              onChange={onChangeRuntimeGenerator(r.property, false)}
            />
          );
        }
        if (r.node) {
          return (
            <WdlRuntimeNode
              disabled={disabled}
              value={r.value}
              className={
                classNames(
                  styles.propertyValue,
                  {
                    'cp-error': !r.valid
                  }
                )
              }
              onChange={onChangeRuntimeGenerator(r.property, false)}
              allowedInstanceTypes={allowedInstanceTypes}
            />
          );
        }
        return (
          <Input
            disabled={disabled}
            value={r.value}
            className={
              classNames(
                styles.propertyValue,
                {
                  'cp-error': !r.valid
                }
              )
            }
            onChange={onChangeRuntimeGenerator(r.property)}
          />
        );
      };
      const hasDocker = runtime.find((o) => o.docker);
      const hasNode = runtime.find((o) => o.node);
      return (
        <Collapse.Panel
          key="runtime"
          className="cp-collapse-body-no-padding"
          header={(
            <div className={styles.headerRow}>
              {header}
            </div>
          )}
        >
          {
            runtime.map((r) => (
              <div key={r.property}>
                <div
                  className={styles.propertiesRow}
                >
                  <div
                    className={
                      classNames(
                        styles.propertyTitle,
                        {
                          'cp-error': !r.valid
                        }
                      )
                    }
                  >
                    {r.property}
                  </div>
                  {renderInput(r)}
                  {
                    (r.removable === undefined || r.removable) && !disabled && (
                      <div
                        className={styles.deleteButton}
                        onClick={() => onRemoveClick(r)}
                      >
                        <Icon
                          type="delete"
                          className={'cp-danger'}
                        />
                      </div>
                    )
                  }
                </div>
                <WdlIssues issues={r.issues || []} />
              </div>
            ))
          }
          {
            !hasDocker && !disabled && (
              <div className={styles.propertiesRow}>
                <a onClick={() => addRuntime('docker')}>
                  <Icon type="plus" /> add docker configuration
                </a>
              </div>
            )
          }
          {
            !hasNode && !disabled && (
              <div className={styles.propertiesRow}>
                <a onClick={() => addRuntime('node')}>
                  <Icon type="plus" /> add compute node configuration
                </a>
              </div>
            )
          }
        </Collapse.Panel>
      );
    }
    return null;
  };

  renderCommand = () => {
    const {
      command = undefined,
      commandAvailable = false,
      commandEditable = true,
      task,
      commandIssues = []
    } = this.state;
    const {
      disabled
    } = this.props;
    if (
      task &&
      isTask(task) &&
      commandAvailable
    ) {
      const onChange = (code) => {
        task.command = code;
      };
      return (
        <div>
          <div
            className={
              classNames(
                styles.propertiesRow,
                {
                  'cp-error': commandIssues.length > 0
                }
              )
            }
          >
            Command:
          </div>
          <CodeEditor
            readOnly={disabled || !commandEditable}
            className={classNames(styles.codeEditor, 'edit-wdl-form-code-container')}
            disabled={disabled}
            code={command}
            onChange={onChange}
            lineWrapping={false}
            language="shell"
          />
          <WdlIssues issues={commandIssues} />
        </div>
      );
    }
    return null;
  };

  renderRemoveConfirmationModal = () => {
    const {
      entity,
      disabled
    } = this.props;
    const renderFooter = () => (
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'flex-end'
        }}
      >
        <Button
          onClick={() => this.onCancelRemove()}
        >
          Cancel
        </Button>
        <Button
          type="danger"
          style={{marginLeft: 5}}
          onClick={() => this.onConfirmRemove()}
        >
          Remove
        </Button>
      </div>
    );
    const {
      removeExecutable,
      onRemoveRequest
    } = this.state;
    const canRemoveExecutable = entity &&
      isCall(entity) &&
      entity.executable &&
      entity.executable.workflow === entity.workflow &&
      !isWorkflow(entity.executable) &&
      (entity.executable.executions || []).length === 1;
    return (
      <Modal
        visible={
          !disabled &&
          !!entity &&
          !isWorkflow(entity) &&
          onRemoveRequest
        }
        closable
        onCancel={this.onCancelRemove}
        title={null}
        footer={renderFooter()}
      >
        <div
          style={{
            fontSize: 'larger',
            margin: 20,
            display: 'flex',
            flexDirection: 'row',
            alignItems: 'center'
          }}
        >
          <Icon
            type="question-circle"
            className="cp-warning"
            style={{fontSize: 'x-large'}}
          />
          <b>
            Are you sure you want to remove {getEntityName(entity)}?
          </b>
        </div>
        {
          canRemoveExecutable && (
            <div style={{margin: '10px 30px', fontSize: 'larger'}}>
              <Checkbox
                checked={removeExecutable}
                onChange={(e) => this.setState({removeExecutable: e.target.checked})}
              >
                Also remove {getEntityName(entity.executable)}
              </Checkbox>
            </div>
          )
        }
      </Modal>
    );
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={classNames(className, styles.wdlPropertiesForm)}
        style={style}
      >
        {this.renderHeader()}
        {this.renderCondition()}
        {this.renderName()}
        {this.renderAlias()}
        {this.renderIssuesBlock()}
        {this.renderExecutable()}
        {this.renderParameters()}
        {this.renderCommand()}
        {this.renderRemoveConfirmationModal()}
      </div>
    );
  }
}

WdlPropertiesForm.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  entity: PropTypes.object,
  wdlDocument: PropTypes.object,
  disabled: PropTypes.bool,
  allowedInstanceTypes: PropTypes.object,
  onRemoved: PropTypes.func,
  onActionAdded: PropTypes.func
};

export default WdlPropertiesForm;
