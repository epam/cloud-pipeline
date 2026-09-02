/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {
  Button,
  Icon,
  LocaleProvider,
  Modal,
  Select
} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
import classNames from 'classnames';
import {Provider, inject, observer} from 'mobx-react';
import {isObservableArray} from 'mobx';
import PipelineRunInfo from '../../../models/pipelines/PipelineRunInfo';
import AllowedInstanceTypes from '../../../models/utils/AllowedInstanceTypes';
import {names} from '../../../models/utils/ContextualPreference';
import preferences from '../../../models/preferences/PreferencesLoad';
import RunDisplayName from './run-display-name';
import {getSelectOptions} from '../../special/instance-type-info';
import {FallbackInstanceTypesInput} from '../../special/fallback-instance-types-input';
import {
  RUN_CAPABILITIES,
  RUN_CAPABILITIES_PARAMETERS
} from '../../pipelines/launch/form/utilities/parameters';
import styles from './resume-confirmation.css';

const resumeRunDialogContainer = document.createElement('div');
document.body.appendChild(resumeRunDialogContainer);

async function getRun (id) {
  const runInfo = new PipelineRunInfo(id);
  await runInfo.fetch();
  if (runInfo.loaded) {
    return runInfo.value || {};
  }
  return {};
}

function toPlainArray (value) {
  if (Array.isArray(value) || isObservableArray(value)) {
    return value.slice();
  }
  return undefined;
}

const disableHyperThreadingParameter = RUN_CAPABILITIES_PARAMETERS[
  RUN_CAPABILITIES.disableHyperThreading
];

function isHyperThreadingDisabled (run) {
  const parameters = (run && run.pipelineRunParameters) || [];
  const parameter = parameters.find(
    (p) => p && p.name === disableHyperThreadingParameter
  );
  return !!parameter && /^true$/i.test(parameter.value || '');
}

function sortInstanceTypes (instanceTypes = []) {
  const deduped = [];
  for (let i = 0; i < instanceTypes.length; i += 1) {
    const instanceType = instanceTypes[i];
    if (deduped.filter((t) => t.name === instanceType.name).length === 0) {
      deduped.push(instanceType);
    }
  }
  return deduped.sort((typeA, typeB) => {
    const vcpuCompared = (typeA.vcpu || 0) - (typeB.vcpu || 0);
    if (vcpuCompared !== 0) {
      return vcpuCompared;
    }
    const familyA = typeA.instanceFamily || '';
    const familyB = typeB.instanceFamily || '';
    if (familyA > familyB) return 1;
    if (familyA < familyB) return -1;
    return 0;
  });
}

/**
 * @typedef {Object} ResumeConfirmationOptions
 * @property {string|number} id
 * @property {*} [run]
 * @property {string|React.ReactNode} [title]
 */

/**
 * @typedef {Object} ResumeConfirmationPayload
 * @property {string} [instanceType]
 * @property {string[]} [fallbackInstanceTypes]
 */

class ConfirmResumeContainer extends React.Component {
  state = {
    run: undefined,
    title: undefined,
    visible: false,
    resolver: undefined,
    allowedInstanceTypes: undefined,
    instanceType: undefined,
    fallbackInstanceTypes: undefined
  };

  componentDidMount () {
    const {onRegisterCallback} = this.props;
    if (typeof onRegisterCallback === 'function') {
      onRegisterCallback(this.confirmResume.bind(this));
    }
  }

  /**
   * @param {ResumeConfirmationOptions} options
   * @returns {Promise<false|ResumeConfirmationPayload>}
   */
  check = async (options = {}) => {
    let resolved = false;
    const {
      id,
      run: providedRun,
      title
    } = options;
    const applyRun = (run) => {
      const instance = run && run.instance ? run.instance : {};
      const allowedInstanceTypes = new AllowedInstanceTypes({
        regionId: instance.cloudRegionId,
        spot: instance.spot
      });
      this.setState({
        run,
        allowedInstanceTypes,
        instanceType: instance.nodeType,
        fallbackInstanceTypes: toPlainArray(instance.fallbackInstanceTypes)
      });
    };
    if (providedRun) {
      applyRun(providedRun);
    } else {
      getRun(id)
        .then((runInfo) => {
          if (!resolved) {
            applyRun(runInfo);
          }
        });
    }
    return new Promise((resolve) => {
      const resolver = (result) => {
        resolved = true;
        this.setState({
          visible: false,
          resolver: undefined,
          allowedInstanceTypes: undefined
        }, () => resolve(result));
      };
      this.setState({
        resolver,
        runId: id,
        visible: true,
        title
      });
    });
  };

  /**
   * @param {ResumeConfirmationOptions} options
   * @returns {Promise<false|ResumeConfirmationPayload>}
   */
  async confirmResume (options) {
    if (this.previousPromise) {
      await this.previousPromise;
    }
    this.previousPromise = undefined;
    const {id} = options || {};
    if (!id) {
      return false;
    }
    this.previousPromise = new Promise((resolve) =>
      this.check(options)
        .then(resolve)
        .catch((error) => {
          console.warn(`Error preparing resume dialog for run #${id}: ${error.message}`);
          resolve(false);
        })
    );
    return this.previousPromise;
  }

  onCancel = () => {
    const {resolver} = this.state;
    if (typeof resolver === 'function') {
      resolver(false);
    }
  };

  onConfirm = () => {
    const {
      resolver,
      instanceType,
      fallbackInstanceTypes
    } = this.state;
    if (typeof resolver !== 'function') {
      return;
    }
    const fallbackDisabled = (preferences.maximumFallbackInstanceTypes || 0) <= 0;
    const payload = {};
    if (instanceType) {
      payload.instanceType = instanceType;
    }
    if (!fallbackDisabled) {
      payload.fallbackInstanceTypes = toPlainArray(fallbackInstanceTypes) || [];
    }
    resolver(payload);
  };

