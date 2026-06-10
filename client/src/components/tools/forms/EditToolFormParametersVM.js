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

import {makeAutoObservable, observable, runInAction} from 'mobx';

/**
 * View model for {@link EditToolFormParameters}.
 *
 * Encapsulates parameter list state, validation, dialog visibility and the
 * pieces of logic previously hosted on the class component. The owning
 * component (or its future parent VM) calls {@link setProps} on each render
 * to keep injected/observed props in sync without coupling the VM to React
 * lifecycles.
 */
class EditToolFormParametersVM {
  parameters = [];
  validation = [];
  bucketBrowserParameter = null;
  systemParameterBrowserVisible = false;

  /**
   * Component props as a shallow (ref) observable so VM consumers re-render
   * when the parent passes new injected stores / values.
   */
  props = {};

  constructor() {
    makeAutoObservable(this, {
      props: observable.ref,
    });
  }

  setProps(props) {
    this.props = props || {};
  }

  get skippedSystemParameters() {
    const skipped = this.props.skippedSystemParameters;
    if (skipped && skipped.length) {
      return skipped;
    }
    return [];
  }

  get authenticatedUserRolesNames() {
    const info = this.props.authenticatedUserInfo;
    if (!info || !info.loaded) {
      return [];
    }
    const {roles = []} = info.value || {};
    return roles.map((r) => r.name);
  }

  get isAdmin() {
    const info = this.props.authenticatedUserInfo;
    if (!info || !info.loaded) {
      return false;
    }
    const {admin} = info.value || {};
    return admin;
  }

  get sectionName() {
    return this.props.isSystemParameters ? 'systemParameters' : 'parameters';
  }

  get isValid() {
    return this.validation.filter((v) => !!v.error || !!v.errorValue).length === 0;
  }

  get modified() {
    const propsValue = (this.props.value || []).filter(this.filterPropsParameter);
    const currentValue = this.parameters || [];
    if (propsValue.length !== currentValue.length) {
      return true;
    }
    for (let i = 0; i < propsValue.length; i++) {
      const propsValueItem = propsValue[i];
      const currentValueItem = currentValue[i];
      if (
        propsValueItem.name !== currentValueItem.name ||
        propsValueItem.value !== currentValueItem.value
      ) {
        return true;
      }
    }
    return false;
  }

  filterPropsParameter = (parameter) => {
    const {testSkipParameter} = this.props;
    return testSkipParameter ? !testSkipParameter(parameter.name) : true;
  };

  isSystemParameter = (parameter) => {
    const runDefaults = this.props.runDefaultParameters;
    if (runDefaults && runDefaults.loaded && parameter && parameter.name) {
      return (
        (runDefaults.value || []).filter(
          (p) => p.name.toUpperCase() === (parameter.name || '').toUpperCase(),
        ).length > 0
      );
    }
    return false;
  };

  isSystemParameterRestrictedByRole = (parameter) => {
    if (parameter && this.isSystemParameter(parameter) && !this.isAdmin) {
      const runDefaults = this.props.runDefaultParameters;
      const [systemParam] = (runDefaults && runDefaults.value ? runDefaults.value : []).filter(
        (p) => p.name.toUpperCase() === (parameter.name || '').toUpperCase(),
      );
      if (systemParam && systemParam.roles && systemParam.roles.length > 0) {
        return !systemParam.roles.some((roleName) =>
          this.authenticatedUserRolesNames.includes(roleName),
        );
      }
    }
    return false;
  };

  /**
   * Whether a given parameter should be visible in the rendered list. System
   * parameters can be hidden through `getSystemParameterDisabledState`
   * supplied by the parent.
   */
  shouldRenderParameter = (parameter) => {
    const {isSystemParameters, getSystemParameterDisabledState} = this.props;
    if (
      isSystemParameters &&
      getSystemParameterDisabledState &&
      getSystemParameterDisabledState(parameter.name || '')
    ) {
      return false;
    }
    return true;
  };

