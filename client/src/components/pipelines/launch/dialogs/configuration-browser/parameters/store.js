/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {action, computed, observable} from 'mobx';
import {Provider, inject, observer} from 'mobx-react';
import preferences from '../../../../../../models/preferences/PreferencesLoad';
import runDefaultParameters from '../../../../../../models/pipelines/PipelineRunDefaultParameters';
import userInfo from '../../../../../../models/user/WhoAmI';
import {getAllSkippedSystemParametersList} from '../../../form/utilities/launch-cluster';
import {
  applyCapabilities,
  getEnabledCapabilities,
  isCapability
} from '../../../form/utilities/run-capabilities';
import {
  buildParameters,
  isSystemParameter,
  parameterIsVisible,
  parametersSetAreEqual,
  parseParameters,
  isSystemParameterRestrictedByRole, validateParameters
} from './utilities';
import * as parameterUtilities from '../../../form/utilities/parameter-utilities';
import {CP_CAP_LIMIT_MOUNTS} from '../../../form/utilities/parameters';

class ParametersStore {
  static Events = {
    onChange: 'onChange'
  };
  handle = 0;

  @observable pending = false;
  @observable error = false;
  @observable valid = true;
  @observable parameters = [];
  @observable entityTypeFields = [];
  @observable projectFields = [];
  @observable capabilities = [];
  @observable limitMounts;
  eventListeners = [];

  @computed
  get visibleParameters () {
    return this.parameters
      .filter(parameter => !parameter.skipped &&
        !parameter.capability &&
        parameterIsVisible(parameter, this.parameters)
      );
  }

  get systemParameters () {
    return this.visibleParameters
      .filter(parameter => parameter.system);
  }

  get nonSystemParameters () {
    return this.visibleParameters
      .filter(parameter => !parameter.system);
  }

  constructor (parameters = {}) {
    (this.initialize)(parameters);
  }

  update (newParameters) {
    if (!parametersSetAreEqual(newParameters, this.original)) {
      this.original = {...(newParameters || {})};
      (this.initialize)(newParameters);
    }
  }

  updateEntityTypeFields (newEntityTypeFields = []) {
    this.entityTypeFields = newEntityTypeFields.slice();
  }

  updateProjectFields (newProjectFields = []) {
    this.projectFields = newProjectFields.slice();
  }

  @action
  async initialize (parameters = {}) {
    this.handle = this.handle + 1;
    const handle = this.handle;
    this.pending = true;
    this.error = undefined;
    this.valid = true;
    try {
      await Promise.all([
        preferences.fetchIfNeededOrWait(),
        runDefaultParameters.fetchIfNeededOrWait(),
        userInfo.fetchIfNeededOrWait()
      ]);
      if (handle !== this.handle) {
        // outdated
        return;
      }
      const skipped = new Set(getAllSkippedSystemParametersList(preferences));
      const parameterIsSkipped = parameter => !parameter ||
        skipped.has(parameter.name) ||
        isCapability(parameter.name, preferences);
      this.capabilities = getEnabledCapabilities(parameters);
      this.parameters = parseParameters(parameters)
        .map(o => {
          const system = isSystemParameter(o.name, runDefaultParameters);
          return {
            ...o,
            skipped: parameterIsSkipped(o),
            capability: isCapability(o.name, preferences),
            limitMounts: CP_CAP_LIMIT_MOUNTS === o.name,
            system: !!system,
            description: o.description || (system ? system.description : undefined),
            restricted: !!system && isSystemParameterRestrictedByRole(
              o.name,
              runDefaultParameters,
              userInfo.value
            )
          };
        });
      const limitMountsParameter = this.parameters.find(o => o.limitMounts);
      this.limitMounts = limitMountsParameter
        ? limitMountsParameter.value
        : undefined;
    } catch (e) {
      this.error = e.message;
    } finally {
      this.pending = false;
      this.onChange();
    }
  }

  onChange = () => {
    this.validate();
    this.emit(ParametersStore.Events.onChange, this);
  };

