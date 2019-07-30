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
import {inject, observer} from 'mobx-react';
import {Button, Icon, Popover, Row} from 'antd';
import roleModel from '../../../utils/roleModel';

const SCHEMAS = /^(gs:\/\/|s3:\/\/|az:\/\/|cp:\/\/)/i;

function getStoragePathMask (storage) {
  const delimiter = storage.delimiter || '/';
  let pathMask = storage.pathMask;
  if (!pathMask.endsWith(delimiter)) {
    pathMask = `^${pathMask}${delimiter}`;
  }
  return new RegExp(pathMask, 'i');
}

function checkPermission (parameter, storageList, permissionChecker, permissionName) {
  const values = (parameter.value || '').split(',').map(v => v.trim());
  const errors = [];
  for (let i = 0; i < values.length; i++) {
    const value = values[i];
    if (!value || !value.length || !SCHEMAS.test(value)) {
      continue;
    }
    const [storage] = storageList.filter(
      s => s.pathMask.test(value) &&
        permissionChecker(s)
    );
    if (!storage) {
      errors.push(
        <span>
          <b>{value}</b>
          <span> ({parameter.name} parameter): {permissionName} operation is denied</span>
        </span>
      );
    }
  }
  return errors;
}

function getPaths (formParameters, defaultParameters, types) {
  const result = [];
  if (
    formParameters &&
    formParameters.hasOwnProperty('params') &&
    formParameters.hasOwnProperty('keys')
  ) {
    result.push(
      ...(
        Object.values(formParameters.params)
          .filter(p => formParameters.keys.indexOf(p.key) >= 0 && types.indexOf(p.type) >= 0)
      )
    );
  } else if (defaultParameters) {
    result.push(
      ...(
        Object.entries(defaultParameters)
          .map(([name, param]) => ({name, ...param}))
          .filter(p => types.indexOf(p.type) >= 0)
      )
    );
  }
  return result;
}

export function getInputPaths (formParameters, defaultParameters) {
  return getPaths(formParameters, defaultParameters, ['input', 'common', 'path']);
}

export function getOutputPaths (formParameters, defaultParameters) {
  return getPaths(formParameters, defaultParameters, ['output']);
}

function parameterChanged (oldParameter, newParameter) {
  if (!oldParameter && !newParameter) {
    return false;
  }
  if (!oldParameter) {
    return true;
  }
  if (!newParameter) {
    return true;
  }
  return oldParameter.value !== newParameter.value;
}

function parametersChanged (oldParameters, newParameters) {
  if (!oldParameters && !newParameters) {
    return false;
  }
  if (!oldParameters) {
    return true;
  }
  if (!newParameters) {
    return true;
  }
  if (oldParameters.length !== newParameters.length) {
    return true;
  }
  for (let i = 0; i < oldParameters.length; i++) {
    const oldParameter = oldParameters[i];
    const [newParameter] = newParameters.filter(p => p.name === oldParameter.name);
    if (parameterChanged(oldParameter, newParameter)) {
      return true;
    }
  }
  return false;
}

function checkDockerImage (props) {
  const {
    dockerImage,
    dockerRegistries
  } = props;
  if (dockerImage && dockerRegistries && dockerRegistries.loaded) {
    const [registry, group, image] = dockerImage.split('/');
    const registries = ((dockerRegistries.value || {}).registries || []);
    const [reg] = registries.filter(r =>
      r.path.toLowerCase() === (registry || '').toLowerCase()
    );
    const reportExecutionDenied = () => {
      return [(
        <span key="docker image error">
          <b>{dockerImage}</b>: execution is denied
        </span>
      )];
    };
    if (reg) {
      const groups = (reg.groups || []);
      const [gr] = groups.filter(g =>
        g.name.toLowerCase() === (group || '').toLowerCase()
      );
      if (gr) {
        let [imageName] = image.split(':');
        imageName = `${group}/${imageName}`.toLowerCase();
        const [tool] = (gr.tools || [])
          .filter(t => t.image.toLowerCase() === imageName);
        if (!tool || !roleModel.executeAllowed(tool)) {
          return reportExecutionDenied();
        }
      } else {
        return reportExecutionDenied();
      }
    } else {
      return reportExecutionDenied();
    }
  }
  return [];
}

function checkInputs (props) {
  const {
    dataStorages,
    inputs
  } = props;
  if (inputs && dataStorages && dataStorages.loaded) {
    const storages = (dataStorages.value || []).map(s => ({
      name: s.name,
      path: s.pathMask,
      pathMask: getStoragePathMask(s),
      mask: s.mask
    }));
    const result = [];
    for (let i = 0; i < (inputs || []).length; i++) {
      result.push(...checkPermission(
        inputs[i],
        storages,
        roleModel.readAllowed,
        'read'
      ));
    }
    return result;
  }
  return [];
}