  onInstanceTypeChange = (value) => {
    this.setState({instanceType: value});
  };

  onFallbackInstanceTypesChange = (value) => {
    this.setState({fallbackInstanceTypes: value});
  };

  renderTitle = () => {
    const {run, title} = this.state;
    if (title) {
      return title;
    }
    return (
      <span>
        Do you want to resume
        <RunDisplayName
          run={run}
          style={{marginLeft: 5}}
        />
        ?
      </span>
    );
  };

  render () {
    const {
      visible,
      allowedInstanceTypes,
      instanceType,
      fallbackInstanceTypes,
      run
    } = this.state;
    return (
      <ConfirmResume
        visible={visible}
        title={this.renderTitle()}
        run={run}
        allowedInstanceTypes={allowedInstanceTypes}
        instanceType={instanceType}
        fallbackInstanceTypes={fallbackInstanceTypes}
        onInstanceTypeChange={this.onInstanceTypeChange}
        onFallbackInstanceTypesChange={this.onFallbackInstanceTypesChange}
        onCancel={this.onCancel}
        onConfirm={this.onConfirm}
      />
    );
  }
}

ConfirmResumeContainer.propTypes = {
  onRegisterCallback: PropTypes.func
};

function ConfirmResumeModal (props) {
  const {
    title,
    visible,
    run,
    allowedInstanceTypes,
    instanceType,
    fallbackInstanceTypes,
    onInstanceTypeChange,
    onFallbackInstanceTypesChange,
    onCancel,
    onConfirm,
    preferences: prefs
  } = props;
  const pending = !!allowedInstanceTypes && allowedInstanceTypes.pending;
  const instanceTypesList = (() => {
    if (!allowedInstanceTypes || !allowedInstanceTypes.loaded || !allowedInstanceTypes.value) {
      return [];
    }
    const isPipelineRun = !!(run && run.pipelineId);
    const key = isPipelineRun
      ? names.allowedInstanceTypes
      : names.allowedToolInstanceTypes;
    return sortInstanceTypes(allowedInstanceTypes.value[key] || []);
  })();
  const hyperThreadingDisabled = isHyperThreadingDisabled(run);
  const fallbackDisabled = !prefs || (prefs.maximumFallbackInstanceTypes || 0) <= 0;
  return (
    <Modal
      title={false}
      closable={false}
      maskClosable={false}
      visible={visible}
      onCancel={onCancel}
      footer={false}
      width={500}
    >
      <div className={styles.body}>
        <div className={styles.title}>
          <Icon
            type="question-circle"
            className={
              classNames(
                'cp-warning',
                styles.icon
              )
            }
          />
          {title}
        </div>
        <div className={styles.form}>
          <div className={styles.formItem}>
            <span className={styles.formItemLabel}>Node type:</span>
            <Select
              showSearch
              style={{width: '100%'}}
              value={instanceType}
              placeholder="Node type"
              optionFilterProp="children"
              disabled={pending}
              onChange={onInstanceTypeChange}
              filterOption={(input, option) =>
                (option.props.searchValue || option.props.value || '')
                  .toLowerCase()
                  .indexOf(input.toLowerCase()) >= 0
              }
            >
              {
                getSelectOptions(
                  instanceTypesList,
                  {
                    hyperThreadingDisabled,
                    displayRegion: allowedInstanceTypes && allowedInstanceTypes.regionsMerged,
                    preferences: prefs,
                    showReservationTag: true
                  }
                )
              }
            </Select>
          </div>
          {
            !fallbackDisabled && (
              <div className={styles.formItem}>
                <span className={styles.formItemLabel}>Fallback node types:</span>
                <FallbackInstanceTypesInput
                  disabled={pending}
                  value={fallbackInstanceTypes}
                  onChange={onFallbackInstanceTypesChange}
                  instanceTypes={instanceTypesList}
                  displayRegion={allowedInstanceTypes && allowedInstanceTypes.regionsMerged}
                  hyperThreadingDisabled={hyperThreadingDisabled}
                  showReservationTag
                />
              </div>
            )
          }
        </div>
      </div>
      <div className={styles.footer}>
        <Button
          className={styles.action}
          id="cancel-resume-run"
          onClick={onCancel}
        >
          CANCEL
        </Button>
        <Button
          className={styles.action}
          id="confirm-resume-run"
          type="primary"
          disabled={pending}
          onClick={onConfirm}
        >
          {pending && (<Icon type="loading" />)}
          RESUME
        </Button>
      </div>
    </Modal>
  );
}

ConfirmResumeModal.propTypes = {
  title: PropTypes.node,
  visible: PropTypes.bool,
  run: PropTypes.object,
  allowedInstanceTypes: PropTypes.object,
  instanceType: PropTypes.string,
  fallbackInstanceTypes: PropTypes.array,
  onInstanceTypeChange: PropTypes.func,
  onFallbackInstanceTypesChange: PropTypes.func,
  onCancel: PropTypes.func,
  onConfirm: PropTypes.func
};

const ConfirmResume = inject('preferences')(observer(ConfirmResumeModal));

let confirmResumeCallback;
function registerCallback (callback) {
  confirmResumeCallback = callback;
}

ReactDOM.render(
  (
    <LocaleProvider locale={enUS}>
      <Provider preferences={preferences}>
        <ConfirmResumeContainer onRegisterCallback={registerCallback} />
      </Provider>
    </LocaleProvider>
  ),
  resumeRunDialogContainer
);

/**
 * @param {ResumeConfirmationOptions} options
 * @returns {Promise<false|ResumeConfirmationPayload>}
 */
export default function confirmResume (options) {
  return confirmResumeCallback(options);
}