  validate = () => {
    validateParameters(this.parameters, preferences);
    this.valid = !this.parameters.some(parameter => parameter.nameError || parameter.valueError);
  };

  onAddParameter = (...parameter) => {
    this.parameters.push(...parameter);
    this.onChange();
  };

  onChangeParameter = (parameter) => (newParameter) => {
    const index = this.parameters.indexOf(parameter);
    if (index < 0) {
      return;
    }
    const newParameters = this.parameters.slice();
    newParameters.splice(index, 1, newParameter);
    this.parameters = newParameters;
    this.onChange();
  };

  onRemoveParameter = (parameter) => () => {
    const index = this.parameters.indexOf(parameter);
    if (index < 0) {
      return;
    }
    const newParameters = this.parameters.slice();
    newParameters.splice(index, 1);
    this.parameters = newParameters;
    this.onChange();
  };

  onChangeCapabilities = (capabilities = []) => {
    this.capabilities = capabilities;
    this.onChange();
  }

  onChangeLimitMounts = (limitMounts) => {
    this.limitMounts = limitMounts;
    this.onChange();
  }

  getParameterEnumeration = (parameter) => {
    const builtParameters = buildParameters(this.parameters);
    let enumeration = parameterUtilities.parseEnumeration(parameter);
    if (enumeration && typeof enumeration.filter === 'function') {
      return enumeration
        .filter(o => o.isVisible(builtParameters))
        .map(o => o.name);
    }
    return undefined;
  };

  getPayload = () => {
    try {
      this.validate();
      const filtered = this.parameters.filter(o => !o.capability && !o.limitMounts);
      const params = applyCapabilities(
        buildParameters(filtered, true),
        this.capabilities,
        preferences
      );
      if (this.limitMounts) {
        params[CP_CAP_LIMIT_MOUNTS] = {
          value: this.limitMounts
        };
      }
      return params;
    } catch (e) {
      console.warn(e.message);
      return undefined;
    }
  };

  addEventListener = (event, listener) => {
    this.removeEventListener(event, listener);
    this.eventListeners.push({event, listener});
  }

  removeEventListener = (event, listener) => {
    if (event && listener) {
      this.eventListeners = this.eventListeners
        .filter(o => o.event !== event && o.listener !== listener);
    } else if (event) {
      this.eventListeners = this.eventListeners
        .filter(o => o.event !== event);
    }
  };

  emit = (event, ...payload) => {
    this.eventListeners
      .filter(o => o.event === event)
      .map(o => o.listener)
      .forEach(listener => {
        if (typeof listener === 'function') {
          listener(...payload);
        }
      });
  };
}

class ParametersProvider extends React.Component {
  @observable store = new ParametersStore();
  componentDidMount () {
    this.store.addEventListener(ParametersStore.Events.onChange, this.onChange);
    this.store.update(this.props.parameters);
  }
  componentDidUpdate (prevProps, prevState, snapshot) {
    this.store.update(this.props.parameters);
    this.store.updateEntityTypeFields(this.props.entityFields);
    this.store.updateProjectFields(this.props.projectMetadataFields);
  }
  componentWillUnmount () {
    this.store.removeEventListener(ParametersStore.Events.onChange, this.onChange);
  }
  onChange = () => {
    const {
      onChange
    } = this.props;
    if (typeof onChange === 'function') {
      const payload = this.store.getPayload();
      onChange(payload, this.store.valid);
    }
  };
  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <Provider parametersStore={this.store}>
        <div
          className={className}
          style={style}
        >
          {this.props.children}
        </div>
      </Provider>
    );
  }
}

ParametersProvider.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameters: PropTypes.object,
  onChange: PropTypes.func,
  entityFields: PropTypes.array,
  projectMetadataFields: PropTypes.array
};

const injectParametersStore = (WrappedComponent) => inject('parametersStore')(WrappedComponent);

export {injectParametersStore};
export default observer(ParametersProvider);