function checkOutputs (props) {
  const {
    dataStorages,
    outputs
  } = props;
  if (outputs && dataStorages && dataStorages.loaded) {
    const storages = (dataStorages.value || []).map(s => ({
      name: s.name,
      path: s.pathMask,
      pathMask: getStoragePathMask(s),
      mask: s.mask
    }));
    const result = [];
    for (let i = 0; i < (outputs || []).length; i++) {
      result.push(...checkPermission(
        outputs[i],
        storages,
        roleModel.writeAllowed,
        'write'
      ));
    }
    return result;
  }
  return [];
}

export async function performAsyncCheck (props, state = undefined) {
  const {
    dataStorages,
    dockerRegistries
  } = props;
  await Promise.all([
    dataStorages ? dataStorages.fetchIfNeededOrWait() : null,
    dockerRegistries ? dockerRegistries.fetchIfNeededOrWait() : null]
    .filter(Boolean)
  );
  let {
    dockerImageErrors,
    inputsErrors,
    outputsErrors,
    dockerImageChecked,
    inputsChecked,
    outputsChecked
  } = state || {};
  if (!dockerImageErrors) {
    dockerImageErrors = [];
  }
  if (!inputsErrors) {
    inputsErrors = [];
  }
  if (!outputsErrors) {
    outputsErrors = [];
  }
  if (!dockerImageChecked || props.dockerImage !== dockerImageChecked) {
    dockerImageErrors = checkDockerImage(props).filter(Boolean);
    dockerImageChecked = props.dockerImage;
  }
  if (!inputsChecked || parametersChanged(props.inputs, inputsChecked)) {
    inputsErrors = checkInputs(props).filter(Boolean);
    inputsChecked = props.inputs;
  }
  if (!outputsChecked || parametersChanged(props.outputs, outputsChecked)) {
    outputsErrors = checkOutputs(props).filter(Boolean);
    outputsChecked = props.outputs;
  }
  const errors = [
    ...dockerImageErrors,
    ...inputsErrors,
    ...outputsErrors
  ];
  return {
    errors,
    state: {
      dockerImageErrors,
      inputsErrors,
      outputsErrors,
      dockerImageChecked,
      inputsChecked,
      outputsChecked,
      errors
    }
  };
}

export function PermissionErrorsTitle () {
  return (
    <Row style={{fontWeight: 'bold'}}>
      <Icon type="exclamation-circle-o" style={{marginRight: 5, color: 'orange'}} />
      <span>Permission issues</span>
    </Row>
  );
}

export function PermissionErrors ({errors}) {
  return (
    <ul style={{listStyle: 'disc', paddingLeft: 20}}>
      {errors.map((error, index) => (
        <li key={index}>
          {error}
        </li>
      ))}
    </ul>
  );
}

@inject('dataStorages', 'dockerRegistries')
@observer
class SubmitButton extends React.Component {
  static propTypes = {
    dockerImage: PropTypes.string,
    id: PropTypes.string,
    inputs: PropTypes.object,
    onClick: PropTypes.func,
    outputs: PropTypes.object,
    type: PropTypes.string,
    htmlType: PropTypes.string,
    size: PropTypes.string,
    style: PropTypes.object
  };

  state = {
    errors: []
  };

  asyncCheckIdentifier = 0;

  componentDidMount () {
    return this.performAsyncCheck(this.props);
  }

  componentWillReceiveProps (nextProps, nextContext) {
    return this.performAsyncCheck(nextProps, this.props);
  }

  performAsyncCheck = async (nextProps) => {
    this.asyncCheckIdentifier += 1;
    const identifier = this.asyncCheckIdentifier;
    const {state} = await performAsyncCheck(nextProps, this.state);
    if (identifier === this.asyncCheckIdentifier) {
      this.setState(state);
    }
  };

  render () {
    const {
      children,
      dataStorages,
      dockerRegistries,
      id,
      htmlType,
      onClick,
      size,
      style,
      type
    } = this.props;
    const {
      errors
    } = this.state;
    const pending = (dataStorages.pending && !dataStorages.loaded) ||
      (dockerRegistries.pending && !dockerRegistries.loaded);
    const submitButton = (
      <Button
        id={id}
        type={type}
        htmlType={htmlType}
        style={style}
        disabled={pending || errors.length > 0}
        onClick={onClick}
        size={size}
      >
        {errors.length > 0 ? <Icon type="exclamation-circle" /> : null}
        {children}
      </Button>
    );
    if (errors.length === 0) {
      return submitButton;
    }
    return (
      <Popover
        title={<PermissionErrorsTitle />}
        content={<PermissionErrors errors={errors} />}
        placement="bottomRight"
      >
        {submitButton}
      </Popover>
    );
  }
}

export {SubmitButton};
