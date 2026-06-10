/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {useEffect, useMemo} from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Button, Checkbox, Col, Dropdown, Input, Row, Select, Space} from 'antd';
import {
  DownloadOutlined,
  DownOutlined,
  FolderOutlined,
  MinusCircleOutlined,
  SelectOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import classNames from 'classnames';
import BucketBrowser from '../../pipelines/launch/dialogs/BucketBrowser';
import SystemParametersBrowser from '../../pipelines/launch/dialogs/SystemParametersBrowser';
import {CP_CAP_LIMIT_MOUNTS} from '../../pipelines/launch/form/utilities/parameters';
import roleModel from '../../../utils/roleModel';
import EditToolFormParametersVM from './EditToolFormParametersVM';
import styles from './EditToolFormParameters.module.css';

const PARAMETER_TYPE_MENU_ITEMS = [
  {key: 'string', label: 'String parameter', id: 'add-string-parameter'},
  {key: 'boolean', label: 'Boolean parameter', id: 'add-boolean-parameter'},
  {key: 'path', label: 'Path parameter', id: 'add-path-parameter'},
  {key: 'input', label: 'Input path parameter', id: 'add-input-parameter'},
  {key: 'output', label: 'Output path parameter', id: 'add-output-parameter'},
  {key: 'common', label: 'Common path parameter', id: 'add-common-parameter'},
];

const PATH_PARAMETER_ICONS = {
  input: DownloadOutlined,
  output: UploadOutlined,
  common: SelectOutlined,
};

function StringParameterInput({parameter, sectionName, onChange, isError, disabled}) {
  return (
    <Input
      id={`${sectionName}.params.param_${parameter.id}.value`}
      disabled={disabled}
      value={parameter.value}
      onChange={onChange}
      className={classNames({'cp-error': isError})}
      style={{width: '100%', marginLeft: 5}}
    />
  );
}

function SelectParameterInput({parameter, sectionName, onChange, isError, disabled}) {
  return (
    <Select
      id={`${sectionName}.params.param_${parameter.id}.value`}
      disabled={disabled}
      value={parameter.value}
      onChange={(v) => onChange({target: {value: v}})}
      className={classNames({'cp-error': isError})}
      style={{width: '100%', marginLeft: 5}}
      options={(parameter.enum || []).map((e) => ({label: e, value: e}))}
    />
  );
}

function BooleanParameterInput({parameter, sectionName, onChange, isError, disabled}) {
  return (
    <Checkbox
      id={`${sectionName}.params.param_${parameter.id}.value`}
      disabled={disabled}
      checked={`${parameter.value}` === 'true'}
      className={classNames({'cp-error': isError})}
      style={{marginLeft: 5, marginTop: 4}}
      onChange={onChange}
    >
      Enabled
    </Checkbox>
  );
}

function PathParameterInput({
  parameter,
  sectionName,
  onChange,
  isError,
  disabled,
  onOpenBucketBrowser,
}) {
  const IconComponent = PATH_PARAMETER_ICONS[parameter.type] || FolderOutlined;
  return (
    <Input
      id={`${sectionName}.params.param_${parameter.id}.value`}
      disabled={disabled}
      className={classNames({'cp-error': isError})}
      style={{width: '100%', marginLeft: 5, top: 0}}
      value={parameter.value}
      onChange={onChange}
      addonBefore={
        <div style={{cursor: 'pointer'}} onClick={onOpenBucketBrowser}>
          <IconComponent />
        </div>
      }
      placeholder="Path"
    />
  );
}

function resolveParameterInputComponent(parameter) {
  switch ((parameter.type || '').toLowerCase()) {
    case 'path':
    case 'output':
    case 'input':
    case 'common':
      return PathParameterInput;
    case 'boolean':
      return BooleanParameterInput;
    default:
      if (parameter.enum && parameter.enum.length) {
        return SelectParameterInput;
      }
      return StringParameterInput;
  }
}

function AddParameterButton({vm, readOnly, isSystemParameters}) {
  if (isSystemParameters) {
    return (
      <Button onClick={vm.openSystemParameterBrowser} disabled={readOnly}>
        Add system parameters
      </Button>
    );
  }
  const onSelect = ({key}) => {
    vm.addParameter({
      type: key,
      defaultValue: key === 'boolean' ? 'true' : undefined,
    });
  };
  return (
    <Space.Compact>
      <Button
        disabled={readOnly}
        onClick={() => vm.addParameter({type: 'string'})}
        id="add-parameter-button"
      >
        Add parameter
      </Button>
      {!readOnly ? (
        <Dropdown
          menu={{items: PARAMETER_TYPE_MENU_ITEMS, onClick: onSelect}}
          placement="bottomRight"
        >
          <Button id="add-parameter-dropdown-button" disabled={readOnly}>
            <DownOutlined />
          </Button>
        </Dropdown>
      ) : undefined}
    </Space.Compact>
  );
}

const ObservedAddParameterButton = observer(AddParameterButton);

function ParameterRow({vm, parameter, index, readOnly, isSystemParameters}) {
  if (!vm.shouldRenderParameter(parameter)) {
    return null;
  }
  const {initial = false} = parameter;
  const restrictedReadOnly = initial && vm.isSystemParameterRestrictedByRole(parameter);
  const isBoolean = (parameter.type || '').toLowerCase() === 'boolean';
  const onChange = (property) => (e) => {
    const value = isBoolean && property === 'value' ? e.target.checked : e.target.value;
    vm.updateParameter(index, property, value);
  };
  const onRemoveParameter = () => vm.removeParameter(index);
  const InputComponent = resolveParameterInputComponent(parameter);
  const validationEntry = vm.validation[index] || {};
  const nameError = validationEntry.error;
  const valueError = validationEntry.errorValue;
  return (
    <Row
      key={index}
      id={`${vm.sectionName}.params.param_${parameter.id}`}
      style={{display: 'flex', marginTop: 5, marginBottom: 5}}
      align="top"
    >
      <Col
        offset={3}
        span={3}
        style={{textAlign: 'right', display: 'flex', flexDirection: 'column'}}
      >
        <Input
          id={`${vm.sectionName}.params.param_${parameter.id}.name`}
          disabled={readOnly || isSystemParameters || restrictedReadOnly}
          className={classNames(styles.parameter, styles.parameterName, {
            [styles.wrong]: nameError,
            'cp-error': nameError,
            'cp-text-not-important': isSystemParameters,
          })}
          value={parameter.name}
          onChange={onChange('name')}
          style={{width: '100%', marginRight: 5}}
        />
        {nameError && <span className={classNames(styles.error, 'cp-error')}>{nameError}</span>}
      </Col>
      <Col span={12} style={{display: 'flex', flexDirection: 'column'}}>
        <InputComponent
          parameter={parameter}
          sectionName={vm.sectionName}
          onChange={onChange('value')}
          isError={!!valueError}
          disabled={readOnly || restrictedReadOnly}
          onOpenBucketBrowser={() => vm.openBucketBrowser(index)}
        />
        {valueError && (
          <span className={classNames(styles.error, 'cp-error')} style={{marginLeft: 5}}>
            {valueError}
          </span>
        )}
      </Col>
      <Col>
        {!readOnly && !restrictedReadOnly && (
          <MinusCircleOutlined
            id="remove-parameter-button"
            className="dynamic-delete-button"
            style={{cursor: 'pointer', marginLeft: 20, marginTop: 7}}
            onClick={onRemoveParameter}
          />
        )}
      </Col>
    </Row>
  );
}

const ObservedParameterRow = observer(ParameterRow);

function EditToolFormParameters(props) {
  const {
    vm: externalVm,
    onInitialized,
    readOnly,
    value,
    isSystemParameters,
    getSystemParameterDisabledState,
    skippedSystemParameters,
    testSkipParameter,
    runDefaultParameters,
    authenticatedUserInfo,
  } = props;

  const internalVm = useMemo(() => new EditToolFormParametersVM(), []);
  const vm = externalVm ?? internalVm;

  const vmProps = useMemo(
    () => ({
      value,
      readOnly,
      isSystemParameters,
      getSystemParameterDisabledState,
      skippedSystemParameters,
      testSkipParameter,
      runDefaultParameters,
      authenticatedUserInfo,
    }),
    [
      value,
      readOnly,
      isSystemParameters,
      getSystemParameterDisabledState,
      skippedSystemParameters,
      testSkipParameter,
      runDefaultParameters,
      authenticatedUserInfo,
    ],
  );

  useEffect(() => {
    vm.setProps(vmProps);
  }, [vm, vmProps]);

  useEffect(() => {
    if (!externalVm && onInitialized) {
      onInitialized(vm);
    }
  }, [vm, externalVm, onInitialized]);

  const bucketBrowserIndex = vm.bucketBrowserParameter;
  const bucketBrowserParameter =
    bucketBrowserIndex !== null ? vm.parameters[bucketBrowserIndex] : null;
  const bucketBrowserType = bucketBrowserParameter
    ? (bucketBrowserParameter.type || '').toLowerCase()
    : '';

  return (
    <div>
      {vm.parameters.map((parameter, index) => (
        <ObservedParameterRow
          key={parameter.id}
          vm={vm}
          parameter={parameter}
          index={index}
          readOnly={readOnly}
          isSystemParameters={isSystemParameters}
        />
      ))}
      <Row style={{display: 'flex'}} justify="space-around" align="middle">
        <ObservedAddParameterButton
          vm={vm}
          readOnly={readOnly}
          isSystemParameters={isSystemParameters}
        />
      </Row>
      <BucketBrowser
        multiple
        onSelect={vm.selectBucketPath}
        onCancel={vm.closeBucketBrowser}
        visible={bucketBrowserIndex !== null}
        path={bucketBrowserParameter ? bucketBrowserParameter.value : null}
        showOnlyFolder={bucketBrowserType === 'output'}
        checkWritePermissions={bucketBrowserType === 'output'}
        bucketTypes={['AZ', 'S3', 'GS', 'DTS', 'NFS']}
      />
      <SystemParametersBrowser
        visible={vm.systemParameterBrowserVisible}
        onCancel={vm.closeSystemParameterBrowser}
        onSave={vm.addSystemParameters}
        notToShow={[
          ...vm.parameters.map((p) => p.name),
          CP_CAP_LIMIT_MOUNTS,
          ...vm.skippedSystemParameters,
        ]}
      />
    </div>
  );
}

EditToolFormParameters.propTypes = {
  value: PropTypes.array,
  onInitialized: PropTypes.func,
  readOnly: PropTypes.bool,
  isSystemParameters: PropTypes.bool,
  getSystemParameterDisabledState: PropTypes.func,
  skippedSystemParameters: PropTypes.array,
  testSkipParameter: PropTypes.func,
  vm: PropTypes.instanceOf(EditToolFormParametersVM),
};

export default inject('runDefaultParameters')(
  roleModel.authenticationInfo(observer(EditToolFormParameters)),
);