  validate = (parameters) => {
    const list = parameters || this.parameters;
    const validation = list.map(() => ({}));
    const {isSystemParameters} = this.props;
    for (let i = 0; i < list.length; i++) {
      if (!list[i].name) {
        validation[i].error = 'Parameter name is required';
      } else if (
        !isSystemParameters &&
        this.isSystemParameterRestrictedByRole({name: list[i].name || ''})
      ) {
        validation[i].error = 'This parameter is not allowed for use';
      } else if (!isSystemParameters && this.isSystemParameter({name: list[i].name || ''})) {
        validation[i].error = 'Parameter name is reserved';
      } else if (
        list
          .map((p) => (p.name || '').toLowerCase())
          .filter((n) => n === (list[i].name || '').toLowerCase()).length > 1
      ) {
        validation[i].error = 'Parameter name should be unique';
      } else if (
        isSystemParameters &&
        (((list[i].type || '').toLowerCase() === 'boolean' && list[i].value === undefined) ||
          ((list[i].type || '').toLowerCase() !== 'boolean' && !list[i].value))
      ) {
        validation[i].errorValue = 'Parameter value is required';
      }
    }
    return validation;
  };

  reset = () => {
    const mapParameter = (p, index) => ({
      id: index,
      name: p.name,
      value: p.value,
      type: p.type,
      initial: true,
    });
    runInAction(() => {
      this.parameters = (this.props.value || [])
        .filter(this.filterPropsParameter)
        .map(mapParameter);
      this.validation = this.parameters.map(() => ({}));
    });
  };

  getValues = () => {
    const mapParameter = ({id, ...param}) => ({...param});
    return (this.parameters || []).map(mapParameter);
  };

  addParameter = (parameter) => {
    runInAction(() => {
      const id = Math.max(0, ...this.parameters.map((o) => o.id)) + 1;
      this.parameters.push({
        ...parameter,
        id,
      });
      this.validation = this.validate(this.parameters);
    });
  };

  updateParameter = (index, property, value) => {
    if (index < 0 || index >= this.parameters.length) {
      return;
    }
    runInAction(() => {
      const next = this.parameters.slice();
      const target = {...next[index]};
      if ((target.type || '').toLowerCase() === 'boolean' && property === 'value') {
        target[property] = value;
      } else {
        target[property] = value;
      }
      next[index] = target;
      this.parameters = next;
      this.validation = this.validate(this.parameters);
    });
  };

  removeParameter = (index) => {
    if (index < 0 || index >= this.parameters.length) {
      return;
    }
    runInAction(() => {
      const next = this.parameters.slice();
      next.splice(index, 1);
      this.parameters = next;
      this.validation = this.validate(this.parameters);
    });
  };

  openBucketBrowser = (index) => {
    this.bucketBrowserParameter = index;
  };

  closeBucketBrowser = () => {
    this.bucketBrowserParameter = null;
  };

  selectBucketPath = (path) => {
    if (this.bucketBrowserParameter !== null) {
      const index = this.bucketBrowserParameter;
      runInAction(() => {
        const next = this.parameters.slice();
        if (next[index]) {
          next[index] = {...next[index], value: path};
        }
        this.parameters = next;
        this.bucketBrowserParameter = null;
      });
    }
  };

  openSystemParameterBrowser = () => {
    this.systemParameterBrowserVisible = true;
  };

  closeSystemParameterBrowser = () => {
    this.systemParameterBrowserVisible = false;
  };

  addSystemParameters = (parameters) => {
    runInAction(() => {
      const next = this.parameters.slice();
      let id = Math.max(0, ...next.map((o) => o.id)) + 1;
      (parameters || []).forEach((param) => {
        next.push({
          id,
          name: param.name,
          type: param.type,
          value: param.defaultValue,
        });
        id += 1;
      });
      this.parameters = next;
      this.systemParameterBrowserVisible = false;
      this.validation = this.validate(this.parameters);
    });
  };
}

export default EditToolFormParametersVM;
