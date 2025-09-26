/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {action, computed, observable} from 'mobx';
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Collapse,
  Form,
  Icon,
  Input,
  message,
  Modal,
  Popover,
  Row,
  Select,
  Spin
} from 'antd';
import styles from './LaunchPipelineForm.css';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import BucketBrowser from './../dialogs/BucketBrowser';
import PipelineBrowser from './../dialogs/PipelineBrowser';
import DockerImageInput from './DockerImageInput';
import MetadataBrowser from './../dialogs/MetadataBrowser';
import CodeEditor from '../../../special/CodeEditor';
import JobEstimatedPriceInfo from '../../../special/job-estimated-price-info';
import AWSRegionTag from '../../../special/AWSRegionTag';
import {LimitMountsInput} from './LimitMountsInput';
import RunName from '../../../runs/run-name';

import PipelineRunEstimatedPrice from '../../../../models/pipelines/PipelineRunEstimatedPrice';
import FolderProject from '../../../../models/folders/FolderProject';
import MetadataEntityFields from '../../../../models/folderMetadata/MetadataEntityFields';
import ToolDefaultCommand from '../../../../models/tools/ToolDefaultCommand';
import configurationsRequest from '../../../../models/configuration/Configurations';

import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';

import hints from './hints';
import FireCloudMethodSnapshotConfigurationsRequest
from '../../../../models/firecloud/FireCloudMethodSnapshotConfigurations';
import FireCloudMethodParameters
from '../../../../models/firecloud/FireCloudMethodParameters';
import LoadingView from '../../../special/LoadingView';
import {getSpotTypeName} from '../../../special/spot-instance-names';
import DTSClusterInfo from '../../../../models/dts/DTSClusterInfo';
import {
  autoScaledClusterEnabled,
  hybridAutoScaledClusterEnabled,
  ConfigureClusterDialog,
  gridEngineEnabled,
  sparkEnabled,
  slurmEnabled,
  kubeEnabled,
  getAutoScaledPriceTypeValue,
  applyChildNodeInstanceParameters,
  parseChildNodeInstanceConfiguration
} from './utilities/launch-cluster';
import checkModifiedState from './utilities/launch-form-modified-state';
import {
  ADVANCED,
  EXEC_ENVIRONMENT,
  PARAMETERS,
  SYSTEM_PARAMETERS
} from './utilities/launch-form-sections';
import * as prettyUrlGenerator from './utilities/pretty-url';
import * as parameterUtilities from './utilities/parameter-utilities';
import RunSchedulingList from '../../../runs/run-scheduling/run-sheduling-list';
import pipelinesEquals from './utilities/pipelines-equals';
import LaunchCommand from './utilities/launch-command';
import {names} from '../../../../models/utils/ContextualPreference';
import {
  SubmitButton,
  getInputPaths,
  getOutputPaths
} from '../../../runs/actions';
import LoadToolVersionSettings from '../../../../models/tools/LoadToolVersionSettings';
import ServerlessAPIButton from '../../../special/serverless-api-button';
import RunCapabilities, {
  addCapability,
  applyCapabilities,
  correctRequiredCapabilities,
  getEnabledCapabilities,
  getUserCapabilities,
  hasPlatformSpecificCapabilities,
  RUN_CAPABILITIES,
  RUN_CAPABILITIES_MODE
} from './utilities/run-capabilities';
import {
  CP_CAP_LIMIT_MOUNTS,
  CP_CAP_SGE,
  CP_CAP_SPARK,
  CP_CAP_SLURM,
  CP_CAP_KUBE,
  CP_CAP_DIND_CONTAINER,
  CP_CAP_SYSTEMD_CONTAINER,
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_AUTOSCALE_HYBRID,
  CP_CAP_AUTOSCALE_PRICE_TYPE,
  CP_CAP_RESCHEDULE_RUN
} from './utilities/parameters';
import OOMCheck from './utilities/oom-check';
import AllowedInstancesCountWarning from
'./utilities/allowed-instances-count-warning';
import HostedAppConfiguration from '../dialogs/HostedAppConfiguration';
import JobNotifications from '../dialogs/job-notifications';
import {withCurrentUserAttributes} from '../../../../utils/current-user-attributes';
import {
  applyParameters as applyGPUScalingParameters,
  readGPUScalingPreference
} from './utilities/enable-gpu-scaling';
import {mapObservableNotification} from '../dialogs/job-notifications/job-notification';
import RescheduleRunControl, {
  rescheduleRunParameterValue
} from './utilities/reschedule-run-control';
import {getSelectOptions} from '../../../special/instance-type-info';
import {
  correctLimitMountsParameterValue
} from '../../../../utils/limit-mounts/get-limit-mounts-storages';
import PipelineVersionPicker from './pipeline-version-picker';
import {generateContinueRunParameters} from '../../../runs/actions/continue-run';
import {
  getFsConfigFromParameters,
  getParametersFromFsConfig
} from './utilities/configure-fs/utilities';
import CustomTagsControl from './components/custom-tags/control';
import UploadParametersButton from './components/upload-parameters-button';
import ConfigurePlugins from '../../../plugins/configure';
import {
  filterVisibleTagsSync,
  getUserTagsValidationResult,
  getVisibleUserTags
} from '../../../runs/run-tags/utilities';
import Parameters from './parameters/parameters';
import AddParameterButton from './parameters/add-parameter-button';
import {getParameterKeyClassName} from './parameters/utilities';
import ParametersPayloadSelector from './parameters/payload/selector';
import ReservationParameters from './components/reservation-parameters';
import {
  buildLaunchParametersFromReservationParameters,
  findReservationParameterConfig,
  readReservationParameters
} from './components/reservation-parameters/utilities';
import Markdown from '../../../special/markdown';
import DataStorageItemSize from '../../../../models/dataStorage/DataStorageItemSize';

const FormItem = Form.Item;
const RUN_SELECTED_KEY = 'run selected';
const RUN_CLUSTER_KEY = 'run cluster';

const CLOUD_PLATFORM_ENVIRONMENT = 'CLOUD_PLATFORM';
const FIRE_CLOUD_ENVIRONMENT = 'FIRECLOUD';
const DTS_ENVIRONMENT = 'DTS';

const VALIDATION_DEBOUNCE_TIMEOUT = 700;

function getFormItemClassName (rootClass, key) {
  if (key) {
    return `${rootClass} ${key.replace(/\./g, '_')}`;
  }
  return rootClass;
}

@inject(
  'runDefaultParameters',
  'googleApi',
  'awsRegions',
  'dtsList',
  'preferences',
  'dockerRegistries',
  'dataStorageAvailable',
  'uiNavigation'
)
@localization.localizedComponent
@roleModel.authenticationInfo
@withCurrentUserAttributes()
@observer
class LaunchPipelineForm extends localization.LocalizedReactComponent {
  localizedStringWithSpotDictionaryFn = (key) => {
    return this.localizedString(
      key,
      [
        {key: 'spot', value: getSpotTypeName(true, this.currentCloudRegionProvider)},
        {key: 'on-demand', value: getSpotTypeName(false, this.currentCloudRegionProvider)},
        {key: 'on demand', value: getSpotTypeName(false, this.currentCloudRegionProvider)}
      ]
    );
  };

  isDts = (props = this.props) => {
    if (!this.props.detached) {
      return false;
    }
    const [currentConfiguration] = props.configurations
      .filter(config => config.name === props.currentConfigurationName);

    return currentConfiguration && currentConfiguration.executionEnvironment === DTS_ENVIRONMENT;
  };

  currentUserName = () => {
    if (!this.props.authenticatedUserInfo.loaded) {
      return undefined;
    }
    return this.props.authenticatedUserInfo.value.userName;
  };

  friendlyUrlAvailable = () => {
    return this.isAdmin || this.isAdvancedUser;
  };

  static propTypes = {
    pending: PropTypes.bool,
    pipeline: PropTypes.shape(),
    pipelines: PropTypes.array,
    version: PropTypes.string,
    pipelineConfiguration: PropTypes.string,
    allowedInstanceTypes: PropTypes.object,
    instanceTypes: PropTypes.string,
    toolInstanceTypes: PropTypes.string,
    parameters: PropTypes.shape(),
    configurations: PropTypes.array,
    onLaunch: PropTypes.func,
    alerts: PropTypes.arrayOf(PropTypes.shape({
      message: PropTypes.string,
      type: PropTypes.string
    })),
    editConfigurationMode: PropTypes.bool,
    onConfigurationChanged: PropTypes.func,
    onPipelineChanged: PropTypes.func,
    currentConfigurationName: PropTypes.string,
    currentConfigurationIsDefault: PropTypes.bool,
    onSetConfigurationAsDefault: PropTypes.func,
    onRemoveConfiguration: PropTypes.func,
    readOnly: PropTypes.bool,
    canExecute: PropTypes.bool,
    canRunCluster: PropTypes.bool,
    canRemove: PropTypes.bool,
    detached: PropTypes.bool,
    runConfiguration: PropTypes.func,
    runConfigurationId: PropTypes.string,
    runConfigurationCluster: PropTypes.func,
    onSelectPipeline: PropTypes.func,
    defaultPriceTypeIsSpot: PropTypes.bool,
    defaultPriceTypeIsLoading: PropTypes.bool,
    isDetachedConfiguration: PropTypes.bool,
    configurationId: PropTypes.string,
    selectedPipelineParametersIsLoading: PropTypes.bool,
    fireCloudMethod: PropTypes.shape({
      name: PropTypes.string,
      namespace: PropTypes.string,
      snapshot: PropTypes.string,
      configuration: PropTypes.string,
      configurationSnapshot: PropTypes.string
    }),
    onInitialized: PropTypes.func,
    onModified: PropTypes.func,
    continueRun: PropTypes.oneOfType([PropTypes.number, PropTypes.string])
  };

  static defaultProps = {
    instanceTypes: names.allowedInstanceTypes,
    toolInstanceTypes: names.allowedToolInstanceTypes
  };

  state = {
    userTags: {},
    userTagsValidation: [],
    userTagsVisibleTags: [],
    userTagsValidationPayload: undefined,
    conditionalParameters: [],
    openedPanels: [PARAMETERS],
    isDts: this.isDts(),
    execEnvSelectValue: null,
    dtsId: null,
    startIdle: false,
    useDefaultCmd: false,
    pipelineBrowserVisible: false,
    dockerImageBrowserVisible: false,
    pipeline: null,
    version: null,
    pipelineChanged: false,
    pipelineConfiguration: null,
    launchCluster: false,
    autoScaledCluster: false,
    hybridAutoScaledClusterEnabled: false,
    gpuScalingConfiguration: undefined,
    childNodeInstanceConfiguration: undefined,
    gridEngineEnabled: false,
    sparkEnabled: false,
    slurmEnabled: false,
    kubeEnabled: false,
    autoScaledPriceType: undefined,
    nodesCount: 0,
    maxNodesCount: 0,
    fsConfig: undefined,
    configureClusterDialogVisible: false,
    scheduleRules: null,
    bucketBrowserVisible: false,
    bucketBrowserAllowUpload: false,
    bucketPath: null,
    bucketPathParameterKey: null,
    bucketPathParameterSection: null,
    currentMetadataEntity: [],
    rootEntityId: null,
    filteredEntityFields: [],
    activeAutoCompleteParameterKey: null,
    currentProjectMetadata: null,
    estimatedPrice: {
      evaluated: false,
      pending: false,
      isValid: false,
      pricePerHour: 0,
      maximumPrice: 0,
      averagePrice: 0,
      minimumPrice: 0
    },
    validation: {
      [EXEC_ENVIRONMENT]: {
        coresNumber: {
          result: 'success',
          message: null
        }
      }
    },
    currentProjectId: null,
    currentLaunchKey: null,
    showOnlyFolderInBucketBrowser: false,
    allowBucketSelectionInBucketBrowser: false,
    systemParameters: [],
    fireCloudMethodName: (this.props.fireCloudMethod &&
      this.props.fireCloudMethod.name) || null,
    fireCloudMethodNamespace: (this.props.fireCloudMethod &&
      this.props.fireCloudMethod.namespace) || null,
    fireCloudMethodSnapshot: (this.props.fireCloudMethod &&
      this.props.fireCloudMethod.snapshot) || null,
    fireCloudMethodConfiguration: (this.props.fireCloudMethod &&
      this.props.fireCloudMethod.configuration) || null,
    fireCloudMethodConfigurationSnapshot: (this.props.fireCloudMethod &&
      this.props.fireCloudMethod.configurationSnapshot) || null,
    fireCloudInputs: {},
    fireCloudOutputs: {},
    fireCloudInputsErrors: {},
    fireCloudOutputsErrors: {},
    fireCloudDefaultInputs: (
      this.props.fireCloudMethod &&
      this.props.fireCloudMethod.methodInputs
    ) || [],
    fireCloudDefaultOutputs: (
      this.props.fireCloudMethod &&
      this.props.fireCloudMethod.methodOutputs
    ) || [],
    autoPause: true,
    showLaunchCommands: false,
    runCapabilities: [],
    userRunCapabilities: [],
    userRunCapabilitiesPending: true,
    useResolvedParameters: false,
    runNameAlias: undefined,
    isRawEditEnabled: false,
    parameterType: undefined,
    selectedParameter: undefined,
    highlightedParameterSection: undefined,
    reservationParameters: undefined
  };

  formItemLayout = {
    labelCol: {
      className: styles.formItemLabelColumn
    },
    wrapperCol: {
      className: styles.formItemWrapperColumn
    }
  };

  leftFormItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 5},
      md: {span: 4},
      lg: {span: 3},
      xl: {span: 2}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 16},
      md: {span: 15},
      lg: {span: 15},
      xl: {span: 10}
    }
  };

  parameterItemLayout = {
    wrapperCol: {
      xs: {span: 24}
    }
  };

  cmdTemplateFormItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 5},
      md: {span: 4},
      lg: {span: 3},
      xl: {span: 2}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 19},
      md: {span: 20},
      lg: {span: 21},
      xl: {span: 22}
    }
  };

  prevParameters = {};
  sectionRefs = {};
  parametersNavigationWrapperRef;
  parametersNavigationRef;
  parametersNavigationIsSticky = false;
  checkRAF;

  @observable modified = false;
  @observable inputPaths = [];
  @observable outputPaths = [];
  @observable dockerImage = null;
  @observable cmdTemplateValue;
  @observable launchCommandPayload;
  @observable _toolSettings;
  @observable toolSettingsPending = false;
  @observable toolDefaultCmd;
  @observable regionDisabledByToolSettings = false;
  @observable toolCloudRegion = null;
  @observable toolPlatform = null;
  @observable toolAllowSensitive = true;

  @observable rescheduleRun = undefined;
  @observable rescheduleRunInitialValue = undefined;

  _customValidators = {}

  get customValidators () {
    return this._customValidators;
  }

  @action
  formFieldsChanged = () => {
    const token = this.__formFieldsChangedToken = {};
    class FormFieldChangedAbortedError extends Error {}
    const checkIfNotAborted = () => {
      if (token !== this.__formFieldsChangedToken) {
        throw new FormFieldChangedAbortedError();
      }
    };
    clearTimeout(this.__formFieldsChangedTimeout);
    this.__formFieldsChangedTimeout = setTimeout(async () => {
      try {
        checkIfNotAborted();
        const {form} = this.props;
        const {
          parameters: formParameters,
          initialParameters
        } = this.getCurrentParametersPayload();
        const formParametersPayload = parameterUtilities.parametersToPayloadParams(formParameters);
        this.inputPaths = getInputPaths(formParametersPayload);
        this.outputPaths = getOutputPaths(formParametersPayload);
        const currentDockerImage = form.getFieldValue(`${EXEC_ENVIRONMENT}.dockerImage`);
        if (!this.toolSettingsPending && this.dockerImage !== currentDockerImage) {
          if (currentDockerImage) {
            await this.loadToolSettings(currentDockerImage);
            checkIfNotAborted();
            const currentValue = this.props.form.getFieldValue(`${EXEC_ENVIRONMENT}.cloudRegionId`);
            const regionId = this.correctCloudRegion(
              currentValue ||
              this.defaultCloudRegionId
            );
            this.props.form.setFieldsValue({
              [`${EXEC_ENVIRONMENT}.cloudRegionId`]: this.toolCloudRegion || regionId
            });
          } else {
            this.resetToolSettings();
          }
        }
        this.dockerImage = currentDockerImage || this.getDefaultValue('docker_image');
        this.modified = checkModifiedState(
          this.props,
          this.state,
          {
            defaultCloudRegionId: this.defaultCloudRegionId,
            execEnvSelectValue: this.getExecEnvSelectValue().execEnvSelectValue,
            spotInitialValue: this.correctPriceTypeValue(this.getDefaultValue('is_spot')),
            cmdTemplateValue: this.cmdTemplateValue,
            toolDefaultCmd: this.toolDefaultCmd,
            formParameters,
            initialParameters
          }
        );
        this.props.onModified && this.props.onModified(this.modified);
        checkIfNotAborted();
        const validateFields = async () => new Promise((resolve) => {
          const onValidationChange = (formInvalid, values) => {
            resolve({values, errors: formInvalid});
          };
          if (this.forceValidation) {
            this.forceValidation = false;
            this.props.form.validateFields({force: true}, onValidationChange);
          } else {
            this.props.form.validateFields(onValidationChange);
          }
        });
        const {values} = await validateFields();
        checkIfNotAborted();
        const payload = values ? await this.generateLaunchPayload(values) : undefined;
        await this.validateUserTags(payload);
        checkIfNotAborted();
        await this.rebuildLaunchCommand();
        checkIfNotAborted();
      } catch (error) {
        if (error instanceof FormFieldChangedAbortedError) {
          // noop
        } else {
          console.log(error);
        }
      }
    }, 0);
  };

  rebuildLaunchCommand = async () => {
    return new Promise((resolve) => {
      if (!this.props.detached && !this.props.editConfigurationMode) {
        this.props.form.validateFields(async (err, values) => {
          if (!err && this.validateFireCloudConnections()) {
            this.launchCommandPayload = await this.generateLaunchPayload(values);
          } else {
            this.launchCommandPayload = undefined;
          }
          resolve();
        });
      } else {
        resolve();
      }
    });
  };

  showLaunchCommands = () => {
    this.setState({showLaunchCommands: true});
  };

  hideLaunchCommands = () => {
    this.setState({showLaunchCommands: false});
  };

  get hyperThreadingDisabled () {
    return (this.state.runCapabilities || [])
      .indexOf(RUN_CAPABILITIES.disableHyperThreading) >= 0;
  }

  @computed
  get isWindowsPlatform () {
    return /^windows$/i.test(this.toolPlatform);
  }

  onRunCapabilitiesSelect = (capabilities) => {
    this.setState({
      runCapabilities: (capabilities || []).slice()
    }, this.formFieldsChanged);
  };

  renderAdditionalRunCapabilities = () => {
    if (hasPlatformSpecificCapabilities(this.toolPlatform, this.props.preferences)) {
      return (
        <FormItem
          className={getFormItemClassName(styles.formItem, 'runCapabilities')}
          {...this.formItemLayout}
          label="Run capabilities"
          hasFeedback
        >
          <RunCapabilities
            disabled={this.state.userRunCapabilitiesPending}
            values={this.state.runCapabilities}
            onChange={this.onRunCapabilitiesSelect}
            platform={this.toolPlatform}
            dockerImage={this.props.form.getFieldValue(`${EXEC_ENVIRONMENT}.dockerImage`)}
            provider={this.currentCloudRegionProvider}
            region={this.currentCloudRegion}
            mode={RUN_CAPABILITIES_MODE.launch}
          />
        </FormItem>
      );
    }
  }

  @observable
  _fireCloudConfigurations = null;
  @observable
  _fireCloudParameters = null;

  @computed
  get isFireCloudSelected () {
    return !!(this.state.fireCloudMethodName && this.state.fireCloudMethodNamespace &&
      this.state.fireCloudMethodSnapshot);
  }

  @computed
  get selectedFireCloudConfiguration () {
    if (this.isFireCloudSelected &&
      this._fireCloudConfigurations && this._fireCloudConfigurations.loaded) {
      return (this._fireCloudConfigurations.value || []).filter(
        config =>
          config.name === this.state.fireCloudMethodConfiguration &&
          config.snapshotId === this.state.fireCloudMethodConfigurationSnapshot
      )[0];
    }
    return null;
  }

  @computed
  get selectedFireCloudParameters () {
    if (this.isFireCloudSelected &&
      this._fireCloudParameters && this._fireCloudParameters.loaded) {
      return this._fireCloudParameters.value;
    }
    return null;
  }

  loadFireCloudConfigurations = () => {
    if (this.state.fireCloudMethodNamespace && this.state.fireCloudMethodName &&
      this.state.fireCloudMethodSnapshot) {
      if (!this.state.fireCloudDefaultInputs || this.state.fireCloudDefaultInputs.length === 0 ||
        !this.state.fireCloudDefaultOutputs || this.state.fireCloudDefaultOutputs.length === 0) {
        this._fireCloudConfigurations = new FireCloudMethodSnapshotConfigurationsRequest(
          this.props.googleApi,
          this.state.fireCloudMethodNamespace,
          this.state.fireCloudMethodName,
          this.state.fireCloudMethodSnapshot
        );
      } else {
        this._fireCloudConfigurations = null;
      }
      this._fireCloudParameters = new FireCloudMethodParameters(
        this.props.googleApi,
        this.state.fireCloudMethodNamespace,
        this.state.fireCloudMethodName,
        this.state.fireCloudMethodSnapshot
      );
    }
  };

  getFireCloudDefaultInputs = () => {
    if (this.state.fireCloudDefaultInputs && this.state.fireCloudDefaultInputs.length > 0) {
      const obj = {};
      for (let i = 0; i < this.state.fireCloudDefaultInputs.length; i++) {
        const param = this.state.fireCloudDefaultInputs[i];
        obj[param.name] = param.value;
      }
      return obj;
    } else if (this.selectedFireCloudConfiguration &&
      this.selectedFireCloudConfiguration.payloadObject) {
      return this.selectedFireCloudConfiguration.payloadObject.inputs || {};
    }
    return {};
  };

  getFireCloudDefaultOutputs = () => {
    if (this.state.fireCloudDefaultOutputs && this.state.fireCloudDefaultOutputs.length > 0) {
      const obj = {};
      for (let i = 0; i < this.state.fireCloudDefaultOutputs.length; i++) {
        const param = this.state.fireCloudDefaultOutputs[i];
        obj[param.name] = param.value;
      }
      return obj;
    } else if (this.selectedFireCloudConfiguration &&
      this.selectedFireCloudConfiguration.payloadObject) {
      return this.selectedFireCloudConfiguration.payloadObject.outputs || {};
    }
    return {};
  };

  validateFireCloudConnections = () => {
    if (this.props.detached &&
      this.state.fireCloudMethodName &&
      this.state.fireCloudMethodNamespace &&
      this.state.fireCloudMethodSnapshot &&
      this.selectedFireCloudParameters) {
      const inputs = (this.selectedFireCloudParameters.inputs || []).map(i => i);
      const outputs = (this.selectedFireCloudParameters.outputs || []).map(o => o);
      const defaultInputs = this.getFireCloudDefaultInputs();
      const defaultOutputs = this.getFireCloudDefaultOutputs();
      const inputsValues = this.state.fireCloudInputs;
      const outputsValues = this.state.fireCloudOutputs;
      let validationFailed = false;
      const validateParameters = (params, defaultParams, values) => {
        const validationObj = {};
        for (let i = 0; i < params.length; i++) {
          const value = values[params[i].name] === undefined
            ? defaultParams[params[i].name]
            : values[params[i].name];
          if (!value && !params[i].optional) {
            validationObj[params[i].name] = 'This field is required';
            validationFailed = true;
          } else if (value) {
            switch ((params[i].inputType || params[i].outputType || '').toLowerCase()) {
              case 'int':
              case 'int?':
                if (isNaN(value) || +value !== Math.round(+value)) {
                  validationObj[params[i].name] = 'This field should be integer';
                }
                break;
            }
          }
        }
        return validationObj;
      };
      const inputsValidation = validateParameters(inputs, defaultInputs, inputsValues);
      const outputsValidation = validateParameters(outputs, defaultOutputs, outputsValues);
      this.setState({
        fireCloudInputsErrors: inputsValidation,
        fireCloudOutputsErrors: outputsValidation
      });
      return !validationFailed;
    }
    return true;
  };

  getFireCloudConnections = () => {
    if (this.props.detached &&
      this.state.fireCloudMethodName &&
      this.state.fireCloudMethodNamespace &&
      this.state.fireCloudMethodSnapshot &&
      this.selectedFireCloudParameters) {
      const inputs = (this.selectedFireCloudParameters.inputs || []).map(i => i);
      const outputs = (this.selectedFireCloudParameters.outputs || []).map(o => o);
      const defaultInputs = this.getFireCloudDefaultInputs();
      const defaultOutputs = this.getFireCloudDefaultOutputs();
      const inputsValues = this.state.fireCloudInputs;
      const outputsValues = this.state.fireCloudOutputs;
      const getParameters = (params, defaultParams, values) => {
        const result = [];
        for (let i = 0; i < params.length; i++) {
          const value = values[params[i].name] === undefined
            ? defaultParams[params[i].name]
            : values[params[i].name];
          if (value) {
            result.push({
              name: params[i].name,
              type: params[i].inputType || params[i].outputType,
              optional: params[i].optional,
              value
            });
          }
        }
        return result;
      };
      const methodInputs = getParameters(inputs, defaultInputs, inputsValues);
      const methodOutputs = getParameters(outputs, defaultOutputs, outputsValues);
      return {
        methodInputs,
        methodOutputs
      };
    }
    return null;
  };

  @computed
  get dtsList () {
    if (this.props.dtsList.loaded) {
      return (this.props.dtsList.value || []).filter(dts => dts.schedulable === true).map(i => i);
    }
    return [];
  }

  @computed
  get awsRegions () {
    if (this.props.awsRegions.loaded) {
      return (this.props.awsRegions.value || []).map(r => r);
    }
    return [];
  }

  @computed
  get defaultCloudRegionId () {
    const [defaultRegion] = this.awsRegions.filter(r => r.default);
    if (defaultRegion) {
      return `${defaultRegion.id}`;
    }
    return null;
  }

  @computed
  get currentCloudRegion () {
    const formValue = this.getSectionFieldValue(EXEC_ENVIRONMENT)('cloudRegionId') ||
      this.getDefaultValue('cloudRegionId') || this.defaultCloudRegionId;
    return this.awsRegions.filter(r => `${r.id}` === `${formValue}`)[0];
  }

  @computed
  get currentCloudRegionId () {
    if (this.currentCloudRegion) {
      return this.currentCloudRegion.id;
    }
    return null;
  }

  @computed
  get currentCloudRegionProvider () {
    if (this.currentCloudRegion) {
      return this.currentCloudRegion.provider;
    }
    return null;
  }

  get launchFormUserPreferences () {
    const {uiNavigation, editConfigurationMode, isDetachedConfiguration} = this.props;
    if (editConfigurationMode || isDetachedConfiguration) {
      return undefined;
    }
    const {pipeline} = this.state;
    const config = uiNavigation.launchForm || {};
    if (pipeline) {
      return config.pipelines;
    }
    return config.tools;
  }

  get executionEnvironmentSectionVisible () {
    const {
      'exec-visible': execVisible = true,
      'execution-environment-visible': executionEnvironmentVisible = execVisible
    } = this.launchFormUserPreferences || {};
    return `${executionEnvironmentVisible}`.toLowerCase() === 'true';
  }

  get advancedSectionVisible () {
    const {
      'advanced-visible': advancedVisible = true
    } = this.launchFormUserPreferences || {};
    return `${advancedVisible}`.toLowerCase() === 'true';
  }

  get parametersSectionVisible () {
    const {
      'params-visible': paramsVisible = true,
      'parameters-visible': parameterVisible = paramsVisible
    } = this.launchFormUserPreferences || {};
    return `${parameterVisible}`.toLowerCase() === 'true';
  }

  get estimatedPriceSectionVisible () {
    const {
      'estimates-visible': estimatesVisible = true,
      'estimated-price-visible': estimatedPriceSectionVisible = estimatesVisible
    } = this.launchFormUserPreferences || {};
    return `${estimatedPriceSectionVisible}`.toLowerCase() === 'true';
  }

  get currentDetachedConfiguration () {
    const {detachedConfigurations = []} = this.state;
    const {currentConfigurationName} = this.props;
    return detachedConfigurations.find((d) => d.name === currentConfigurationName);
  }

  getIsInstanceTypeWithReservation () {
    const {
      detached,
      preferences
    } = this.props;
    const instanceTypeValue = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type');
    return detached
      ? false
      : Boolean(findReservationParameterConfig(instanceTypeValue, preferences));
  }

  getDefaultRootEntityId () {
    const {currentDetachedConfiguration = {}} = this;
    const {currentMetadataEntity = []} = this.state;
    const {rootEntityId} = currentDetachedConfiguration;
    if (rootEntityId) {
      const entity = currentMetadataEntity.find(
        entity => entity.metadataClass && `${entity.metadataClass.id}` === `${rootEntityId}`
      );
      if (entity) {
        return `${rootEntityId}`;
      }
    }
    return '';
  }

  expandErroredPanels = (errorKeys, scroll = true) => {
    const {openedPanels} = this.state;
    const getPanelKey = (key) => key === SYSTEM_PARAMETERS ? ADVANCED : key;
    const wrongFields = [];
    const extractFields = (section) => {
      if (section === ADVANCED || section === EXEC_ENVIRONMENT) {
        for (let key in errorKeys[section]) {
          if (errorKeys[section].hasOwnProperty(key)) {
            wrongFields.push(key.replace(/\./g, '_'));
          }
        }
      } else if (section === PARAMETERS || section === SYSTEM_PARAMETERS) {
        for (let key in errorKeys[section]) {
          if (errorKeys[section].hasOwnProperty(key)) {
            wrongFields.push(key);
          }
        }
      }
    };
    for (let key in errorKeys) {
      if (errorKeys.hasOwnProperty(key)) {
        extractFields(key);
        if (openedPanels.indexOf(getPanelKey(key)) === -1) {
          openedPanels.push(getPanelKey(key));
        }
      }
    }
    this.setState({
      openedPanels
    }, () => {
      if (wrongFields.length > 0 && scroll) {
        const scrollToWrongField = () => {
          const element = document.querySelector(`.${wrongFields[0]}`);
          if (element) {
            element.scrollIntoView({behavior: 'smooth', block: 'center'});
          }
        };
        const TIMEOUT_MS = 500;
        setTimeout(scrollToWrongField, TIMEOUT_MS);
      }
    });
  };

  handleSubmit = (e) => {
    e.preventDefault();

    const mergeErrors = (...errors) => {
      const filtered = errors.filter(Boolean);
      if (filtered.length === 0) {
        return undefined;
      }
      if (filtered.length === 1) {
        return filtered[0];
      }
      const [first, second, ...rest] = filtered;
      const merged = [
        ...new Set(Object.keys(first).concat(Object.keys(second)))
      ].reduce((acc, cur) => ({
        ...acc,
        [cur]: {
          ...(first[cur] || {}),
          ...(second[cur] || {})
        }
      }), []);
      return mergeErrors(merged, ...rest);
    };

    this.props.form.validateFields(async (errors, values) => {
      const userTagsValid = await this.validateUserTags(await this.generateLaunchPayload(values));
      const parametersValidationResult = await this.getParametersValidationResult(true);
      const {
        nonValidParameter
      } = parametersValidationResult ?? {};
      const err = mergeErrors(
        errors,
        userTagsValid ? undefined : {[ADVANCED]: {customTags: false}},
        nonValidParameter ? {[nonValidParameter.system ? SYSTEM_PARAMETERS : PARAMETERS]: {
          [getParameterKeyClassName(nonValidParameter)]: false
        }} : undefined);
      if (err) {
        console.warn('Validation error');
        console.log(err);
        if (nonValidParameter) {
          console.log('not valid parameter:');
          console.log(nonValidParameter);
        }
      }
      if (!err && this.validateFireCloudConnections()) {
        let payload;
        try {
          if (this.props.editConfigurationMode) {
            payload = await this.generateConfigurationPayload(values, {
              skipReservationParameters: false,
              applyAdditionalParameters: false
            });
          } else if (this.props.detached) {
            // single payload
            payload = await this.generateLaunchPayload(values);
          } else {
            // multiple payloads
            payload = await this.generateLaunchPayloads(values);
          }
        } catch (e) {
          message.error(e.message, 5);
          return;
        }
        if (this.props.onLaunch) {
          const result = await this.props.onLaunch(
            payload,
            values[ADVANCED].hostedApplication,
            this.toolPlatform,
            this.props.parameters.run_as &&
            this.currentUserName() !== this.props.parameters.run_as
          );
          if (result) {
            this.reset();
            this.prepare();
            this.forceValidation = true;
            this.formFieldsChanged();
          }
        }
      } else {
        this.expandErroredPanels(err);
      }
    });
  };

  run = ({key}, entitiesIds, metadataClass, expansionExpression, folderId) => {
    this.props.form.validateFields(async (err, values) => {
      if (!err && this.validateFireCloudConnections()) {
        const payload = await this.generateConfigurationPayload(values, {
          skipReservationParameters: true
        });
        switch (key) {
          case RUN_SELECTED_KEY:
            if (this.props.runConfiguration) {
              if (this.props.isDetachedConfiguration) {
                this.props.runConfiguration(
                  payload,
                  entitiesIds,
                  metadataClass,
                  expansionExpression,
                  folderId
                );
              } else {
                this.props.runConfiguration(payload);
              }
            }
            break;
          case RUN_CLUSTER_KEY:
            if (this.props.isDetachedConfiguration) {
              this.props.runConfigurationCluster(
                payload,
                entitiesIds,
                metadataClass,
                expansionExpression,
                folderId
              );
            } else {
              this.props.runConfigurationCluster(payload);
            }
            break;
        }
      }
    });
  };

  getExecEnvSelectValue = () => {
    if (!this.props.detached) {
      return {
        execEnvSelectValue: null,
        dtsId: null
      };
    }
    let execEnvSelectValue;
    let dtsId;
    const [currentConfiguration] = this.props.configurations
      .filter(config => config.name === this.props.currentConfigurationName);

    if (this.isFireCloudSelected) {
      execEnvSelectValue = FIRE_CLOUD_ENVIRONMENT;
    } else if (currentConfiguration &&
      currentConfiguration.executionEnvironment === DTS_ENVIRONMENT) {
      dtsId = this.state.dtsId || currentConfiguration.dtsId;
      execEnvSelectValue = `${DTS_ENVIRONMENT}.${dtsId}`;
    } else {
      execEnvSelectValue = CLOUD_PLATFORM_ENVIRONMENT;
    }

    return {execEnvSelectValue, dtsId};
  };

  resetState = (keepPipeline) => {
    const {execEnvSelectValue, dtsId} = this.getExecEnvSelectValue();
    const autoScaledCluster = autoScaledClusterEnabled(this.props.parameters.parameters);
    const hybridAutoScaledCluster = hybridAutoScaledClusterEnabled(
      this.props.parameters.parameters
    );
    const gpuScalingConfiguration = readGPUScalingPreference(
      {
        autoScaled: autoScaledCluster,
        provider: this.currentCloudRegionProvider,
        hybrid: hybridAutoScaledCluster,
        parameters: this.props.parameters.parameters
      },
      this.props.preferences
    );
    const childNodeInstanceConfiguration = parseChildNodeInstanceConfiguration({
      autoScaled: autoScaledCluster,
      gpuScaling: !!gpuScalingConfiguration,
      hybrid: hybridAutoScaledCluster,
      parameters: (this.props.parameters || {}).parameters
    });
    const gridEngineEnabledValue = gridEngineEnabled(this.props.parameters.parameters);
    const sparkEnabledValue = sparkEnabled(this.props.parameters.parameters);
    const slurmEnabledValue = slurmEnabled(this.props.parameters.parameters);
    const kubeEnabledValue = kubeEnabled(this.props.parameters.parameters);
    const autoScaledPriceTypeValue = getAutoScaledPriceTypeValue(this.props.parameters.parameters);
    const fsConfigValue = getFsConfigFromParameters(this.props.parameters.parameters);
    let runCapabilities = getEnabledCapabilities(this.props.parameters.parameters);
    if (
      !this.props.editConfigurationMode
    ) {
      runCapabilities = correctRequiredCapabilities(
        [...new Set([...(runCapabilities || []), ...(this.state.userRunCapabilities || [])])],
        this.props.preferences
      );
    }
    const isRawEditEnabled = this.props.parameters.raw;
    const reservationParameters = readReservationParameters(this.props.parameters.parameters);
    if (keepPipeline) {
      this.setState({
        openedPanels: this.getDefaultOpenedPanels(),
        startIdle: this.props.parameters.cmd_template === 'sleep infinity',
        useDefaultCmd: false,
        isDts: this.isDts(),
        execEnvSelectValue,
        dtsId,
        pipelineBrowserVisible: false,
        dockerImageBrowserVisible: false,
        launchCluster: +this.props.parameters.node_count > 0 || autoScaledCluster,
        autoScaledCluster: autoScaledCluster,
        hybridAutoScaledClusterEnabled: hybridAutoScaledCluster,
        gpuScalingConfiguration,
        childNodeInstanceConfiguration,
        gridEngineEnabled: gridEngineEnabledValue,
        sparkEnabled: sparkEnabledValue,
        slurmEnabled: slurmEnabledValue,
        kubeEnabled: kubeEnabledValue,
        autoScaledPriceType: autoScaledPriceTypeValue,
        fsConfig: fsConfigValue,
        runCapabilities,
        reservationParameters,
        scheduleRules: null,
        nodesCount: +this.props.parameters.node_count,
        maxNodesCount: this.props.parameters.parameters &&
        this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS]
          ? +this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS].value
          : 0,
        bucketBrowserVisible: false,
        bucketBrowserAllowUpload: false,
        bucketPath: null,
        bucketPathParameterKey: null,
        bucketPathParameterSection: null,
        estimatedPrice: {
          evaluated: false,
          isValid: false,
          pending: false,
          pricePerHour: 0,
          maximumPrice: 0,
          averagePrice: 0,
          minimumPrice: 0
        },
        validation: {
          [EXEC_ENVIRONMENT]: {
            coresNumber: {
              result: 'success',
              message: null
            }
          }
        },
        fireCloudInputs: {},
        fireCloudOutputs: {},
        fireCloudInputsErrors: {},
        fireCloudOutputsErrors: {},
        isRawEditEnabled
      }, () => {
        this.forceValidation = true;
        this.formFieldsChanged();
      });
    } else {
      this.setState({
        openedPanels: this.getDefaultOpenedPanels(),
        startIdle: this.props.parameters.cmd_template === 'sleep infinity',
        useDefaultCmd: false,
        isDts: this.isDts(),
        execEnvSelectValue,
        dtsId,
        pipelineBrowserVisible: false,
        dockerImageBrowserVisible: false,
        pipeline: null,
        version: null,
        pipelineChanged: false,
        pipelineConfiguration: null,
        launchCluster: +this.props.parameters.node_count > 0 || autoScaledCluster,
        autoScaledCluster: autoScaledCluster,
        hybridAutoScaledClusterEnabled: hybridAutoScaledCluster,
        gpuScalingConfiguration,
        childNodeInstanceConfiguration,
        gridEngineEnabled: gridEngineEnabledValue,
        sparkEnabled: sparkEnabledValue,
        slurmEnabled: slurmEnabledValue,
        kubeEnabled: kubeEnabledValue,
        autoScaledPriceType: autoScaledPriceTypeValue,
        fsConfig: fsConfigValue,
        runCapabilities,
        reservationParameters,
        scheduleRules: null,
        nodesCount: +this.props.parameters.node_count,
        maxNodesCount: this.props.parameters.parameters &&
        this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS]
          ? +this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS].value
          : 0,
        bucketBrowserVisible: false,
        bucketBrowserAllowUpload: false,
        bucketPath: null,
        bucketPathParameterKey: null,
        bucketPathParameterSection: null,
        estimatedPrice: {
          evaluated: false,
          isValid: false,
          pending: false,
          pricePerHour: 0,
          maximumPrice: 0,
          averagePrice: 0,
          minimumPrice: 0
        },
        validation: {
          [EXEC_ENVIRONMENT]: {
            coresNumber: {
              result: 'success',
              message: null
            }
          }
        },
        fireCloudMethodName: null,
        fireCloudMethodNamespace: null,
        fireCloudMethodSnapshot: null,
        fireCloudMethodConfiguration: null,
        fireCloudMethodConfigurationSnapshot: null,
        fireCloudInputs: {},
        fireCloudOutputs: {},
        fireCloudInputsErrors: {},
        fireCloudOutputsErrors: {},
        autoPause: true,
        isRawEditEnabled
      }, () => {
        this.forceValidation = true;
        this.formFieldsChanged();
      });
    }
  };

  generateConfigurationPayload = async (values, options = {}) => {
    const {
      skipReservationParameters = false,
      applyAdditionalReservationParameters = true
    } = options || {};
    let cmd = values[ADVANCED].cmdTemplate;
    if (this.state.useDefaultCmd && this.toolDefaultCmd) {
      cmd = this.toolDefaultCmd;
    } else if (this.state.startIdle) {
      cmd = 'sleep infinity';
    }
    const stopAfterIsIncorrect = (o) => o === null || o === undefined || o === '' || isNaN(o);
    const {
      parameters,
      conditionalParameters
    } = parameterUtilities.parametersToConfigurationParams(this.getParameters());
    const instanceType = values[EXEC_ENVIRONMENT].type;
    let payload = {
      instance_size: instanceType,
      instance_disk: +values[EXEC_ENVIRONMENT].disk,
      timeout: +(values[ADVANCED].timeout || 0),
      stopAfter: stopAfterIsIncorrect(values[ADVANCED].stopAfter)
        ? undefined
        : (+values[ADVANCED].stopAfter || 0),
      endpointName: values[ADVANCED].endpointName,
      cmd_template: cmd,
      node_count: this.state.launchCluster ? this.state.nodesCount : undefined,
      docker_image: values[EXEC_ENVIRONMENT].dockerImage,
      parameters,
      conditional_parameters: conditionalParameters,
      configuration: values.configuration,
      is_spot: (values[ADVANCED].is_spot || `${this.getDefaultValue('is_spot')}`) === 'true',
      cloudRegionId: values[EXEC_ENVIRONMENT].cloudRegionId
        ? +values[EXEC_ENVIRONMENT].cloudRegionId
        : undefined,
      notifications: (values[ADVANCED].notifications || []).slice(),
      raw: this.state.isRawEditEnabled
    };
    if (this.isWindowsPlatform) {
      payload.node_count = undefined;
    }
    if (!this.props.detached) {
      delete payload.endpointName;
      delete payload.stopAfter;
    }
    if (this.state.isDts && this.props.detached) {
      payload.instance_size = undefined;
      payload.instance_disk = undefined;
      payload.timeout = undefined;
      payload.is_spot = undefined;
      payload.cloudRegionId = undefined;
      payload.coresNumber = +values[EXEC_ENVIRONMENT].coresNumber || null;
      payload.dtsId = +this.state.dtsId;
    }
    if (!this.isFireCloudSelected) {
      if (values[ADVANCED].limitMounts && !this.isWindowsPlatform) {
        payload.parameters[CP_CAP_LIMIT_MOUNTS] = {
          type: 'string',
          required: false,
          value: values[ADVANCED].limitMounts
        };
      }
      if (this.state.launchCluster && this.state.autoScaledCluster) {
        payload.parameters[CP_CAP_AUTOSCALE] = {
          type: 'boolean',
          value: true
        };
        payload.parameters[CP_CAP_AUTOSCALE_WORKERS] = {
          type: 'int',
          value: +this.state.maxNodesCount
        };
        if (this.state.autoScaledPriceType) {
          payload.parameters[CP_CAP_AUTOSCALE_PRICE_TYPE] = {
            type: 'string',
            value: this.state.autoScaledPriceType
          };
        } else {
          delete payload.parameters[CP_CAP_AUTOSCALE_PRICE_TYPE];
        }
        if (this.state.hybridAutoScaledClusterEnabled) {
          payload.parameters[CP_CAP_AUTOSCALE_HYBRID] = {
            type: 'boolean',
            value: true
          };
        }
        if (this.state.gpuScalingConfiguration) {
          payload.parameters = applyGPUScalingParameters(
            this.state.gpuScalingConfiguration,
            payload.parameters
          );
        } else if (this.state.childNodeInstanceConfiguration) {
          applyChildNodeInstanceParameters(
            payload.parameters,
            this.state.childNodeInstanceConfiguration,
            this.state.hybridAutoScaledClusterEnabled
          );
        }
      }
      if (this.state.launchCluster && this.state.gridEngineEnabled) {
        payload.parameters[CP_CAP_SGE] = {
          type: 'boolean',
          value: true
        };
      }
      if (this.state.launchCluster && this.state.sparkEnabled) {
        payload.parameters[CP_CAP_SPARK] = {
          type: 'boolean',
          value: true
        };
      }
      if (this.state.launchCluster && this.state.slurmEnabled) {
        payload.parameters[CP_CAP_SLURM] = {
          type: 'boolean',
          value: true
        };
      }
      if (this.state.launchCluster && this.state.kubeEnabled) {
        payload.parameters[CP_CAP_KUBE] = {
          type: 'boolean',
          value: true
        };
        payload.parameters[CP_CAP_DIND_CONTAINER] = {
          type: 'boolean',
          value: true
        };
        payload.parameters[CP_CAP_SYSTEMD_CONTAINER] = {
          type: 'boolean',
          value: true
        };
      }
      if (this.rescheduleRun !== undefined) {
        payload.parameters[CP_CAP_RESCHEDULE_RUN] = {
          type: 'boolean',
          value: this.rescheduleRun
        };
      }
    }
    payload.parameters = getParametersFromFsConfig(
      this.state.fsConfig,
      payload.parameters,
      this.currentCloudRegionProvider
    );
    if (!skipReservationParameters) {
      const {
        parameters: appliedReservationParameters
      } = await buildLaunchParametersFromReservationParameters(
        this.state.reservationParameters,
        instanceType,
        payload.parameters,
        {
          applyAdditionalParameters: applyAdditionalReservationParameters
        }
      );
      payload.parameters = appliedReservationParameters;
    }
    payload.parameters = applyCapabilities(
      payload.parameters,
      this.state.runCapabilities,
      this.props.preferences,
      this.toolPlatform
    );
    if (this.props.detached && this.state.pipeline && this.state.version) {
      payload.pipelineId = this.state.pipeline.id;
      payload.pipelineVersion = this.state.version;
      payload.configName = this.state.pipelineConfiguration;
      payload.executionEnvironment = this.state.isDts
        ? DTS_ENVIRONMENT : CLOUD_PLATFORM_ENVIRONMENT;
    }
    if (this.props.detached &&
      this.state.fireCloudMethodName &&
      this.state.fireCloudMethodNamespace &&
      this.state.fireCloudMethodSnapshot) {
      payload.methodName =
        `${this.state.fireCloudMethodNamespace}/${this.state.fireCloudMethodName}`;
      payload.methodSnapshot = this.state.fireCloudMethodSnapshot;
      if (this.state.fireCloudMethodConfiguration &&
        this.state.fireCloudMethodConfigurationSnapshot) {
        payload.methodConfigurationName =
          `${this.state.fireCloudMethodNamespace}/${this.state.fireCloudMethodConfiguration}`;
        payload.methodConfigurationSnapshot = this.state.fireCloudMethodConfigurationSnapshot;
      }
      payload.executionEnvironment = FIRE_CLOUD_ENVIRONMENT;
      const connections = this.getFireCloudConnections();
      if (connections) {
        payload.methodInputs = connections.methodInputs;
        payload.methodOutputs = connections.methodOutputs;
      }
    }
    if (this.props.isDetachedConfiguration) {
      payload.rootEntityId = this.state.rootEntityId;
    }
    return payload;
  };

  generateLaunchPayload = async (values, parametersPayloadId = undefined) => {
    let cmd = values[ADVANCED].cmdTemplate;
    if (this.state.useDefaultCmd && this.toolDefaultCmd) {
      cmd = this.toolDefaultCmd;
    } else if (this.state.startIdle) {
      cmd = 'sleep infinity';
    }
    const instanceType = values[EXEC_ENVIRONMENT].type;
    const tags = filterVisibleTagsSync(
      this.state.userTags,
      this.state.userTagsVisibleTags
    );
    const payload = {
      instanceType,
      hddSize: +values[EXEC_ENVIRONMENT].disk,
      timeout: +(values[ADVANCED].timeout || 0),
      cmdTemplate: cmd,
      nodeCount: this.state.launchCluster ? this.state.nodesCount : undefined,
      dockerImage: values[EXEC_ENVIRONMENT].dockerImage,
      pipelineId: this.props.pipeline ? this.props.pipeline.id : undefined,
      version: this.props.version,
      tags,
      params: parameterUtilities.parametersToPayloadParams(this.getParameters(parametersPayloadId)),
      isSpot: (values[ADVANCED].is_spot || `${this.getDefaultValue('is_spot')}`) === 'true',
      cloudRegionId: values[EXEC_ENVIRONMENT].cloudRegionId
        ? +values[EXEC_ENVIRONMENT].cloudRegionId
        : undefined,
      prettyUrl: this.prettyUrlEnabled
        ? prettyUrlGenerator.build(values[ADVANCED].prettyUrl)
        : undefined,
      notifications: (values[ADVANCED].notifications || []).slice()
    };
    if (this.isWindowsPlatform) {
      payload.node_count = undefined;
    }
    if (this.state.runNameAlias) {
      payload.runNameAlias = this.state.runNameAlias;
    }
    if ((values[ADVANCED].is_spot ||
      `${this.getDefaultValue('is_spot')}`) !== 'true' &&
      !this.state.autoScaledCluster &&
      !this.state.launchCluster &&
      !this.state.autoPause) {
      payload.nonPause = true;
    }
    const getBooleanValue = (value) => {
      if (typeof value === 'boolean') {
        return value;
      } else if (value === undefined) {
        return false;
      } else {
        return value === 'true';
      }
    };
    const conditionalParameters = (this.state.conditionalParameters || [])
      .filter(p => !p.markAsDeleted);
    if (conditionalParameters.length) {
      for (let i = 0; i < conditionalParameters.length; i++) {
        const parameter = conditionalParameters[i];
        payload.params[parameter.name] = {
          type: parameter.type,
          value: (parameter.type || '').toLowerCase() === 'boolean'
            ? getBooleanValue(parameter.value)
            : (parameter.value || ''),
          required: `${parameter.required || false}`.toLowerCase() === 'true',
          enum: parameter.initialEnumeration,
          visible: parameter.visible,
          validation: parameter.validation,
          no_override: parameter.noOverride,
          section: parameter.section
        };
      }
    }
    if (values[ADVANCED].limitMounts && !this.isWindowsPlatform) {
      payload.params[CP_CAP_LIMIT_MOUNTS] = {
        type: 'string',
        required: false,
        value: values[ADVANCED].limitMounts
      };
    }
    const launchAutoScaledCluster = this.state.launchCluster && this.state.autoScaledCluster;
    const launchAutoScaledHybridCluster = launchAutoScaledCluster &&
      this.state.hybridAutoScaledClusterEnabled;
    payload.params[CP_CAP_AUTOSCALE] = {
      type: 'boolean',
      value: launchAutoScaledCluster
    };
    payload.params[CP_CAP_AUTOSCALE_HYBRID] = {
      type: 'boolean',
      value: launchAutoScaledHybridCluster
    };
    if (launchAutoScaledCluster) {
      payload.params[CP_CAP_AUTOSCALE_WORKERS] = {
        type: 'int',
        value: +this.state.maxNodesCount
      };
      if (this.state.autoScaledPriceType) {
        payload.params[CP_CAP_AUTOSCALE_PRICE_TYPE] = {
          type: 'string',
          value: this.state.autoScaledPriceType
        };
      } else {
        delete payload.params[CP_CAP_AUTOSCALE_PRICE_TYPE];
      }
      if (this.state.gpuScalingConfiguration) {
        payload.params = applyGPUScalingParameters(
          this.state.gpuScalingConfiguration,
          payload.params
        );
      } else if (this.state.childNodeInstanceConfiguration) {
        applyChildNodeInstanceParameters(
          payload.params,
          this.state.childNodeInstanceConfiguration,
          this.state.hybridAutoScaledClusterEnabled
        );
      }
    }
    payload.params[CP_CAP_SGE] = {
      type: 'boolean',
      value: false
    };
    payload.params[CP_CAP_SPARK] = {
      type: 'boolean',
      value: false
    };
    payload.params[CP_CAP_SLURM] = {
      type: 'boolean',
      value: false
    };
    payload.params[CP_CAP_KUBE] = {
      type: 'boolean',
      value: false
    };
    if (this.state.launchCluster && this.state.gridEngineEnabled) {
      payload.params[CP_CAP_SGE] = {
        type: 'boolean',
        value: true
      };
    } else if (this.state.launchCluster && this.state.sparkEnabled) {
      payload.params[CP_CAP_SPARK] = {
        type: 'boolean',
        value: true
      };
    } else if (this.state.launchCluster && this.state.slurmEnabled) {
      payload.params[CP_CAP_SLURM] = {
        type: 'boolean',
        value: true
      };
    } else if (this.state.launchCluster && this.state.kubeEnabled) {
      payload.params[CP_CAP_KUBE] = {
        type: 'boolean',
        value: true
      };
    }
    if (this.state.launchCluster && this.state.kubeEnabled) {
      payload.params[CP_CAP_DIND_CONTAINER] = {
        type: 'boolean',
        value: true
      };
      payload.params[CP_CAP_SYSTEMD_CONTAINER] = {
        type: 'boolean',
        value: true
      };
    }
    if (this.rescheduleRun !== undefined) {
      payload.params[CP_CAP_RESCHEDULE_RUN] = {
        type: 'boolean',
        value: !!this.rescheduleRun
      };
    }
    payload.params = getParametersFromFsConfig(
      this.state.fsConfig,
      payload.params,
      this.currentCloudRegionProvider
    );
    payload.params = applyCapabilities(
      payload.params,
      this.state.runCapabilities,
      this.props.preferences,
      this.toolPlatform
    );
    if (this.props.continueRun) {
      payload.params = generateContinueRunParameters(this.props.continueRun, payload.params);
    }
    const {
      parameters: appliedReservationParameters,
      podAssignPolicy
    } = await buildLaunchParametersFromReservationParameters(
      this.state.reservationParameters,
      instanceType,
      payload.params
    );
    payload.params = appliedReservationParameters;
    payload.podAssignPolicy = podAssignPolicy;
    if (!payload.isSpot &&
      !this.state.launchCluster &&
      this.state.scheduleRules &&
      this.state.scheduleRules.length > 0) {
      payload.scheduleRules = this.state.scheduleRules;
    }
    return payload;
  };

  generateLaunchPayloads = async (values) => {
    const parametersPayloads = this.getParametersPayloads();
    if (parametersPayloads.length === 0) {
      const payload = this.getParametersPayloads() || {};
      parametersPayloads.push({
        ...payload,
        enabled: true
      });
    }
    const payloads = parametersPayloads.filter((p) => p.enabled);
    return Promise.all(
      payloads.map((p) => this.generateLaunchPayload(values, p.id))
    );
  };

  getSectionFieldDecorator = (section) => (name, ...opts) => {
    return this.props.form.getFieldDecorator(`${section}.${name}`, ...opts);
  };

  getSectionFieldValue = (section) => (name, ...opts) => {
    if (name) {
      return this.props.form.getFieldValue(`${section}.${name}`, ...opts);
    } else {
      return this.props.form.getFieldValue(section, ...opts);
    }
  };

  getSectionValue = (section, ...opts) => {
    return this.props.form.getFieldValue(section, ...opts);
  };

  getDefaultValue = (key) => {
    if (!key || !this.props.parameters) {
      return undefined;
    }
    if (key.split('.')[0] === 'instanceType') {
      return this.getInstanceTypeParameterDefaultValue(key.split('.')[1]);
    }
    if (key.split('.')[0] === 'parameters') {
      return this.getParameterDefaultValue(key.split('.')[1]);
    }
    if (key.toLowerCase() === 'is_spot') {
      if (this.props.parameters[key] !== undefined && this.props.parameters[key] !== null) {
        return `${this.props.parameters[key]}`;
      }
      return `${this.props.defaultPriceTypeIsSpot}`;
    }
    if (key.split('.')[0] === 'notifications' && this.props.parameters) {
      return (this.props.parameters.notifications || []).map(mapObservableNotification);
    }
    if (!this.props.parameters[key]) {
      return undefined;
    }
    return `${this.props.parameters[key]}`;
  };

  getInstanceTypes = (instanceTypesRequest) => {
    if (!instanceTypesRequest) {
      return [];
    }
    const instanceTypes = [];
    for (let i = 0; i < instanceTypesRequest.length; i++) {
      const instanceType = instanceTypesRequest[i];
      if (instanceTypes.filter(t => t.name === instanceType.name).length === 0) {
        instanceTypes.push(instanceType);
      }
    }
    return instanceTypes.sort((typeA, typeB) => {
      const vcpuCompared = typeA.vcpu - typeB.vcpu;
      const skuCompare = (a, b) => {
        return a.instanceFamily > b.instanceFamily
          ? 1
          : a.instanceFamily < b.instanceFamily ? -1 : 0;
      };
      return vcpuCompared === 0 ? skuCompare(typeA, typeB) : vcpuCompared;
    });
  };

  @computed
  get instanceTypesLoaded () {
    return this.props.allowedInstanceTypes && this.props.allowedInstanceTypes.loaded;
  }

  @computed
  get instanceTypes () {
    const request = this.props.allowedInstanceTypes && this.props.allowedInstanceTypes.loaded
      ? this.props.allowedInstanceTypes
      : null;
    if (request) {
      if (this.state.pipeline) {
        return this.getInstanceTypes(request.value[this.props.instanceTypes]);
      } else {
        return this.getInstanceTypes(request.value[this.props.toolInstanceTypes]);
      }
    }
    return [];
  }

  @computed
  get instanceTypesMergedForRegions () {
    return this.props.allowedInstanceTypes &&
      this.props.allowedInstanceTypes.regionsMerged;
  }

  @computed
  get priceTypes () {
    let availableMasterNodeTypes = [true, false];
    if (this.state.launchCluster && this.props.preferences.loaded) {
      availableMasterNodeTypes = this.props.preferences.allowedMasterPriceTypes;
    }
    if (!this.props.allowedInstanceTypes || !this.props.allowedInstanceTypes.loaded) {
      return availableMasterNodeTypes;
    }
    return (this.props.allowedInstanceTypes.value[names.allowedPriceTypes] || [])
      .map(v => {
        if (v === 'spot') {
          return true;
        } else if (v === 'on_demand') {
          return false;
        }
        return undefined;
      })
      .filter(v => v !== undefined && availableMasterNodeTypes.indexOf(v) >= 0);
  }

  getInstanceTypeParameterDefaultValue = (key) => {
    if (!this.props.parameters) {
      return undefined;
    }
    const type = this.props.parameters['instance_size'];
    const [instanceType] = this.instanceTypes.filter(t => t.name === type);
    if (key && instanceType) {
      return `${instanceType[key]}`;
    }
    return undefined;
  };

  getParameterDefaultValue = (key) => {
    if (!this.props.parameters) {
      return undefined;
    }
    if (this.props.parameters.parameters) {
      for (let pKey in this.props.parameters.parameters) {
        if (this.props.parameters.parameters.hasOwnProperty(pKey) && pKey === key) {
          return this.props.parameters.parameters[pKey].value;
        }
      }
    }
    return undefined;
  };

  diskSizeChanged = ({target: {value}}) => {
    this.evaluateEstimatedPrice({disk: value});
  };

  prepare = (updateFireCloud = false) => {
    const autoScaledCluster = autoScaledClusterEnabled(this.props.parameters.parameters);
    const hybridAutoScaledCluster = hybridAutoScaledClusterEnabled(
      this.props.parameters.parameters
    );
    const gpuScalingConfiguration = readGPUScalingPreference(
      {
        autoScaled: autoScaledCluster,
        provider: this.currentCloudRegionProvider,
        hybrid: hybridAutoScaledCluster,
        parameters: this.props.parameters.parameters
      },
      this.props.preferences
    );
    const childNodeInstanceConfiguration = parseChildNodeInstanceConfiguration({
      autoScaled: autoScaledCluster,
      gpuScaling: !!gpuScalingConfiguration,
      hybrid: hybridAutoScaledCluster,
      parameters: this.props.parameters.parameters
    });
    const gridEngineEnabledValue = gridEngineEnabled(this.props.parameters.parameters);
    const sparkEnabledValue = sparkEnabled(this.props.parameters.parameters);
    const slurmEnabledValue = slurmEnabled(this.props.parameters.parameters);
    const kubeEnabledValue = kubeEnabled(this.props.parameters.parameters);
    const autoScaledPriceTypeValue = getAutoScaledPriceTypeValue(this.props.parameters.parameters);
    const fsConfigValue = getFsConfigFromParameters(this.props.parameters.parameters);
    const runCapabilities = getEnabledCapabilities(this.props.parameters.parameters);
    let state = {
      launchCluster: +this.props.parameters.node_count > 0 || autoScaledCluster,
      autoScaledCluster: autoScaledCluster,
      hybridAutoScaledClusterEnabled: hybridAutoScaledCluster,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration,
      gridEngineEnabled: gridEngineEnabledValue,
      sparkEnabled: sparkEnabledValue,
      slurmEnabled: slurmEnabledValue,
      kubeEnabled: kubeEnabledValue,
      autoScaledPriceType: autoScaledPriceTypeValue,
      fsConfig: fsConfigValue,
      runCapabilities,
      nodesCount: +this.props.parameters.node_count,
      maxNodesCount: this.props.parameters.parameters &&
      this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS]
        ? +this.props.parameters.parameters[CP_CAP_AUTOSCALE_WORKERS].value
        : 0,
      pipeline: this.props.pipeline,
      version: this.props.version,
      pipelineConfiguration: this.props.pipelineConfiguration
    };
    if (updateFireCloud) {
      state = Object.assign(state, {
        fireCloudMethodName: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.name : null,
        fireCloudMethodNamespace: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.namespace : null,
        fireCloudMethodSnapshot: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.snapshot : null,
        fireCloudMethodConfiguration: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.configuration : null,
        fireCloudMethodConfigurationSnapshot: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.configurationSnapshot : null,
        fireCloudInputs: {},
        fireCloudOutputs: {},
        fireCloudDefaultInputs: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.methodInputs
          : null,
        fireCloudDefaultOutputs: this.props.fireCloudMethod
          ? this.props.fireCloudMethod.methodOutputs
          : null
      });
    }
    this.setState(state);
  };

  evaluateEstimatedPrice = async ({disk, type, isSpot, cloudRegionId}) => {
    if (!disk) {
      disk = this.getSectionFieldValue(EXEC_ENVIRONMENT)('disk') ||
        this.getDefaultValue('instance_disk');
    }
    if (!type) {
      this.props.allowedInstanceTypes &&
      await this.props.allowedInstanceTypes.fetchIfNeededOrWait();
      type = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type') ||
        this.correctInstanceTypeValue(this.getDefaultValue('instance_size'));
    }
    if (!isSpot) {
      isSpot = this.getSectionFieldValue(ADVANCED)('is_spot') || this.getDefaultValue('is_spot');
    }
    if (!cloudRegionId) {
      cloudRegionId = this.getSectionFieldValue(EXEC_ENVIRONMENT)('cloudRegionId') ||
        this.getDefaultValue('cloudRegionId') || this.defaultCloudRegionId;
    }
    isSpot = `${isSpot}` === 'true';
    if (!isNaN(disk) && type && !this.state.estimatedPrice.pending) {
      const request = this.props.pipeline
        ? new PipelineRunEstimatedPrice(
          this.props.pipeline.id,
          this.props.version,
          this.props.currentConfigurationName
        )
        : new PipelineRunEstimatedPrice();
      const estimatedPriceState = this.state.estimatedPrice;
      estimatedPriceState.pending = true;
      this.setState({estimatedPrice: estimatedPriceState}, async () => {
        await request.send({
          'instanceType': type,
          'instanceDisk': disk,
          'spot': isSpot,
          'regionId': cloudRegionId
        });
        estimatedPriceState.pending = false;
        if (!request.error) {
          const adjustPrice = (value) => {
            let cents = Math.ceil(value * 100);
            if (cents < 1) {
              cents = 1;
            }
            return cents / 100;
          };
          estimatedPriceState.evaluated = true;
          estimatedPriceState.isValid = request.value.diskPricePerHour > 0 &&
            request.value.computePricePerHour > 0;
          estimatedPriceState.averagePrice = adjustPrice(request.value.averageTimePrice);
          estimatedPriceState.maximumPrice = adjustPrice(request.value.maximumTimePrice);
          estimatedPriceState.minimumPrice = adjustPrice(request.value.minimumTimePrice);
          estimatedPriceState.pricePerHour = adjustPrice(request.value.pricePerHour);
        }
        this.setState({estimatedPrice: estimatedPriceState});
      });
    }
  };

  instanceTypeChanged = (newType) => {
    const [instanceType] = this.instanceTypes.filter(t => t.name === newType);
    if (instanceType) {
      try {
        const formField = `${EXEC_ENVIRONMENT}.cloudRegionId`;
        const currentRegion = this.props.form.getFieldValue(formField);
        const regionId = this.correctCloudRegion(
          currentRegion ||
          this.defaultCloudRegionId
        );
        const {
          regionId: iRegionId,
          regionIds: iRegionIds = [iRegionId]
        } = instanceType;
        const changed = iRegionIds.length > 0 &&
          !iRegionIds.some((id) => Number(id) === Number(regionId));
        if (changed) {
          const switchTo = iRegionIds[0];
          this.props.form.setFieldsValue({
            [formField]: `${switchTo}`
          });
        }
      } catch (e) {
        console.warn(e);
      }
      this.evaluateEstimatedPrice({type: instanceType.name});
    }
  };

  renderEstimatedPriceTable = (multiply = 1) => {
    const priceElements = [];
    const {pricePerHour, minimumPrice, maximumPrice, averagePrice} = this.state.estimatedPrice;
    priceElements.push({
      key: 'pricePerHour',
      title: 'Price per hour:',
      value: `${(pricePerHour * multiply).toFixed(2)} $`
    });
    if (minimumPrice) {
      priceElements.push({
        key: 'minimumTimePrice',
        title: 'Minimum price:',
        value: `${(minimumPrice * multiply).toFixed(2)} $`
      });
    }
    if (maximumPrice) {
      priceElements.push({
        key: 'maximumTimePrice',
        title: 'Maximum price:',
        value: `${(maximumPrice * multiply).toFixed(2)} $`
      });
    }
    if (averagePrice) {
      priceElements.push({
        key: 'averageTimePrice',
        title: 'Average price:',
        value: `${(averagePrice * multiply).toFixed(2)} $`
      });
    }
    return (
      <Spin spinning={this.state.estimatedPrice.pending}>
        <Row>
          <ul id="launch-pipeline-estimated-price-list">
            {
              priceElements.map(e =>
                <li
                  id={`launch-pipeline-estimated-price-list-item-${e.key}`}
                  key={e.key}><b>{e.title}</b> {e.value}</li>
              )
            }
          </ul>
        </Row>
      </Spin>);
  };
  renderEstimatedPriceInfo = () => {
    const {
      pricePerHour,
      isValid,
      averagePrice,
      pending
    } = this.state.estimatedPrice;
    const isInstanceTypeWithReservation = this.getIsInstanceTypeWithReservation();
    if (isInstanceTypeWithReservation || pending || !this.estimatedPriceSectionVisible) {
      return undefined;
    }
    let priceContent;
    let infoContent;
    if (!isValid) {
      priceContent = <span> &mdash; </span>;
      infoContent = 'Price cannot be estimated for the selected node type / disk configuration';
    } else if (averagePrice > 0) {
      priceContent = (
        <JobEstimatedPriceInfo>
          {(pricePerHour * this.multiplyValueBy).toFixed(2)} $
        </JobEstimatedPriceInfo>
      );
      infoContent = this.renderEstimatedPriceTable(this.multiplyValueBy);
    } else if (pricePerHour > 0) {
      priceContent = (
        <JobEstimatedPriceInfo>
          {(pricePerHour * this.multiplyValueBy).toFixed(2)} $
        </JobEstimatedPriceInfo>
      );
    }
    return (
      <span>
        Estimated price per hour:
        <span className={classNames(
          styles.price,
          {'cp-text-not-important': pending}
        )}>
          {priceContent}
        </span>
        {infoContent ? (
          <Popover
            placement="bottom"
            content={infoContent}
            trigger="hover">
            <Icon
              className={styles.hint}
              type="info-circle"
            />
          </Popover>
        ) : null}
      </span>
    );
  };
  selectBucketPath = (path) => {
    const key = this.state.bucketPathParameterKey;
    const sectionName = this.state.bucketPathParameterSection;
    if (key && sectionName) {
      const parametersValue = this.getSectionValue(sectionName);
      parametersValue.params[key].value = path;
      this.props.form.setFieldsValue({[sectionName]: parametersValue});
      this.props.form.validateFieldsAndScroll();
      this.closeBucketBrowser();
    }
  };

  cmdTemplateEditorValueChanged = (code) => {
    const advancedValues = this.getSectionValue(ADVANCED) || {};
    advancedValues.cmdTemplate = code;
    this.cmdTemplateValue = code;
    this.props.form.setFieldsValue({[ADVANCED]: advancedValues});
  };

  parameterIndexIdentifier = {
    system: 0,
    nonSystem: 0
  };

  openBucketBrowser = (sectionName, key, value, type) => {
    this.setState({
      bucketBrowserVisible: true,
      bucketBrowserAllowUpload: type !== 'output',
      bucketPath: value,
      bucketPathParameterKey: key,
      bucketPathParameterSection: sectionName,
      showOnlyFolderInBucketBrowser: type === 'output',
      allowBucketSelectionInBucketBrowser: /^path$/i.test(type),
      parameterType: type
    });
  };

  closeBucketBrowser = () => {
    this.setState({
      bucketBrowserVisible: false,
      bucketBrowserAllowUpload: false,
      bucketPath: null,
      bucketPathParameterKey: null,
      bucketPathParameterSection: null,
      showOnlyFolderInBucketBrowser: false,
      allowBucketSelectionInBucketBrowser: false,
      parameterType: undefined
    });
  };

  openPipelineBrowser = () => {
    if (this.pipelineInput) {
      this.pipelineInput.blur();
    }
    this.setState({pipelineBrowserVisible: true});
  };

  closePipelineBrowser = () => {
    this.setState({pipelineBrowserVisible: false}, this.formFieldsChanged);
  };

  selectPipelineConfirm = async (pipeline, isFireCloud = false) => {
    return new Promise((resolve) => {
      const selectPipeline = () => this.selectPipeline(pipeline, isFireCloud);
      Modal.confirm({
        title: 'Are you sure you want to change configuration?',
        style: {
          wordWrap: 'break-word'
        },
        content: 'Current parameters and values may be lost.',
        async onOk () {
          selectPipeline();
          resolve(true);
        },
        onCancel () {
          resolve(false);
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    });
  };

  selectPipeline = (pipeline, isFireCloud = false) => {
    if (isFireCloud) {
      if (pipeline.name &&
        pipeline.namespace &&
        pipeline.snapshot) {
        if (this.state.fireCloudMethodName !== pipeline.name ||
          this.state.fireCloudMethodNamespace !== pipeline.namespace ||
          this.state.fireCloudMethodSnapshot !== pipeline.snapshot ||
          this.state.fireCloudMethodConfiguration !== pipeline.configuration ||
          this.state.fireCloudMethodConfigurationSnapshot !== pipeline.configurationSnapshot) {
          this.setState({
            fireCloudMethodName: pipeline.name,
            fireCloudMethodNamespace: pipeline.namespace,
            fireCloudMethodSnapshot: pipeline.snapshot,
            fireCloudMethodConfiguration: pipeline.configuration,
            fireCloudMethodConfigurationSnapshot: pipeline.configurationSnapshot,
            fireCloudInputs: {},
            fireCloudOutputs: {},
            fireCloudInputsErrors: {},
            fireCloudOutputsErrors: {},
            fireCloudDefaultInputs: [],
            fireCloudDefaultOutputs: [],
            pipeline: null,
            version: null,
            configuration: null
          }, () => {
            if (this.props.onSelectPipeline) {
              this.props.onSelectPipeline({
                fireCloudMethodName: pipeline.name,
                fireCloudMethodNamespace: pipeline.namespace,
                fireCloudMethodSnapshot: pipeline.snapshot,
                fireCloudMethodConfiguration: pipeline.configuration,
                fireCloudMethodConfigurationSnapshot: pipeline.configurationSnapshot,
                isFireCloud: true
              }, () => {
                this.prevParameters = this.props.form.getFieldsValue().parameters;
                this.reset(true);
                this.evaluateEstimatedPrice({});
              });
            }
          });
        }
      } else {
        this.setState({
          fireCloudMethodName: null,
          fireCloudMethodNamespace: null,
          fireCloudMethodSnapshot: null,
          fireCloudMethodConfiguration: null,
          fireCloudMethodConfigurationSnapshot: null,
          fireCloudInputs: {},
          fireCloudOutputs: {},
          fireCloudInputsErrors: {},
          fireCloudOutputsErrors: {},
          fireCloudDefaultInputs: [],
          fireCloudDefaultOutputs: []
        }, () => {
          if (this.props.onSelectPipeline) {
            this.props.onSelectPipeline(null, () => {
              this.reset(true);
            });
          }
        });
      }
    } else if (pipeline) {
      const [existedPipeline] = this.props.pipelines.filter(p => p.id === pipeline.id);
      if (existedPipeline) {
        const hide = message.loading('Updating configuration...', 0);
        this.setState({
          pipeline: existedPipeline,
          version: pipeline.version,
          pipelineChanged: true,
          pipelineConfiguration: pipeline.configuration,
          fireCloudMethodName: null,
          fireCloudMethodNamespace: null,
          fireCloudMethodSnapshot: null,
          fireCloudMethodConfiguration: null,
          fireCloudMethodConfigurationSnapshot: null,
          fireCloudInputs: {},
          fireCloudOutputs: {},
          fireCloudInputsErrors: {},
          fireCloudOutputsErrors: {},
          fireCloudDefaultInputs: [],
          fireCloudDefaultOutputs: []
        }, () => {
          if (this.props.onSelectPipeline) {
            this.props.onSelectPipeline({
              pipeline: existedPipeline,
              version: pipeline.version,
              configuration: pipeline.configuration
            }, (anError) => {
              hide();
              if (anError) {
                message.error(anError, 5);
              } else {
                this.prevParameters = this.props.form.getFieldsValue().parameters;
                this.reset(true);
                this.evaluateEstimatedPrice({});
              }
            });
          } else {
            hide();
          }
        });
      }
    } else {
      this.setState({
        pipeline: null,
        version: null,
        configuration: null
      }, () => {
        if (this.props.onSelectPipeline) {
          this.props.onSelectPipeline(null, () => {
            this.reset(true);
          });
        }
      });
    }
    this.closePipelineBrowser();
    this.formFieldsChanged();
  };

  openMetadataBrowser = () => {
    if (this.pipelineInput) {
      this.pipelineInput.blur();
    }
    this.setState({metadataBrowserVisible: true});
  };

  closeMetadataBrowser = () => {
    this.setState({metadataBrowserVisible: false});
  };

  selectMetadataConfirm = (entitiesIds, metadataClass, expansionExpression, folderId) => {
    this.run(
      {key: this.state.currentLaunchKey},
      entitiesIds,
      metadataClass,
      expansionExpression,
      folderId
    );
  };

  renderPipelineSelection = () => {
    if (!this.props.detached) {
      return undefined;
    }
    let inputValue;
    const {
      pipeline,
      version,
      pipelineConfiguration
    } = this.state;
    const isLatestVersion = !!pipeline && !!version && /^latest$/i.test(version);
    const onRefreshClick = () => {
      const {id} = pipeline || {};
      if (id !== undefined && id !== null) {
        const selectPipeline = () => this.selectPipeline(
          {id, version, configuration: pipelineConfiguration}
        );
        Modal.confirm({
          title: 'Are you sure you want to refresh configuration?',
          style: {
            wordWrap: 'break-word'
          },
          content: 'Current parameters and values may be lost.',
          onOk: selectPipeline,
          okText: 'Yes',
          cancelText: 'No'
        });
      }
    };
    if (pipeline) {
      inputValue = pipeline.name;
      if (version && !pipeline.unknown) {
        let versionStr = `(${version})`;
        if (pipelineConfiguration) {
          versionStr = `(${version} - ${pipelineConfiguration})`;
        }
        inputValue = `${inputValue} ${versionStr}`;
      }
    } else if (this.state.fireCloudMethodName &&
      this.state.fireCloudMethodNamespace &&
      this.state.fireCloudMethodSnapshot) {
      inputValue = `${this.state.fireCloudMethodNamespace}/${this.state.fireCloudMethodName}`;
      if (this.state.fireCloudMethodConfiguration) {
        inputValue = `${inputValue} (${this.state.fireCloudMethodConfiguration})`;
      }
    }
    const ref = (input) => {
      if (input && input.refs && input.refs.input) {
        this.pipelineInput = input.refs.input;
      }
    };
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'pipeline')}
        {...this.formItemLayout}
        label={this.localizedString('Pipeline')} >
        <Input
          size="large"
          disabled={this.props.readOnly && !this.props.canExecute}
          ref={ref}
          onFocus={this.openPipelineBrowser}
          value={inputValue}
          onChange={(e) => {}}
          addonBefore={
            <div
              className={styles.pathType}
              onClick={!(this.props.readOnly && !this.props.canExecute) &&
              this.openPipelineBrowser}>
              <Icon type="export" />
            </div>
          }
          addonAfter={isLatestVersion ? (
            <div className={styles.inputAddonButton} onClick={onRefreshClick}>
              Refresh configuration
            </div>
          ) : undefined}
        />
      </FormItem>
    );
  };

  renderExecutionEnvironmentSelection = () => {
    if (!this.props.detached) {
      return undefined;
    }
    const onChange = (key) => {
      let isDts = false;
      let [execEnvSelectValue, dtsId] = key.split('.');
      dtsId = +dtsId || null;
      if (execEnvSelectValue === DTS_ENVIRONMENT && dtsId) {
        isDts = true;
        execEnvSelectValue = `${execEnvSelectValue}.${dtsId}`;
      }
      this.setState({isDts, execEnvSelectValue, dtsId});
    };
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'executionEnvironment')}
        {...this.formItemLayout}
        label="Execution environment" >
        <Select
          size="large"
          value={`${this.state.execEnvSelectValue}`}
          onSelect={onChange}
          disabled={
            (this.props.readOnly && !this.props.canExecute) || (!!this.state.fireCloudMethodName)
          }>
          {
            !!this.state.fireCloudMethodName &&
            <Select.Option key={FIRE_CLOUD_ENVIRONMENT}>
              FireCloud
            </Select.Option>
          }
          <Select.Option key={CLOUD_PLATFORM_ENVIRONMENT}>
            {this.props.preferences.deploymentName || 'EPAM Cloud Pipeline'}
          </Select.Option>
          {
            this.dtsList && this.dtsList.map(dts =>
              <Select.Option key={`${DTS_ENVIRONMENT}.${dts.id}`}>
                {dts.name}
              </Select.Option>
            )
          }
        </Select>
      </FormItem>
    );
  };

  @observable
  _dtsClusterInfo = null;
  _dtsCoresTotal = 0;
  _dtsCoresAvailable = 0;

  loadDtsClusterInfo = () => {
    this._dtsClusterInfo = new DTSClusterInfo(this.state.dtsId);
  };

  renderDtsClusterInfo = () => {
    let infoString = '';
    if (!this._dtsClusterInfo) {
      return infoString;
    }
    if (this._dtsClusterInfo.error) {
      infoString = <Alert type="error" message={this._dtsClusterInfo.error} />;
    }
    const nodes = this._dtsClusterInfo.value.nodes;
    if (!this._dtsClusterInfo.error && nodes && nodes.length) {
      nodes.forEach(node => {
        if (!this._dtsCoresTotal || this._dtsCoresTotal < node.slotsTotal) {
          this._dtsCoresTotal = node.slotsTotal;
        }
        const available = node.slotsTotal - node.slotsUsed;
        if (!this._dtsCoresAvailable || this._dtsCoresAvailable < available) {
          this._dtsCoresAvailable = available;
        }
      });
      if (this._dtsCoresTotal) {
        const isPlural = this._dtsCoresTotal !== 1;
        const totalStr = `${this._dtsCoresTotal} core${isPlural ? 's' : ''} total`;
        const availableStr = `${this._dtsCoresAvailable} available`;
        infoString = `${totalStr} / ${availableStr}`;
      }
    }
    return [
      <Col key="info" style={{paddingLeft: 7, flex: 1}}>
        {infoString}
      </Col>,
      <Col key="reload" style={{textAlign: 'center', width: 30}}>
        <Button
          shape="circle"
          icon="reload"
          size="small"
          onClick={this.loadDtsClusterInfo} />
      </Col>
    ];
  };

  validateCoresNumber = (value, callback) => {
    if (!!this.state.fireCloudMethodName || !value) {
      callback();
      return;
    }
    const validation = this.state.validation;
    if (!isNaN(value)) {
      if (+value < 1) {
        validation[EXEC_ENVIRONMENT].coresNumber.result = 'error';
        validation[EXEC_ENVIRONMENT].coresNumber.message = 'Minimum value is 1';
      } else if (this._dtsCoresTotal && +value > this._dtsCoresTotal) {
        validation[EXEC_ENVIRONMENT].coresNumber.result = 'error';
        validation[EXEC_ENVIRONMENT].coresNumber.message =
          'The selected number of cores cannot be more than the total amount';
      } else if (this._dtsCoresTotal && +value > this._dtsCoresAvailable) {
        validation[EXEC_ENVIRONMENT].coresNumber.result = 'warning';
        const availableStr = `At the moment - only ${this._dtsCoresAvailable} cores are available`;
        validation[EXEC_ENVIRONMENT].coresNumber.message =
          `${availableStr}. Your job will wait in queue until more cores are freed`;
      } else {
        validation[EXEC_ENVIRONMENT].coresNumber.result = 'success';
        validation[EXEC_ENVIRONMENT].coresNumber.message = null;
      }
    }
    this.setState({validation});
    if (callback) {
      if (validation[EXEC_ENVIRONMENT].coresNumber.result === 'warning') {
        callback();
      } else {
        callback(validation[EXEC_ENVIRONMENT].coresNumber.message || undefined);
      }
    }
  };

  renderCoresFormItem = () => {
    if (!this.props.detached || !this.state.isDts) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'coresNumber')}
        {...this.formItemLayout}
        label="Cores"
        hasFeedback>
        <Row type="flex" align="start">
          <div style={{flex: 1, display: 'flex', flexDirection: 'row'}}>
            <FormItem
              className={styles.formItem}
              {...this.formItemLayout}
              validateStatus={this.state.validation[EXEC_ENVIRONMENT].coresNumber.result}
              help={this.state.validation[EXEC_ENVIRONMENT].coresNumber.message}
              hasFeedback>
              {this.getSectionFieldDecorator(EXEC_ENVIRONMENT)('coresNumber',
                {
                  rules: [
                    {
                      pattern: /^\d+(\.\d+)?$/,
                      message: 'Please enter a valid positive number'
                    },
                    {
                      validator: (rule, value, callback) =>
                        this.validateCoresNumber(value, callback)
                    }
                  ],
                  initialValue: this.getDefaultValue('coresNumber')
                }
              )(
                <Input
                  disabled={this.props.readOnly && !this.props.canExecute} />
              )}
            </FormItem>
          </div>
          {
            this._dtsClusterInfo && this._dtsClusterInfo.pending
              ? <div style={{flex: 1}}><LoadingView /></div>
              : this.renderDtsClusterInfo()
          }
        </Row>
      </FormItem>
    );
  };

  isSystemParameter = (parameter) => {
    if (this.props.runDefaultParameters.loaded) {
      return (this.props.runDefaultParameters.value || [])
        .filter(p => p.name.toUpperCase() === (parameter.name || '').toUpperCase()).length > 0;
    }
    return false;
  };

  validateUserTags = async (payload = this.launchCommandPayload) => new Promise(async (resolve) => {
    let result = [];
    let visibleTags = [];
    if (
      !this.props.detached &&
      !this.props.isDetachedConfiguration &&
      !this.props.editConfigurationMode
    ) {
      const {userTags} = this.state;
      result = await getUserTagsValidationResult(userTags, {launchPayload: payload});
      visibleTags = await getVisibleUserTags(payload);
    }
    this.setState({
      userTagsValidation: result,
      userTagsVisibleTags: visibleTags,
      userTagsValidationPayload: payload
    }, () => {
      resolve(!result || result.length === 0);
    });
  });

  @computed
  get authenticatedUserRolesNames () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return [];
    }
    const {
      roles = []
    } = this.props.authenticatedUserInfo.value;
    return roles.map(r => r.name);
  }

  @computed
  get isAdmin () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    const {
      admin
    } = this.props.authenticatedUserInfo.value;
    return admin;
  }

  @computed
  get isAdvancedUser () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    const {
      roles = []
    } = this.props.authenticatedUserInfo.value;
    return roles.find(r => /^ROLE_ADVANCED_USER$/i.test(r.name));
  }

  isSystemParameterRestrictedByRole = (parameter) => {
    if (
      parameter &&
      this.isSystemParameter(parameter) &&
      !this.isAdmin
    ) {
      const [systemParam] = (this.props.runDefaultParameters.value || [])
        .filter(p => p.name.toUpperCase() === (parameter.name || '').toUpperCase());
      if (systemParam && systemParam.roles && systemParam.roles.length > 0) {
        return !(
          systemParam.roles
            .some(roleName => this.authenticatedUserRolesNames.includes(roleName))
        );
      }
    }
    return false;
  };

  getSystemParameter = (parameter) => {
    if (parameter && parameter.name && this.props.runDefaultParameters.loaded) {
      return (this.props.runDefaultParameters.value || [])
        .filter(p => p.name === parameter.name)[0];
    }
    return null;
  };

  onChangeRootEntity = (rootEntityId = null) => {
    this.setState({rootEntityId}, this.formFieldsChanged);
  };

  renderParameters = (system = false) => {
    const parameters = this.getParameters();
    const {isRawEditEnabled, pipeline} = this.state;
    const {detached} = this.props;
    const pipelineSelected = pipeline !== undefined && pipeline !== null;
    let description;
    if (!system) {
      const {
        config_description: configurationDescription
      } = this.props.parameters || {};
      description = configurationDescription;
    }
    return [
      <Parameters
        key={`${system ? 'system' : 'default'}-parameters`}
        disabled={this.props.readOnly && !this.props.canExecute}
        parameters={parameters}
        onChange={this.onParametersChange}
        system={system}
        rawEdit={isRawEditEnabled}
        editConfiguration={this.props.editConfigurationMode}
        currentCloudRegionId={this.currentCloudRegionId}
        currentProjectId={this.state.currentProjectId}
        currentProjectMetadata={this.state.currentProjectMetadata}
        currentMetadataEntity={this.state.currentMetadataEntity}
        rootEntityId={this.state.rootEntityId}
        onChangeRootEntityId={this.onChangeRootEntity}
        showRootEntityId={!system && this.props.isDetachedConfiguration}
        metadataAutoComplete={this.props.isDetachedConfiguration}
        navigationStyle={this.state.navigationStyle}
        navigationRef={system ? undefined : (div) => {
          this.parametersNavigationWrapperRef = div;
        }}
        detached={detached}
        pipeline={pipelineSelected}
        description={description ? (<Markdown md={description} />) : undefined}
      />,
      <div
        key={`add-${system ? 'system' : 'default'}-parameter`}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginTop: 10
        }}
      >
        <AddParameterButton
          key={`add-${system ? 'system' : 'default'}-parameter`}
          parameters={parameters}
          onChange={this.onParametersChange}
          system={system}
          disabled={(this.props.readOnly && !this.props.canExecute) || (!!detached && !!pipeline)}
        />
      </div>
    ];
  };

  @computed
  get multiplyValueBy () {
    if (this.state.launchCluster) {
      return (this.state.nodesCount || 0) + 1;
    } else {
      return 1;
    }
  }

  @computed
  get maxMultiplyValueBy () {
    if (this.state.launchCluster) {
      let value = this.state.maxNodesCount;
      if (!value || isNaN(value)) {
        value = 1;
      } else {
        value = +value;
      }
      return value + 1;
    } else {
      return 1;
    }
  }

  renderDockerImageFormItem = () => {
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'dockerImage')}
        {...this.formItemLayout}
        label="Docker image"
        required={!this.state.fireCloudMethodName}
        hasFeedback>
        {this.getSectionFieldDecorator(EXEC_ENVIRONMENT)('dockerImage',
          {
            rules: [
              {
                required: !this.state.fireCloudMethodName,
                message: 'Docker image is required'
              }
            ],
            initialValue: this.getDefaultValue('docker_image')
          }
        )(
          <DockerImageInput disabled={
            !!this.state.fireCloudMethodName ||
            (this.props.readOnly && !this.props.canExecute) ||
            (this.state.pipeline && this.props.detached)} />
        )}
      </FormItem>
    );
  };

  @computed
  get disableAutoPauseEnabled () {
    return !this.state.fireCloudMethodName &&
      !this.props.detached &&
      !this.props.editConfigurationMode;
  }

  get prettyUrlEnabled () {
    return !this.state.fireCloudMethodName && !this.props.detached;
  }

  @computed
  get prettyUrlSSHMode () {
    if (!this.prettyUrlEnabled) {
      return false;
    }
    const dockerImage = this.getSectionFieldValue(EXEC_ENVIRONMENT)('dockerImage') ||
      this.getDefaultValue('docker_image');
    if (dockerImage && this.props.dockerRegistries.loaded) {
      const [registry, group, toolAndVersion] = dockerImage.toLowerCase().split('/');
      const [imageRegistry] = (this.props.dockerRegistries.value.registries || [])
        .filter(r => r.path.toLowerCase() === registry);
      if (imageRegistry) {
        const [imageGroup] = (imageRegistry.groups || [])
          .filter(g => g.name.toLowerCase() === group);
        if (imageGroup) {
          const [image] = toolAndVersion.split(':');
          const [im] = (imageGroup.tools || [])
            .filter(i => i.image.toLowerCase() === `${group}/${image}`);
          return !(im && im.endpoints && (im.endpoints || []).length > 0);
        }
      }
    }
    return true;
  }

  checkFriendlyURL = (rule, value, callback) => {
    const error = prettyUrlGenerator.validate(value, this.prettyUrlSSHMode);
    if (error) {
      callback(error);
    }
    callback();
  };

  renderPrettyUrlFormItem = () => {
    if (this.prettyUrlEnabled && this.friendlyUrlAvailable()) {
      const sshMode = this.prettyUrlSSHMode;
      return (
        <FormItem
          className={getFormItemClassName(styles.formItemRow, 'prettyUrl')}
          {...this.leftFormItemLayout}
          label="Friendly URL"
          hasFeedback>
          <Col span={10}>
            <FormItem
              className={styles.formItemRow}
              hasFeedback
            >
              {this.getSectionFieldDecorator(ADVANCED)('prettyUrl',
                {
                  rules: [
                    {
                      validator: this.checkFriendlyURL
                    }
                  ],
                  initialValue: prettyUrlGenerator.parse(this.getDefaultValue('prettyUrl'))
                }
              )(
                <Input
                  disabled={(this.props.readOnly && !this.props.canExecute)} />
              )}
            </FormItem>
          </Col>
          <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
            {
              hints.renderHint(
                this.localizedStringWithSpotDictionaryFn,
                sshMode ? hints.prettySSHUrlHint : hints.prettyUrlHint
              )
            }
          </Col>
        </FormItem>
      );
    }
    return undefined;
  };

  renderEndpointNameFormItem = () => {
    if (this.props.detached && this.props.editConfigurationMode) {
      return (
        <FormItem
          className={getFormItemClassName(styles.formItemRow, 'endpointName')}
          {...this.leftFormItemLayout}
          label="Endpoint Name"
          hasFeedback>
          <Col span={10}>
            <FormItem
              className={styles.formItemRow}
              hasFeedback
            >
              {this.getSectionFieldDecorator(ADVANCED)('endpointName',
                {
                  initialValue: this.getDefaultValue('endpointName')
                }
              )(
                <Input
                  disabled={(this.props.readOnly && !this.props.canExecute)} />
              )}
            </FormItem>
          </Col>
          <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
            {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.endpointNameHint)}
          </Col>
        </FormItem>
      );
    }
    return undefined;
  };

  cpuMapper = cpu => this.hyperThreadingDisabled && !Number.isNaN(Number(cpu))
    ? (cpu / 2.0)
    : cpu;

  renderInstanceTypeSelection = () => {
    if (this.state.isDts && this.props.detached) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'type')}
        {...this.formItemLayout}
        required={!this.state.fireCloudMethodName && !this.state.isDts}
        label="Node type"
        hasFeedback>
        {this.getSectionFieldDecorator(EXEC_ENVIRONMENT)('type',
          {
            rules: [
              {
                required: !this.state.fireCloudMethodName && !this.state.isDts,
                message: 'Node type is required'
              }
            ],
            initialValue: this.correctInstanceTypeValue(this.getDefaultValue('instance_size'))
          }
        )(
          <Select
            disabled={
              !!this.state.fireCloudMethodName ||
              (this.props.readOnly && !this.props.canExecute) ||
              (
                this.props.allowedInstanceTypes &&
                (this.props.allowedInstanceTypes.changed || this.props.allowedInstanceTypes.pending)
              )}
            showSearch
            allowClear={false}
            placeholder="Node type"
            optionFilterProp="children"
            onChange={this.instanceTypeChanged}
            filterOption={
              (input, option) =>
                (option.props.searchValue || option.props.value)
                  .toLowerCase().indexOf(input.toLowerCase()) >= 0}>
            {
              getSelectOptions(
                this.instanceTypes,
                {
                  hyperThreadingDisabled: this.hyperThreadingDisabled,
                  displayRegion: this.instanceTypesMergedForRegions,
                  preferences: this.props.preferences,
                  showReservationTag: !this.props.detached
                }
              )
            }
          </Select>
        )}
      </FormItem>
    );
  };

  renderReservationParametersSelector = () => {
    const {
      detached
    } = this.props;
    if (detached) {
      return null;
    }
    const instanceTypeValue = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type');
    const instanceType = this.instanceTypes.find(t => t.name === instanceTypeValue);
    const {
      reservationParameters
    } = this.state;
    const onChange = (p) => {
      this.setState({
        reservationParameters: p
      }, this.formFieldsChanged);
    };
    return (
      <ReservationParameters
        className={styles.reservationParameters}
        instanceType={instanceType}
        parameters={reservationParameters}
        onChange={onChange}
      />
    );
  };

  resetToolSettings = () => {
    this._toolSettings = null;
    this.regionDisabledByToolSettings = false;
    this.toolCloudRegion = null;
    this.toolPlatform = null;
    this.toolAllowSensitive = true;
    this.toolDefaultCmd = undefined;
    this.rescheduleRun = undefined;
    this.rescheduleRunInitialValue = undefined;
    this.setState({
      useDefaultCmd: false
    });
  };

  lastConfirmedImage;

  loadToolSettings = async (dockerImage) => {
    await this.props.dockerRegistries.fetchIfNeededOrWait();
    if (this.props.dockerRegistries.loaded && !this.toolSettingsPending) {
      const [registry, group, toolAndVersion] = dockerImage.toLowerCase().split('/');
      const [imageRegistry] = (this.props.dockerRegistries.value.registries || [])
        .filter(r => r.path.toLowerCase() === registry);
      if (imageRegistry) {
        const [imageGroup] = (imageRegistry.groups || [])
          .filter(g => g.name.toLowerCase() === group);
        if (imageGroup) {
          const [image, version] = toolAndVersion.split(':');
          const [im] = (imageGroup.tools || [])
            .filter(i => i.image.toLowerCase() === `${group}/${image}`);
          if (im && im.id) {
            this.toolAllowSensitive = im.allowSensitive;
            this.toolPlatform = im.platform;
            this.toolSettingsPending = true;
            this._toolSettings = new LoadToolVersionSettings(im.id, version);
            await this._toolSettings.fetchIfNeededOrWait();

            if (this._toolSettings && this._toolSettings.loaded && this._toolSettings.value &&
              this._toolSettings.value[0] && this._toolSettings.value[0].settings &&
              this._toolSettings.value[0].settings[0].configuration &&
              this._toolSettings.value[0].settings[0].configuration.cloudRegionId) {
              this.regionDisabledByToolSettings = true;
              this.toolCloudRegion = `${
                this._toolSettings.value[0].settings[0].configuration.cloudRegionId
              }`;
            } else {
              this.regionDisabledByToolSettings = false;
              this.toolCloudRegion = null;
            }

            if (this._toolSettings && this._toolSettings.loaded && this._toolSettings.value &&
              this._toolSettings.value[0] && this._toolSettings.value[0].settings &&
              this._toolSettings.value[0].settings[0].configuration) {
              const {
                parameters: toolParameters
              } = this._toolSettings.value[0].settings[0].configuration;
              const rescheduleRun = rescheduleRunParameterValue(toolParameters);
              this.rescheduleRun = rescheduleRun;
              this.rescheduleRunInitialValue = rescheduleRun;
            } else {
              this.rescheduleRun = undefined;
              this.rescheduleRunInitialValue = undefined;
            }

            const defaultCmdRequest = new ToolDefaultCommand(im.id, version);
            await defaultCmdRequest.fetch();
            if (defaultCmdRequest.loaded) {
              this.toolDefaultCmd = defaultCmdRequest.value;
              const advancedValues = this.getSectionValue(ADVANCED) || {};
              const cmd = (advancedValues.cmdTemplate || this.getDefaultValue('cmd_template'));
              const useDefaultCmd = cmd === this.toolDefaultCmd;
              if (useDefaultCmd) {
                this.setState({
                  useDefaultCmd: true,
                  startIdle: false
                }, this.formFieldsChanged);
              } else {
                this.setState({
                  useDefaultCmd: false
                }, this.formFieldsChanged);
              }
            } else {
              this.toolDefaultCmd = undefined;
            }
            this.toolSettingsPending = false;
          } else {
            this.toolAllowSensitive = true;
          }
        } else {
          this.toolAllowSensitive = true;
        }
      } else {
        this.toolAllowSensitive = true;
      }
    }
  };

  getDefaultCloudRegionValue = () => {
    if (this.toolCloudRegion) {
      return this.toolCloudRegion;
    }

    return this.getDefaultValue('cloudRegionId') || this.defaultCloudRegionId;
  };

  getInitialCloudRegionNotAvailable = () => {
    const {getFieldValue} = this.props.form;
    const initialValue = `${this.getDefaultCloudRegionValue()}`;
    const currentValue = getFieldValue(`${EXEC_ENVIRONMENT}.cloudRegionId`);
    return (!currentValue || currentValue === initialValue) &&
      initialValue &&
      this.awsRegions.filter((region) => `${region.id}` === initialValue).length === 0;
  };

  renderAWSRegionSelection = () => {
    if (this.state.isDts && this.props.detached) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'cloudRegionId')}
        {...this.formItemLayout}
        required={this.awsRegions.length > 0 && !this.state.isDts}
        hasFeedback
        label="Cloud Region">
        {this.getSectionFieldDecorator(EXEC_ENVIRONMENT)('cloudRegionId',
          {
            rules: [
              {
                required: this.awsRegions.length > 0 && !this.state.isDts,
                message: 'Cloud region id is required'
              }
            ],
            initialValue: this.getDefaultCloudRegionValue()
          }
        )(
          <Select
            disabled={
              this.regionDisabledByToolSettings ||
              !!this.state.fireCloudMethodName ||
              (this.props.readOnly && !this.props.canExecute) ||
              (
                this.props.allowedInstanceTypes &&
                (this.props.allowedInstanceTypes.changed || this.props.allowedInstanceTypes.pending)
              )
            }
            showSearch
            allowClear={false}
            placeholder="Cloud Region"
            optionFilterProp="children"
            onSelect={(cloudRegionId) => this.evaluateEstimatedPrice({cloudRegionId})}
            filterOption={
              (input, option) =>
                option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0}>
            {
              this.getInitialCloudRegionNotAvailable() && (
                <Select.Option
                  key={this.getDefaultCloudRegionValue()}
                  name="Not available"
                  title="Not available"
                  value={this.getDefaultCloudRegionValue()}
                >
                  Not available
                </Select.Option>
              )
            }
            {
              this.awsRegions
                .map(region => {
                  return (
                    <Select.Option
                      key={`${region.id}`}
                      name={region.name}
                      title={region.name}
                      value={`${region.id}`}>
                      <AWSRegionTag
                        provider={region.provider}
                        regionUID={region.regionId}
                        style={{fontSize: 'larger'}}
                      /> {region.name}
                    </Select.Option>
                  );
                })
            }
          </Select>
        )}
      </FormItem>
    );
  };

  renderRescheduleRunControl = () => {
    if (this.props.detached || this.props.editConfigurationMode) {
      return undefined;
    }
    const {
      rescheduleRun,
      rescheduleRunInitialValue
    } = this;
    const onChange = (value) => {
      this.rescheduleRun = value;
      (this.formFieldsChanged)();
    };
    const disabled = rescheduleRunInitialValue !== undefined || (
      this.regionDisabledByToolSettings ||
      !!this.state.fireCloudMethodName ||
      (this.props.readOnly && !this.props.canExecute) ||
      (
        this.props.allowedInstanceTypes &&
        (this.props.allowedInstanceTypes.changed || this.props.allowedInstanceTypes.pending)
      ) ||
      (!this._toolSettings || this._toolSettings.pending)
    );
    return (
      <div
        className={getFormItemClassName(styles.formItem, 'rescheduleRun')}
      >
        <div
          className={styles.formItemLabelColumn}
        >
          {'\u00A0'}
        </div>
        <div
          className={styles.formItemWrapperColumn}
        >
          <RescheduleRunControl
            value={rescheduleRun}
            disabled={disabled}
            onChange={onChange}
            checkbox
          >
            Allow reschedule to different region in case of insufficient capacity
          </RescheduleRunControl>
        </div>
      </div>
    );
  };

  openConfigureClusterDialog = () => {
    this.setState({
      configureClusterDialogVisible: true
    });
  };

  closeConfigureClusterDialog = () => {
    this.setState({
      configureClusterDialogVisible: false
    }, this.formFieldsChanged);
  };

  onChangeClusterConfiguration = (configuration) => {
    const {
      launchCluster,
      autoScaledCluster,
      hybridAutoScaledClusterEnabled,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration,
      nodesCount,
      maxNodesCount,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      autoScaledPriceType,
      fsConfig
    } = configuration;
    let {runCapabilities} = this.state;
    if (kubeEnabled) {
      runCapabilities = addCapability(
        runCapabilities,
        RUN_CAPABILITIES.dinD,
        RUN_CAPABILITIES.systemD
      );
    }
    this.setState({
      launchCluster,
      autoScaledCluster,
      hybridAutoScaledClusterEnabled,
      gpuScalingConfiguration,
      childNodeInstanceConfiguration,
      gridEngineEnabled,
      sparkEnabled,
      slurmEnabled,
      kubeEnabled,
      nodesCount,
      maxNodesCount,
      autoScaledPriceType,
      fsConfig,
      runCapabilities
    }, () => {
      this.closeConfigureClusterDialog();
      const priceType = this.getSectionFieldValue(ADVANCED)('is_spot') ||
        this.getDefaultValue('is_spot');
      const priceTypeField = `${ADVANCED}.is_spot`;
      this.props.form.setFieldsValue({
        [priceTypeField]: this.correctPriceTypeValue(priceType)
      });
    });
  };

  renderExecutionEnvironmentSummary = () => {
    const instanceTypeValue = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type');
    const [instanceType] = this.instanceTypes.filter(t => t.name === instanceTypeValue);
    let cpu = 0;
    let ram = 0;
    let gpu = 0;
    if (instanceType) {
      cpu = +(instanceType.vcpu || 0);
      gpu = +(instanceType.gpu || 0);
      ram = +(instanceType.memory || 0);
    }
    let disk = +(this.getSectionFieldValue(EXEC_ENVIRONMENT)('disk') || 0);
    let maxCPU = cpu;
    let maxRAM = ram;
    let maxGPU = gpu;
    let maxDISK = disk;
    if (this.state.launchCluster && !this.state.fireCloudMethodName) {
      cpu *= this.multiplyValueBy;
      gpu *= this.multiplyValueBy;
      ram *= this.multiplyValueBy;
      disk *= this.multiplyValueBy;
      if (this.state.autoScaledCluster) {
        maxCPU *= this.maxMultiplyValueBy;
        maxGPU *= this.maxMultiplyValueBy;
        maxRAM *= this.maxMultiplyValueBy;
        maxDISK *= this.maxMultiplyValueBy;
      } else {
        maxCPU = maxRAM = maxGPU = maxDISK = 0;
      }
    } else {
      maxCPU = maxRAM = maxGPU = maxDISK = 0;
    }
    const lines = [];
    if (cpu) {
      maxCPU && maxCPU > cpu
        ? lines.push(<span>{this.cpuMapper(cpu)} - {this.cpuMapper(maxCPU)} <b>CPU</b></span>)
        : lines.push(<span>{this.cpuMapper(cpu)} <b>CPU</b></span>);
    }
    if (ram) {
      maxRAM && maxRAM > ram
        ? lines.push(<span>{ram} - {maxRAM} <b>RAM</b></span>)
        : lines.push(<span>{ram} <b>RAM</b></span>);
    }
    if (gpu) {
      maxGPU && maxGPU > gpu
        ? lines.push(<span>{gpu} - {maxGPU} <b>GPU</b></span>)
        : lines.push(<span>{gpu} <b>GPU</b></span>);
    }
    if (disk) {
      maxDISK && maxDISK > disk
        ? lines.push(<span>{disk} - {maxDISK} <b>Gb</b></span>)
        : lines.push(<span>{disk} <b>Gb</b></span>);
    }
    if (lines.length > 0) {
      return [
        <div key="summary" className={styles.summaryContainer}>
          <div className={classNames(styles.summary, 'cp-exec-env-summary')}>
            {
              lines.map((l, index) => (
                <div
                  key={index}
                  className={classNames(
                    styles.summaryItem,
                    'cp-exec-env-summary-item'
                  )}
                >
                  {l}
                </div>
              ))
            }
          </div>
        </div>,
        <div key="hint" style={{width: 30, textAlign: 'center'}}>
          {hints.renderHint(
            this.localizedStringWithSpotDictionaryFn,
            hints.executionEnvironmentSummaryHint
          )}
        </div>
      ];
    } else {
      return null;
    }
  };

  renderDiskFormItem = () => {
    if (this.state.isDts && this.props.detached) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItem, 'disk')}
        {...this.formItemLayout}
        label="Disk (Gb)"
        required={!this.state.fireCloudMethodName && !this.state.isDts}
        hasFeedback>
        {this.getSectionFieldDecorator(EXEC_ENVIRONMENT)('disk',
          {
            rules: [
              {
                pattern: /^\d+(\.\d+)?$/,
                message: 'Please enter a valid positive number'
              },
              {
                required: !this.state.fireCloudMethodName &&
                  !this.state.isDts,
                message: 'Instance disk is required'
              },
              {
                validator: (rule, value, callback) => {
                  if (!!this.state.fireCloudMethodName || this.state.isDts) {
                    callback();
                    return;
                  }
                  if (!isNaN(value)) {
                    if (+value > 15360) {
                      // eslint-disable-next-line
                      callback('Maximum value is 15360');
                      return;
                    } else if (+value < 15) {
                      // eslint-disable-next-line
                      callback('Minimum value is 15');
                      return;
                    }
                  }
                  callback();
                }
              }
            ],
            initialValue: this.getDefaultValue('instance_disk')
          }
        )(
          <Input
            disabled={
              !!this.state.fireCloudMethodName ||
              (this.props.readOnly && !this.props.canExecute)
            }
            onChange={this.diskSizeChanged} />
        )}
      </FormItem>
    );
  };

  correctInstanceTypeValue = (value) => {
    if (value !== undefined && value !== null && this.instanceTypesLoaded) {
      const v = this.instanceTypes.find(v => v.name === value);
      if (v !== undefined) {
        return v.name;
      }
      return null;
    }
    return value;
  };

  correctPriceTypeValue = (value) => {
    if (value !== undefined && value !== null) {
      let realValue = `${value}` === 'true';
      const [v] = this.priceTypes.filter(v => v === realValue);
      if (v !== undefined) {
        return `${v}`;
      } else if (this.priceTypes.length > 0) {
        return `${this.priceTypes[0]}`;
      } else {
        return undefined;
      }
    }
    return value;
  };

  correctAllowedInstanceValues = () => {
    const instanceType = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type') ||
      this.getDefaultValue('instance_size');
    const priceType = this.getSectionFieldValue(ADVANCED)('is_spot') ||
      this.getDefaultValue('is_spot');
    const instanceTypeField = `${EXEC_ENVIRONMENT}.type`;
    const priceTypeField = `${ADVANCED}.is_spot`;
    this.props.form.setFieldsValue({
      [instanceTypeField]: this.correctInstanceTypeValue(instanceType),
      [priceTypeField]: this.correctPriceTypeValue(priceType)
    });
  };

  correctCloudRegion = (value) => {
    const regionId = +value;
    const [region] = this.awsRegions.filter(r => r.id === regionId);
    return region ? `${region.id}` : this.defaultCloudRegionId;
  };

  renderScheduleControl = () => {
    const {
      editConfigurationMode,
      isDetachedConfiguration,
      preferences,
      pipeline
    } = this.props;
    const {launchCluster, scheduleRules} = this.state;
    const isSpot = `${this.getSectionFieldValue(ADVANCED)('is_spot') ||
      this.correctPriceTypeValue(this.getDefaultValue('is_spot'))}` === 'true';

    if (editConfigurationMode || isDetachedConfiguration || isSpot || launchCluster) {
      return null;
    }
    const isPipeline = !!pipeline && !!pipeline.id;
    const configuration = isPipeline
      ? preferences.pipelineJobMaintenanceConfiguration
      : preferences.toolJobMaintenanceConfiguration;
    if (!configuration.pause && !configuration.resume) {
      return null;
    }
    const onScheduleSubmit = (rules) => {
      const scheduleRules = rules.filter(r => !r.removed);
      this.setState({scheduleRules});
    };

    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'runScheduling')}
        {...this.leftFormItemLayout}
        label="Maintenance"
        hasFeedback>
        <RunSchedulingList
          allowEdit
          onSubmit={onScheduleSubmit}
          rules={scheduleRules}
          availableActions={[
            configuration.pause ? RunSchedulingList.Actions.pause : false,
            configuration.resume ? RunSchedulingList.Actions.resume : false
          ].filter(Boolean)}
        />
      </FormItem>
    );
  };

  renderPriceTypeSelection = () => {
    if (this.state.isDts && this.props.detached) {
      return undefined;
    }
    const isInstanceTypeWithReservation = this.getIsInstanceTypeWithReservation();
    const initialValue = this.correctPriceTypeValue(this.getDefaultValue('is_spot'));
    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'is_spot')}
        {...this.leftFormItemLayout}
        label="Price type"
        hasFeedback>
        <Row type="flex" align="middle">
          <Col span={10}>
            <FormItem
              className={styles.formItemRow}
              hasFeedback>
              {this.getSectionFieldDecorator(ADVANCED)('is_spot',
                {
                  rules: [
                    {
                      required: !this.state.isDts,
                      message: 'Price type is required'
                    }
                  ],
                  initialValue: initialValue !== undefined && initialValue !== null
                    ? `${initialValue}`
                    : undefined
                }
              )(
                <Select
                  onSelect={(isSpot) => this.evaluateEstimatedPrice({isSpot})}
                  disabled={
                    isInstanceTypeWithReservation ||
                    !!this.state.fireCloudMethodName ||
                    (this.props.readOnly && !this.props.canExecute) ||
                    this.props.defaultPriceTypeIsLoading
                  }
                  allowClear={false}
                  placeholder="Price type">
                  {
                    this.priceTypes.map(p => {
                      return (
                        <Select.Option key={`${p}`} value={`${p}`}>
                          {getSpotTypeName(p, this.currentCloudRegionProvider)}
                        </Select.Option>
                      );
                    })
                  }
                </Select>
              )}
            </FormItem>
          </Col>
          <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
            {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.priceTypeHint)}
          </Col>
        </Row>
      </FormItem>
    );
  };

  renderDisableAutoPauseFormItem = () => {
    if (this.disableAutoPauseEnabled) {
      const isSpot = `${this.getSectionFieldValue(ADVANCED)('is_spot') ||
        this.correctPriceTypeValue(this.getDefaultValue('is_spot'))}` === 'true';
      const {
        autoScaledCluster,
        launchCluster
      } = this.state;
      if (
        !isSpot &&
        !autoScaledCluster &&
        !launchCluster &&
        (this.isAdmin || this.isAdvancedUser)
      ) {
        const onChange = (e) => {
          this.setState({
            autoPause: e.target.checked
          }, this.formFieldsChanged);
        };
        return (
          <Row type="flex" align="middle" style={{marginTop: 10, marginBottom: 10}}>
            <Col
              xs={10}
              sm={5}
              md={4}
              lg={3}
              xl={2}
              className="cp-accent"
              style={{
                textAlign: 'right',
                paddingRight: 10
              }}>
              Auto pause:
            </Col>
            <Col xs={24} sm={16} md={15} lg={15} xl={10}>
              <Row type="flex" align="middle">
                <Col span={10}>
                  <Checkbox checked={this.state.autoPause} onChange={onChange}>
                    Enabled
                  </Checkbox>
                </Col>
                <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
                  {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.autoPauseHint)}
                </Col>
              </Row>
            </Col>
          </Row>
        );
      }
    }
    return null;
  };

  renderTimeoutFormItem = () => {
    if (this.state.isDts && this.props.detached) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'timeout')}
        {...this.leftFormItemLayout}
        label="Timeout (min)"
        hasFeedback>
        <Col span={10}>
          <FormItem
            className={styles.formItemRow}
            hasFeedback>
            {this.getSectionFieldDecorator(ADVANCED)('timeout',
              {
                rules: [
                  {
                    pattern: /^\d+(\.\d+)?$/,
                    message: 'Please enter a valid positive number'
                  }
                ],
                initialValue: this.getDefaultValue('timeout')
              }
            )(
              <Input
                disabled={
                  !!this.state.fireCloudMethodName ||
                  (this.props.readOnly && !this.props.canExecute)
                } />
            )}
          </FormItem>
        </Col>
        <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
          {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.timeOutHint)}
        </Col>
      </FormItem>
    );
  };

  renderStopAfterFormItem = () => {
    if (!this.props.detached || !this.props.editConfigurationMode) {
      return undefined;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'stopAfter')}
        {...this.leftFormItemLayout}
        label="Stop after (min)"
        hasFeedback>
        <Col span={10}>
          <FormItem
            className={styles.formItemRow}
            hasFeedback>
            {this.getSectionFieldDecorator(ADVANCED)('stopAfter',
              {
                rules: [
                  {
                    pattern: /^\d+(\.\d+)?$/,
                    message: 'Please enter a valid positive number'
                  }
                ],
                initialValue: this.getDefaultValue('stopAfter')
              }
            )(
              <Input
                disabled={
                  (this.props.readOnly && !this.props.canExecute)
                }
              />
            )}
          </FormItem>
        </Col>
        <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
          {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.stopAfterHint)}
        </Col>
      </FormItem>
    );
  };

  renderLimitMountsFormItem = () => {
    if (this.isWindowsPlatform) {
      return null;
    }
    const {
      dataStorageAvailable,
      currentUserAttributes
    } = this.props;
    if (dataStorageAvailable.loaded && currentUserAttributes.loaded) {
      const getDefaultValue = () => {
        if (this.props.parameters.parameters &&
          this.props.parameters.parameters[CP_CAP_LIMIT_MOUNTS]) {
          return this.props.parameters.parameters[CP_CAP_LIMIT_MOUNTS].value;
        }
        if (
          !this.props.isDetachedConfiguration &&
          !this.props.editConfigurationMode &&
          currentUserAttributes.hasAttribute(CP_CAP_LIMIT_MOUNTS)
        ) {
          return currentUserAttributes.getAttributeValue(
            CP_CAP_LIMIT_MOUNTS,
            this.toolAllowSensitive
          );
        }
        return null;
      };
      const defaultValue = correctLimitMountsParameterValue(
        getDefaultValue() || '',
        dataStorageAvailable.value || [],
        {
          cloudRegion: this.currentCloudRegion,
          cloudRegions: this.awsRegions
        }
      );
      let currentValue = this.props.form.getFieldValue(`${ADVANCED}.limitMounts`);
      if (currentValue === undefined) {
        currentValue = defaultValue;
      }
      const noStoragesSelected = /^none$/i.test(currentValue);
      const instanceType = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type') ||
        this.getDefaultValue('instance_size');
      const instance = this.instanceTypes.find(t => t.name === instanceType);
      const toggleDoNotMountStorages = (e) => {
        if (e.target.checked) {
          this.props.form.setFieldsValue({
            [`${ADVANCED}.limitMounts`]: 'None'
          });
        } else {
          this.props.form.setFieldsValue({
            [`${ADVANCED}.limitMounts`]: null
          });
        }
      };
      return (
        <FormItem
          className={getFormItemClassName(styles.formItemRow, 'limitMounts')}
          {...this.cmdTemplateFormItemLayout}
          label="Limit mounts">
          <div>
            <Row type="flex" align="middle">
              <Checkbox
                checked={/^none$/i.test(currentValue)}
                onChange={toggleDoNotMountStorages}
              >
                Do not mount storages
              </Checkbox>
              <div style={{marginLeft: 7, marginTop: 3}}>
                {hints.renderHint(
                  this.localizedStringWithSpotDictionaryFn,
                  hints.doNotMountStoragesHint
                )}
              </div>
            </Row>
            <Row
              type="flex"
              align="middle"
              style={{display: noStoragesSelected ? 'none' : undefined}}
            >
              <div style={{flex: 1}}>
                <FormItem
                  className={styles.formItemRow}
                >
                  {this.getSectionFieldDecorator(ADVANCED)('limitMounts',
                    {
                      initialValue: defaultValue
                    }
                  )(
                    <LimitMountsInput
                      allowSensitive={this.toolAllowSensitive}
                      disabled={
                        !!this.state.fireCloudMethodName ||
                        (this.props.readOnly && !this.props.canExecute)
                      }
                      cloudRegion={this.currentCloudRegion}
                    />
                  )}
                </FormItem>
              </div>
              <div style={{marginLeft: 7, marginTop: 3}}>
                {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.limitMountsHint)}
              </div>
            </Row>
            {
              !this.toolAllowSensitive && !noStoragesSelected && (
                <Alert
                  type="warning"
                  showIcon
                  message="Tool configuration restricts selection of sensitive storages"
                />
              )
            }
            {
              !this.props.editConfigurationMode && !noStoragesSelected && (
                <OOMCheck
                  dataStorages={
                    dataStorageAvailable.loaded
                      ? (dataStorageAvailable.value || [])
                      : []
                  }
                  limitMounts={currentValue}
                  preferences={this.props.preferences}
                  instance={instance}
                  platform={this.toolPlatform}
                />
              )
            }
          </div>
        </FormItem>
      );
    }
    return null;
  };

  renderHostedAppConfigurationItem = () => {
    if (
      this.props.detached ||
      this.props.isDetachedConfiguration ||
      this.props.editConfigurationMode
    ) {
      return null;
    }

    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'hostedApplication')}
        {...this.leftFormItemLayout}
        label="Internal DNS name"
      >
        <Col span={10}>
          <FormItem
            className={styles.formItemRow}
          >
            {
              this.getSectionFieldDecorator(ADVANCED)('hostedApplication')(
                <HostedAppConfiguration />
              )
            }
          </FormItem>
        </Col>
        <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
          {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.hostedApplicationHint)}
        </Col>
      </FormItem>
    );
  };

  renderCustomTagsConfigurationItem = () => {
    if (
      this.props.detached ||
      this.props.isDetachedConfiguration ||
      this.props.editConfigurationMode
    ) {
      return null;
    }
    const {
      userTags,
      userTagsValidation = [],
      userTagsVisibleTags = [],
      userTagsValidationPayload
    } = this.state;

    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'customTags')}
        {...this.leftFormItemLayout}
        label="Tags"
      >
        <CustomTagsControl
          tags={userTags}
          validation={userTagsValidation}
          visibleTags={userTagsVisibleTags}
          payload={userTagsValidationPayload}
          onChange={(tags) => this.setState({userTags: tags}, this.formFieldsChanged)}
          buttonText="Configure"
        />
      </FormItem>
    );
  };

  renderJobNotificationsItem = () => (
    <FormItem
      className={getFormItemClassName(styles.formItemRow, 'notifications')}
      {...this.leftFormItemLayout}
      label="Notifications"
    >
      <Col span={10}>
        <FormItem
          className={styles.formItemRow}
        >
          {this.getSectionFieldDecorator(ADVANCED)('notifications',
            {
              initialValue: this.getDefaultValue('notifications')
            }
          )(
            <JobNotifications
              disabled={
                (this.props.readOnly && !this.props.canExecute)
              }
            />
          )}
        </FormItem>
      </Col>
      <Col span={1} style={{marginLeft: 7, marginTop: 3}}>
        {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.jobNotificationsHint)}
      </Col>
    </FormItem>
  );

  renderCustomUIItem = () => {
    const {
      detached,
      editConfigurationMode,
      pipeline = {},
      version: pipelineVersion
    } = this.props;
    const {id: pipelineId} = pipeline;
    if (detached || !editConfigurationMode || !pipelineId || !pipelineVersion) {
      return null;
    }
    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'customUI')}
        {...this.leftFormItemLayout}
        label="Custom UI Pages"
      >
        <Col span={24}>
          <ConfigurePlugins
            pipelineId={pipelineId}
            pipelineVersion={pipelineVersion}
          />
        </Col>
      </FormItem>
    );
  };

  renderCmdTemplateFormItem = () => {
    const {isRawEditEnabled} = this.state;
    return (
      <FormItem
        className={getFormItemClassName(styles.formItemRow, 'cmdTemplate')}
        {...this.cmdTemplateFormItemLayout}
        label="Cmd template">
        <Row>
          <Row>
            <Checkbox
              disabled={
                !!this.state.fireCloudMethodName ||
                (this.props.readOnly && !this.props.canExecute) ||
                (this.state.pipeline && this.props.detached && !isRawEditEnabled)
              }
              onChange={(e) => this.setState(
                {
                  startIdle: e.target.checked,
                  useDefaultCmd: false
                },
                this.formFieldsChanged
              )}
              checked={this.state.startIdle}>
              Start idle
            </Checkbox>
            {hints.renderHint(this.localizedStringWithSpotDictionaryFn, hints.startIdleHint)}
          </Row>
          {
            !!this.toolDefaultCmd && (
              <Row>
                <Checkbox
                  disabled={
                    !!this.state.fireCloudMethodName ||
                    (this.props.readOnly && !this.props.canExecute) ||
                    (this.state.pipeline && this.props.detached && !isRawEditEnabled)
                  }
                  onChange={(e) => this.setState(
                    {
                      useDefaultCmd: e.target.checked,
                      startIdle: false
                    },
                    this.formFieldsChanged
                  )}
                  checked={this.state.useDefaultCmd}>
                  Use default command
                </Checkbox>
                {hints.renderHint(
                  this.localizedStringWithSpotDictionaryFn,
                  hints.useDefaultCommandHint
                )}
              </Row>
            )
          }
          {
            !this.state.startIdle && !this.state.useDefaultCmd
              ? (
                <Row>
                  <Col span={24}>
                    <FormItem
                      className={styles.formItemRow}
                      required={!this.state.fireCloudMethodName}>
                      {this.getSectionFieldDecorator(ADVANCED)('cmdTemplate',
                        {
                          rules: [{
                            required: !this.state.fireCloudMethodName,
                            message: 'Command template is required'
                          }],
                          initialValue: this.getDefaultValue('cmd_template')
                        }
                      )(
                        <Input
                          disabled={
                            (this.props.readOnly && !this.props.canExecute) ||
                            (this.state.pipeline && this.props.detached && !isRawEditEnabled)
                          }
                          className={styles.hiddenItem} />
                      )}
                      <CodeEditor
                        ref={(editor) => { this.codeEditor = editor; }}
                        readOnly={
                          !!this.state.fireCloudMethodName ||
                          (this.props.readOnly && !this.props.canExecute) ||
                          (this.state.pipeline && this.props.detached && !isRawEditEnabled)
                        }
                        className={styles.codeEditor}
                        language="shell"
                        onChange={this.cmdTemplateEditorValueChanged}
                        lineWrapping
                        defaultCode={this.getDefaultValue('cmd_template')}
                      />
                    </FormItem>
                  </Col>
                </Row>
              ) : undefined
          }
          {
            this.state.useDefaultCmd && this.toolDefaultCmd
              ? (
                <Row>
                  <Col span={24} className={styles.formItemRow}>
                    <CodeEditor
                      readOnly
                      className={styles.codeEditor}
                      language="shell"
                      lineWrapping
                      defaultCode={this.toolDefaultCmd}
                    />
                  </Col>
                </Row>
              ) : undefined
          }
        </Row>
      </FormItem>
    );
  };

  reset (keepPipeline) {
    const {resetFields} = this.props.form;
    resetFields();
    if (this.codeEditor) {
      this.codeEditor.reset();
      this.cmdTemplateValue = undefined;
    }
    this.resetState(keepPipeline);
  };

  initializeParametersNavigationCheck = () => {
    const padding = 20; // Should be equals to .parametersNavigation.sticky top
    let sticky = false;
    const check = () => {
      if (this.parametersNavigationWrapperRef) {
        const {top} = this.parametersNavigationWrapperRef
          .getBoundingClientRect();
        const s = top <= padding;
        if (s !== sticky) {
          sticky = s;
          this.setState({
            navigationStyle: s ? {position: 'fixed', top: padding} : undefined
          });
        }
      }
      this.checkRAF = requestAnimationFrame(check);
    };
    this.checkRAF = requestAnimationFrame(check);
  }

  runNameAliasChange = (name) => {
    this.setState({runNameAlias: name});
  };

  renderRunButton = () => {
    if (!this.props.detached || !this.props.canExecute) {
      return undefined;
    }

    if (this.props.canRunCluster) {
      const onDropDownSelect = ({key}) => {
        if (
          this.state.currentProjectId &&
          this.state.rootEntityId &&
          this.validateFireCloudConnections()
        ) {
          this.openMetadataBrowser();
          this.setState({currentLaunchKey: key});
        } else {
          this.run({key});
        }
      };
      const dropDownMenu = (
        <Menu onClick={onDropDownSelect} selectedKeys={[]} style={{cursor: 'pointer'}}>
          <MenuItem key={RUN_SELECTED_KEY}>Run selected</MenuItem>
          <MenuItem key={RUN_CLUSTER_KEY}>Run cluster</MenuItem>
        </Menu>
      );
      return (
        <Dropdown
          overlay={dropDownMenu}
          placement="bottomRight"
          trigger={['click']}>
          <SubmitButton
            size="small"
            id="run-configuration-button" type="primary" style={{marginRight: 10}}
            inputs={this.inputPaths}
            outputs={this.outputPaths}
            skipCheck={
              this.props.parameters.run_as &&
              this.currentUserName() !== this.props.parameters.run_as
            }
            dockerImage={this.dockerImage}>
            Run <Icon type="down" />
          </SubmitButton>
        </Dropdown>
      );
    } else {
      return (
        <SubmitButton
          size="small"
          id="run-configuration-button"
          type="primary"
          inputs={this.inputPaths}
          outputs={this.outputPaths}
          skipCheck={
            this.props.parameters.run_as &&
            this.currentUserName() !== this.props.parameters.run_as
          }
          dockerImage={this.dockerImage}
          onClick={() => {
            if (this.validateFireCloudConnections()) {
              if (this.state.currentProjectId && this.state.rootEntityId) {
                this.openMetadataBrowser();
                this.setState({currentLaunchKey: RUN_SELECTED_KEY});
              } else {
                this.run({key: RUN_SELECTED_KEY});
              }
            }
          }}
          style={{marginRight: 10}}>
          Run
        </SubmitButton>
      );
    }
  };

  getDefaultOpenedPanels = () => {
    const cmdTemplate = this.getDefaultValue('cmd_template');
    const instanceType = this.getDefaultValue('instance_size');
    const disk = this.getDefaultValue('instance_disk');
    const dockerImage = this.getDefaultValue('docker_image');
    const panels = this.state.openedPanels || [];
    if (!cmdTemplate || !cmdTemplate.length) {
      if (panels.indexOf(ADVANCED) === -1) {
        panels.push(ADVANCED);
      }
    }
    if (!instanceType || !disk || !dockerImage) {
      if (panels.indexOf(EXEC_ENVIRONMENT) === -1) {
        panels.push(EXEC_ENVIRONMENT);
      }
    }
    return panels;
  };

  getPanelHeader = (key) => {
    let title;
    let icon;
    switch (key) {
      case EXEC_ENVIRONMENT: title = 'Exec environment'; icon = 'code-o'; break;
      case ADVANCED: title = 'Advanced'; icon = 'setting'; break;
      case PARAMETERS: title = 'Parameters'; icon = 'bars'; break;
    }
    return (
      <Row
        className={styles.panelHeader}
        type="flex"
        justify={key === PARAMETERS ? 'flex-start' : 'space-between'}
        align="middle"
      >
        <span className={styles.itemHeader}>
          <Icon type={icon} /> {title}
        </span>
        {
          this.getPanelShortDescription(key)
        }
        {
          key === PARAMETERS && this.renderUploadParametersControls({marginLeft: 10})
        }
        {
          key === PARAMETERS && this.renderParametersPayloadSelector({marginLeft: 10})
        }
        {
          key === PARAMETERS && this.renderRawEditCheckbox()
        }
      </Row>
    );
  };

  getPanelShortDescription = (key) => {
    if (this.state.openedPanels.indexOf(key) >= 0 || key === PARAMETERS) {
      return undefined;
    }
    const descriptions = [];
    switch (key) {
      case EXEC_ENVIRONMENT:
        const getDockerImageName = (dockerImage) => {
          if (!dockerImage) {
            return undefined;
          }
          const parts = dockerImage.split('/');
          if (parts.length > 2) {
            return parts.slice(1).join('/');
          } else {
            return dockerImage;
          }
        };
        descriptions.push(
          getDockerImageName(
            this.getSectionFieldValue(EXEC_ENVIRONMENT)('dockerImage') ||
            this.getDefaultValue('docker_image')
          )
        );
        if (this.state.launchCluster) {
          const instanceType = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type') ||
            this.getDefaultValue('instance_size');
          descriptions.push(
            `${instanceType} ${ConfigureClusterDialog.getClusterDescription(this, true)}`
          );
        } else {
          descriptions.push(this.getSectionFieldValue(EXEC_ENVIRONMENT)('type') ||
            this.getDefaultValue('instance_size'));
        }
        descriptions.push(`${this.getSectionFieldValue(EXEC_ENVIRONMENT)('disk') ||
        this.getDefaultValue('instance_disk')}Gb`);
        break;
      case ADVANCED:
        const isSpot = `${this.getSectionFieldValue(ADVANCED)('is_spot') ||
          this.getDefaultValue('is_spot')}` === 'true';
        descriptions.push(getSpotTypeName(isSpot, this.currentCloudRegionProvider));
        const timeout = this.getSectionFieldValue(ADVANCED)('timeout') ||
          this.getDefaultValue('timeout');
        if (timeout && !isNaN(timeout)) {
          descriptions.push(`Timeout: ${timeout} min`);
        }
        if (this.state.startIdle) {
          descriptions.push('Start idle');
        } else {
          let command = this.getSectionFieldValue(ADVANCED)('cmdTemplate') ||
            this.getDefaultValue('cmd_template');
          if (command) {
            if (command.length > 50) {
              command = `${command.substring(0, 50)}...`;
            }
            descriptions.push(`"${command}"`);
          }
        }
        break;
    }
    return (
      <Row
        className={styles.panelDescriptionContainer}
        type="flex">
        {
          descriptions.filter(d => d && d.length).map((description, index) =>
            <span
              key={`description-${index}`}
              className={classNames(styles.panelDescription, 'cp-text-not-important')}>
              {description}
            </span>
          )
        }
      </Row>
    );
  };

  renderSeparator = (text, marginInCols, key, style, highlighted = false) => {
    return (
      <Row key={key} type="flex" style={style || {margin: 0}}>
        <Col span={marginInCols} />
        <Col span={24 - 2 * marginInCols}>
          <table style={{width: '100%'}}>
            <tbody>
              <tr className={classNames(styles.parameterSectionHeader, {
                [styles.highlighted]: highlighted
              })}>
                <td style={{width: '50%'}}>
                  <div
                    className={classNames('cp-divider horizontal', {
                      'cp-primary': highlighted,
                      'border': highlighted
                    })}
                    style={{
                      width: 'unset',
                      margin: '0 5px'
                    }}
                  >
                    {'\u00A0'}
                  </div>
                </td>
                <td style={{width: 1, whiteSpace: 'nowrap'}}>
                  <b className={classNames({
                    'cp-primary': highlighted
                  })}>
                    {text}
                  </b>
                </td>
                <td style={{width: '50%'}}>
                  <div
                    className={classNames('cp-divider horizontal', {
                      'cp-primary': highlighted,
                      'border': highlighted
                    })}
                    style={{
                      width: 'unset',
                      margin: '0 5px'
                    }}
                  >
                    {'\u00A0'}
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </Col>
        <Col span={marginInCols} />
      </Row>
    );
  };

  renderFireCloudConfigConnectionsList = () => {
    if (this._fireCloudParameters && this._fireCloudParameters.pending) {
      return <Row style={{marginTop: 20}}><LoadingView /></Row>;
    }
    if (this._fireCloudParameters && this._fireCloudParameters.error) {
      return (
        <Row style={{marginTop: 20}}>
          <Alert type="warning" message={this._fireCloudParameters.error} />
        </Row>
      );
    }
    if (this._fireCloudParameters && this._fireCloudParameters.googleApi.error) {
      return (
        <Row style={{marginTop: 20}}>
          <Alert type="warning" message="Google auth initialization error" />
        </Row>
      );
    }
    if (this._fireCloudParameters && !this._fireCloudParameters.isSignedIn) {
      return (
        <Row
          type="flex"
          align="middle"
          justify="center"
          className={classNames(styles.fireCloudSignInContainer, 'cp-content-panel')}
        >
          <Row style={{margin: 2}}>
            You must sign in with your Google account to browse FireCloud method inputs & outputs
          </Row>
          <Row style={{margin: 2}}>
            <Button type="primary" onClick={this.props.googleApi.signIn}>
              Sign In
            </Button>
          </Row>
        </Row>
      );
    }
    if (!this.selectedFireCloudParameters) {
      return null;
    }
    const inputs = (this.selectedFireCloudParameters.inputs || []).map(i => i);
    const outputs = (this.selectedFireCloudParameters.outputs || []).map(o => o);
    const defaultInputs = this.getFireCloudDefaultInputs();
    const defaultOutputs = this.getFireCloudDefaultOutputs();
    const renderConnections = (connections, defaultConnections, key, stateKey, errorsStateKey) => {
      let conns = [];
      for (let i = 0; i < connections.length; i++) {
        const conn = connections[i];
        const onChange = (e) => {
          const values = this.state[stateKey];
          values[conn.name] = e.target.value;
          this.setState({[stateKey]: values}, this.formFieldsChanged);
        };
        let value = defaultConnections[conn.name];
        if (this.state[stateKey][conn.name] !== undefined) {
          value = this.state[stateKey][conn.name];
        }
        const error = this.state[errorsStateKey][conn.name];
        conns.push(
          <Row
            key={conn.name}
            align="middle"
            type="flex"
            style={{margin: '4px 0'}}>
            {
              <Popover
                content={
                  error
                    ? (
                      <div>
                        <Row>
                          {conn.name}
                        </Row>
                        <Row className="cp-error">
                          {error}
                        </Row>
                      </div>
                    )
                    : conn.name
                }
                trigger="hover">
                <Col
                  style={{
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}
                  span={4}
                  offset={2}>
                  <span
                    className={
                      classNames(
                        {
                          'cp-error': error
                        }
                      )
                    }
                  >
                    {conn.name}
                  </span>
                </Col>
              </Popover>
            }
            <Col
              key={conn}
              span={15}>
              <Input
                value={value}
                onChange={onChange}
                size="large"
                className={
                  classNames({
                    'cp-error': error
                  })
                }
              />
            </Col>
          </Row>
        );
      }
      return <Row key={key}>
        <Col>
          {conns}
        </Col>
      </Row>;
    };
    return [
      inputs && this.renderSeparator(
        'FireCloud inputs',
        0,
        'FireCloud-inputs-separator',
        {marginTop: 20, marginBottom: 10}
      ),
      inputs && renderConnections(
        inputs,
        defaultInputs,
        'FireCloud-inputs-value',
        'fireCloudInputs',
        'fireCloudInputsErrors'
      ),
      outputs && this.renderSeparator(
        'FireCloud outputs',
        0,
        'FireCloud-outputs-separator',
        {marginTop: 20, marginBottom: 10}
      ),
      outputs && renderConnections(
        outputs,
        defaultOutputs,
        'FireCloud-outputs-value',
        'fireCloudOutputs',
        'fireCloudOutputsErrors'
      )
    ];
  };

  renderFormItemRow = (renderer, hint, options) => {
    const content = renderer && renderer(options);
    if (content) {
      return (
        <Row
          type="flex"
          className={styles.formItemContainer}
          style={options ? options.containerStyle : undefined}>
          {content}
          <div className={styles.hintContainer}>
            {hint ? hints.renderHint(this.localizedStringWithSpotDictionaryFn, hint) : '\u00A0'}
          </div>
        </Row>
      );
    }
    return null;
  };

  renderAlerts = () => {
    const {alerts} = this.props;
    if (!alerts || !alerts.length) {
      return null;
    }
    const defaultType = 'warning';
    const groupedAlerts = alerts.reduce((result, alert) => {
      const {type = defaultType} = alert;
      (result[type] = result[type] || []).push(alert);
      return result;
    }, {});
    const getMessagesList = (messages) => {
      return (
        <ul style={{listStyle: 'none'}}>
          {messages.map((alert, index) => (
            <li key={`error_${index}`}>{alert.message}</li>
          ))}
        </ul>
      );
    };
    return (
      <Row style={{marginBottom: '10px'}}>
        {Object.entries(groupedAlerts).map(([type, messages]) => {
          return messages && messages.length ? (
            <Alert
              key={type}
              type={type}
              style={{marginBottom: '4px'}}
              message={getMessagesList(messages)}
            />) : null;
        })}
      </Row>
    );
  };

  renderUploadParametersControls = (style = {}) => {
    const {preferences} = this.props;
    const preventDefault = (e) => {
      e.stopPropagation();
    };
    const onUploaded = (files) => {
      (async () => {
        const hide = message.loading('Applying parameters', 0);
        try {
          const {
            detached = false
          } = this.props;
          const {
            pipeline
          } = this.state;
          const {
            parameters: current,
            initialParameters = []
          } = this.getCurrentParametersPayload();
          const paramsPayloads = await Promise.all(
            files.map(async (file) => {
              const parameters = await parameterUtilities.mergeParametersWithConfiguration(
                file.parameters,
                {
                  parameters: current,
                  detached,
                  pipeline: pipeline !== undefined && pipeline !== null
                }
              );
              return {
                id: files.length === 1 ? 'default' : file.file,
                parameters,
                initialParameters
              };
            }));
          await this.registerParametersPayloads(paramsPayloads);
        } catch (error) {
          message.error(
            <div>Error applying parameters: {error.message}</div>,
            5
          );
        } finally {
          hide();
        }
      })();
    };
    if (!preferences.loaded) {
      return null;
    }
    const {
      // eslint-disable-next-line camelcase
      upload_parameters = false,
      uploadParameters = upload_parameters
    } = preferences.uiLaunchParameters || {};
    if (!uploadParameters) {
      return null;
    }
    return (
      <div
        style={{display: 'inline-flex', alignItems: 'center', ...(style || {})}}
        onClick={preventDefault}>
        <UploadParametersButton
          disabled={
            this.getLoadingState('parameters').pending ||
            (this.props.readOnly && !this.props.canExecute)
          }
          multiple={!this.props.editConfigurationMode && !this.props.detached}
          onParametersUploaded={onUploaded}
          asLink={false}>
          Upload
        </UploadParametersButton>
      </div>
    );
  }

  renderParametersPayloadSelector = (style = {}) => {
    const preventDefault = (e) => {
      e.stopPropagation();
    };
    const payloads = this.getParametersPayloads();
    const current = this.getCurrentParametersPayload();
    const onChange = (e) => this.setCurrentParametersPayload(e);
    if (!payloads.some((p) => p.id !== 'default')) {
      return null;
    }
    return (
      <div
        style={{display: 'inline-flex', alignItems: 'center', ...(style || {})}}
        onClick={preventDefault}
      >
        <ParametersPayloadSelector
          payloads={payloads}
          onChange={this.updateParametersPayloads}
          active={current ? current.id : undefined}
          onChangeActive={onChange}
          onReset={this.updateFromProps}
          onRemovePayload={this.removeParametersPayload}
        />
      </div>
    );
  };

  renderRawEditCheckbox = () => {
    const handleChangeRawEdit = (e) => {
      if (e) {
        e.stopPropagation();
        e.preventDefault();
      }
      this.setState(
        {isRawEditEnabled: !this.state.isRawEditEnabled},
        () => {
          this.forceValidation = true;
          (this.formFieldsChanged)();
          this.onValidateParameters();
        }
      );
    };
    return (
      <div
        style={{
          display: 'inline',
          marginLeft: 'auto'
        }}
        onClick={handleChangeRawEdit}
      >
        <Popover
          placement="topLeft"
          content={(
            <div>
              <b>Raw edit</b> mode:
              <ul
                className={styles.list}
              >
                <li>
                  disables parameters validation;
                </li>
                <li>
                  displays all available parameters, despite any visibility controls.
                </li>
              </ul>
            </div>
          )}
        >
          <Checkbox checked={this.state.isRawEditEnabled}>
            Raw edit
            <Icon
              type="info-circle"
              style={{marginLeft: 5}}
            />
          </Checkbox>
        </Popover>
      </div>
    );
  };

  onChangePipelineVersion = (version) => {
    const {
      pipeline,
      onPipelineChanged
    } = this.props;
    if (onPipelineChanged && pipeline) {
      this.props.onPipelineChanged(pipeline.id, version);
    }
  };

  render () {
    const renderSubmitButton = () => {
      if (this.props.editConfigurationMode) {
        return (
          <div className={styles.actions}>
            <FormItem style={{margin: 0}}>
              {
                this.renderRunButton()
              }
              {
                this.props.detached && this.props.editConfigurationMode && (
                  <ServerlessAPIButton
                    style={{verticalAlign: 'middle', marginRight: 10}}
                    configurationId={this.props.configurationId}
                    configurationName={this.props.currentConfigurationName}
                  />
                )
              }
              {
                this.props.canRemove && !this.props.readOnly
                  ? (
                    <Button
                      size="small"
                      id="remove-pipeline-configuration-button"
                      type="danger"
                      onClick={
                        () => this.props.onRemoveConfiguration && this.props.onRemoveConfiguration()
                      }
                      style={{verticalAlign: 'middle'}}
                    >
                      Remove
                    </Button>
                  ) : undefined
              }
              {
                !this.props.currentConfigurationIsDefault && !this.props.readOnly
                  ? (
                    <Button
                      size="small"
                      id="set-pipeline-configuration-as-default-button"
                      onClick={
                        () => this.props.onSetConfigurationAsDefault &&
                          this.props.onSetConfigurationAsDefault()
                      }
                      style={{verticalAlign: 'middle', marginLeft: 10}}>
                      Set as default
                    </Button>
                  ) : undefined
              }
              {
                !this.props.readOnly
                  ? (
                    <Button
                      size="small"
                      id="save-pipeline-configuration-button"
                      disabled={!this.modified}
                      type="primary"
                      htmlType="submit"
                      style={{verticalAlign: 'middle', marginLeft: 10}}>
                      Save
                    </Button>
                  ) : undefined
              }
            </FormItem>
          </div>
        );
      } else if (!this.props.pipeline || roleModel.executeAllowed(this.props.pipeline)) {
        const KEYS = {
          selectMetadata: 'select metadata'
        };
        const onDropDownClick = ({key}) => {
          if (key === KEYS.selectMetadata) {
            this.run({key: RUN_SELECTED_KEY});
          }
        };
        const dropdownRenderer = () => (
          <Menu onClick={onDropDownClick} selectedKeys={[]} style={{cursor: 'pointer'}}>
            <MenuItem key={KEYS.selectMetadata}>
              Select metadata entries and launch
            </MenuItem>
          </Menu>
        );
        return (
          <div className={styles.actions}>
            <FormItem style={{margin: 0, marginRight: 10}}>
              {
                !this.props.detached && !this.props.editConfigurationMode && (
                  <Button
                    id="launch-command-button"
                    disabled={!this.launchCommandPayload}
                    style={{marginRight: 5}}
                    onClick={this.showLaunchCommands}
                  >
                    <Icon type="code" />
                  </Button>
                )
              }
              <SubmitButton
                id="launch-pipeline-button"
                inputs={this.inputPaths}
                outputs={this.outputPaths}
                skipCheck={
                  this.props.parameters.run_as &&
                  this.currentUserName() !== this.props.parameters.run_as
                }
                dockerImage={this.dockerImage}
                type="primary"
                htmlType="submit"
                dropdown={!!this.props.runConfigurationId}
                dropdownRenderer={dropdownRenderer}
                dropdownId="launch-metadata"
                loading={this.props.pending}
                disabled={this.props.pending}
              >
                Launch
              </SubmitButton>
            </FormItem>
          </div>
        );
      }
      return undefined;
    };
    const renderFormTitle = () => {
      if (this.props.editConfigurationMode) {
        const nameError =
          'Name can contain only letters, digits, spaces, \'_\', \'-\', \'@\' and \'.\'.';
        return (
          <div
            key="header"
            className={styles.itemHeader}
            style={{
              display: 'flex',
              flexWrap: 'nowrap',
              alignItems: 'center',
              lineHeight: '32px',
              minWidth: 200,
              marginRight: 5
            }}
          >
            <div key="name" style={{whiteSpace: 'nowrap', marginRight: 5}}>
              Name:
            </div>
            <FormItem
              key="input"
              className={styles.formItemRow}
              hasFeedback>
              {this.getSectionFieldDecorator('configuration')('name',
                {
                  rules: [
                    {
                      required: true,
                      message: 'Configuration name is required'
                    },
                    {
                      pattern: /^[\da-zA-Z._\-@ ]+$/,
                      message: nameError
                    }
                  ],
                  initialValue: this.props.currentConfigurationName
                }
              )(
                <Input disabled={this.props.readOnly && !this.props.canExecute} />
              )}
            </FormItem>
          </div>
        );
      }

      let pipelineName, pipelineVersion;
      if (this.props.pipeline) {
        pipelineName = this.props.pipeline.name;
      } else {
        const dockerImageParts = (
          this.props.form.getFieldValue(`${EXEC_ENVIRONMENT}.dockerImage`) || ''
        ).split('/');
        if (dockerImageParts.length > 0) {
          pipelineName = dockerImageParts[dockerImageParts.length - 1].split(':')[0];
          pipelineVersion = dockerImageParts[dockerImageParts.length - 1].split(':')[1];
        } else {
          pipelineName = this.localizedString('pipeline');
        }
      }

      return (
        <div
          key="header"
          className={styles.itemHeader}
          style={{
            display: 'flex',
            flexWrap: 'nowrap',
            alignItems: 'center',
            lineHeight: '32px',
            minWidth: 200,
            marginRight: 5
          }}
        >
          <Icon
            type="play-circle-o"
            className="cp-primary"
          />
          <span style={{whiteSpace: 'pre'}}>Launch </span>
          <RunName
            style={{fontWeight: 'bold'}}
            alias={this.state.runNameAlias}
            editable
            onChange={this.runNameAliasChange}
            ignoreOffset
          >
            <span
              id="launch-form-pipeline-name"
            >
              {pipelineName}
            </span>
            {
              pipelineVersion && (
                <span
                  id="launch-form-pipeline-version"
                  style={{fontWeight: 'normal'}}
                >
                  :{pipelineVersion}
                </span>
              )
            }
          </RunName>
        </div>
      );
    };
    const titleConfigurationSection = (() => {
      if (this.props.editConfigurationMode) {
        return null;
      }
      let configuration;
      if (this.props.configurations.length > 1 && this.props.currentConfigurationName) {
        const configurationChange = (configurationName) => {
          if (this.props.onConfigurationChanged) {
            this.props.onConfigurationChanged(configurationName);
          }
        };
        configuration = (
          <div
            key="configuration"
            className={styles.itemHeaderConfigurationControl}
          >
            <span style={{marginRight: 5}}>Configuration:</span>
            <div style={{width: 200}}>
              <Select
                disabled={this.props.readOnly && !this.props.canExecute}
                defaultValue={this.props.currentConfigurationName}
                showSearch
                allowClear={false}
                placeholder="Configuration name"
                optionFilterProp="children"
                onChange={configurationChange}
                filterOption={
                  (input, option) =>
                    option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0}>
                {
                  this.props.configurations.map(c => {
                    return (
                      <Select.Option
                        key={c.name}
                        value={c.name}>
                        {c.name}
                      </Select.Option>
                    );
                  })
                }
              </Select>
            </div>
          </div>
        );
      }

      let pipelineVersionPicker;
      if (this.props.pipeline) {
        if (!this.props.editConfigurationMode && !this.props.detached) {
          pipelineVersionPicker = (
            <div key="pipeline version" className={styles.itemHeaderConfigurationControl}>
              <span style={{marginRight: 5}}>Version:</span>
              <div style={{width: 200}}>
                <PipelineVersionPicker
                  pipelineId={this.props.pipeline.id}
                  pipelineVersion={this.props.version}
                  onPipelineVersionChange={this.onChangePipelineVersion}
                  disabled={this.props.readOnly && !this.props.canExecute}
                />
              </div>
            </div>
          );
        }
      }

      return [
        pipelineVersionPicker,
        configuration
      ];
    })();
    const bucketTypes = ['AZ', 'S3', 'GS', 'DTS', 'NFS'];
    if (this.state.parameterType === 'path' || this.state.parameterType === 'input') {
      bucketTypes.push('AWS_OMICS_SEQ', 'AWS_OMICS_REF');
    }
    return (
      <Form onSubmit={this.handleSubmit}>
        <div className={styles.layout}>
          <div className={classNames(styles.layoutHeader, 'cp-divider', 'bottom')}>
            <div
              id="launch-pipeline-form-header-container"
              style={{width: '100%', display: 'flex', alignItems: 'flex-start', margin: 5}}
            >
              <div
                id="launch-pipeline-form-header"
                style={{
                  flex: 1,
                  overflow: 'auto',
                  display: 'inline-flex',
                  flexWrap: 'wrap',
                  alignItems: 'center',
                  marginRight: 5,
                  padding: '1px 0'
                }}
              >
                {renderFormTitle()}
                <div style={{display: 'inline-flex', lineHeight: '32px'}}>
                  {titleConfigurationSection}
                </div>
              </div>
              {renderSubmitButton()}
            </div>
            <div
              style={{
                width: '100%', display: 'flex', alignItems: 'center', margin: 5, flexWrap: 'wrap'
              }}>
              {this.renderEstimatedPriceInfo()}
            </div>
          </div>
          {this.renderAlerts()}
          {
            this.props.pipeline &&
            !roleModel.executeAllowed(this.props.pipeline) &&
            !this.props.detached
              ? (
                <Row>
                  <Alert
                    type="warning"
                    message={`You have no permissions to launch ${this.props.pipeline.name}`} />
                  <br />
                </Row>
              ) : undefined
          }
          <Collapse
            bordered={false}
            onChange={(tabs) => this.setState({openedPanels: tabs})}
            activeKey={this.state.openedPanels}>
            <Collapse.Panel
              id="launch-pipeline-exec-environment-panel"
              key={EXEC_ENVIRONMENT}
              className={
                classNames(styles.section, {
                  [styles.hidden]: !this.executionEnvironmentSectionVisible
                })
              }
              header={this.getPanelHeader(EXEC_ENVIRONMENT)}>
              <Row type="flex" justify="space-between">
                <div
                  className={styles.settingsContainer}
                  style={{padding: 5}}>
                  <div className={styles.settingsContent}>
                    {this.renderFormItemRow(this.renderPipelineSelection, hints.pipelineHint)}
                    {this.renderFormItemRow(this.renderExecutionEnvironmentSelection)}
                    {this.renderFormItemRow(this.renderDockerImageFormItem, hints.dockerImageHint)}
                    {
                      this.renderFormItemRow(
                        this.renderInstanceTypeSelection,
                        hints.instanceTypeHint
                      )
                    }
                    {this.renderReservationParametersSelector()}
                    {this.renderFormItemRow(this.renderDiskFormItem, hints.diskHint)}
                    {!this.isWindowsPlatform &&
                    !this.state.fireCloudMethodName &&
                    !this.state.isDts && (
                      <Row
                        type="flex"
                        className={styles.formItemContainer}
                        style={{flexWrap: 'wrap', marginRight: '5px'}}
                      >
                        <Col
                          offset={6}
                          span={17}
                        >
                          <AllowedInstancesCountWarning
                            payload={{
                              nodeCount: this.state.nodesCount,
                              maxNodeCount: this.state.maxNodesCount
                            }}
                            style={{width: '100%'}}
                          />
                        </Col>
                        <a
                          onClick={this.openConfigureClusterDialog}
                          className="cp-text underline"
                          style={{marginLeft: 'auto', marginRight: '30px'}}
                        >
                          <Icon type="setting" />
                          {ConfigureClusterDialog.getConfigureClusterButtonDescription(this)}
                        </a>
                      </Row>
                    )}
                    <ConfigureClusterDialog
                      instanceName={this.getSectionFieldValue(EXEC_ENVIRONMENT)('type')}
                      launchCluster={this.state.launchCluster}
                      cloudRegionProvider={this.currentCloudRegionProvider}
                      autoScaledPriceType={this.state.autoScaledPriceType}
                      fsConfig={this.state.fsConfig}
                      autoScaledCluster={this.state.autoScaledCluster}
                      hybridAutoScaledClusterEnabled={this.state.hybridAutoScaledClusterEnabled}
                      gpuScalingConfiguration={this.state.gpuScalingConfiguration}
                      childNodeInstanceConfiguration={this.state.childNodeInstanceConfiguration}
                      gridEngineEnabled={this.state.gridEngineEnabled}
                      sparkEnabled={this.state.sparkEnabled}
                      slurmEnabled={this.state.slurmEnabled}
                      kubeEnabled={this.state.kubeEnabled}
                      nodesCount={this.state.nodesCount}
                      maxNodesCount={this.state.maxNodesCount || 1}
                      onClose={this.closeConfigureClusterDialog}
                      onChange={this.onChangeClusterConfiguration}
                      visible={this.state.configureClusterDialogVisible}
                      disabled={this.props.readOnly && !this.props.canExecute}
                      instanceTypes={this.instanceTypes}
                    />
                    {
                      this.renderFormItemRow(
                        this.renderAWSRegionSelection,
                        this.regionDisabledByToolSettings
                          ? hints.awsRegionRestrictedByToolSettingsHint
                          : hints.awsRegionHint
                      )
                    }
                    {
                      this.renderFormItemRow(
                        this.renderRescheduleRunControl
                      )
                    }
                    {this.renderFormItemRow(this.renderCoresFormItem)}
                    {
                      this.renderFormItemRow(
                        this.renderAdditionalRunCapabilities,
                        hints.runCapabilitiesHint
                      )
                    }
                  </div>
                </div>
                <div
                  className={styles.settingsContainer}
                  style={{padding: 5}}>
                  <div className={styles.settingsContent}>
                    <Row
                      type="flex"
                      style={{alignItems: 'center'}}
                    >
                      {
                        this.renderExecutionEnvironmentSummary()
                      }
                    </Row>
                  </div>
                </div>
              </Row>
            </Collapse.Panel>
            <Collapse.Panel
              id="launch-pipeline-advanced-panel"
              key={ADVANCED}
              className={
                classNames(styles.section, {[styles.hidden]: !this.advancedSectionVisible})
              }
              header={this.getPanelHeader(ADVANCED)}>
              {this.renderCustomTagsConfigurationItem()}
              {this.renderScheduleControl()}
              {this.renderPriceTypeSelection()}
              {this.renderDisableAutoPauseFormItem()}
              {this.renderPrettyUrlFormItem()}
              {this.renderHostedAppConfigurationItem()}
              {this.renderJobNotificationsItem()}
              {this.renderTimeoutFormItem()}
              {this.renderCustomUIItem()}
              {this.renderEndpointNameFormItem()}
              {this.renderStopAfterFormItem()}
              {this.renderLimitMountsFormItem()}
              {this.renderCmdTemplateFormItem()}
              {this.renderParameters(true)}
            </Collapse.Panel>
            <Collapse.Panel
              id="launch-pipeline-parameters-panel"
              key={PARAMETERS}
              className={
                classNames(styles.section, {[styles.hidden]: !this.parametersSectionVisible})
              }
              header={this.getPanelHeader(PARAMETERS)}
            >
              {this.renderParameters(false)}
              {this.isFireCloudSelected && this.renderFireCloudConfigConnectionsList()}
            </Collapse.Panel>
            {
              !this.state.detached && !this.props.editConfigurationMode && (
                <LaunchCommand
                  payload={this.launchCommandPayload}
                  visible={this.state.showLaunchCommands}
                  onClose={this.hideLaunchCommands}
                />
              )
            }
          </Collapse>
        </div>
        <BucketBrowser
          multiple
          onSelect={this.selectBucketPath}
          onCancel={this.closeBucketBrowser}
          visible={this.state.bucketBrowserVisible}
          uploadFilesAllowed={this.state.bucketBrowserAllowUpload}
          path={this.state.bucketPath}
          showOnlyFolder={this.state.showOnlyFolderInBucketBrowser}
          allowBucketSelection={this.state.allowBucketSelectionInBucketBrowser}
          checkWritePermissions={this.state.showOnlyFolderInBucketBrowser}
          bucketTypes={bucketTypes} />
        <PipelineBrowser
          multiple={false}
          onCancel={this.closePipelineBrowser}
          onSelect={this.selectPipelineConfirm}
          visible={this.state.pipelineBrowserVisible}
          pipelineId={this.state.pipeline ? this.state.pipeline.id : undefined}
          version={this.state.version}
          pipelineConfiguration={this.state.pipelineConfiguration}
          allowSelectLatestVersion={!!this.props.isDetachedConfiguration}
          fireCloudMethod={this.state.fireCloudMethodName}
          fireCloudNamespace={this.state.fireCloudMethodNamespace}
          fireCloudMethodSnapshot={this.state.fireCloudMethodSnapshot}
          fireCloudMethodConfiguration={this.state.fireCloudMethodConfiguration}
          fireCloudMethodConfigurationSnapshot={this.state.fireCloudMethodConfigurationSnapshot}
        />
        {
          this.state.currentProjectId
            ? (
              <MetadataBrowser
                multiple={false}
                readOnly
                onCancel={this.closeMetadataBrowser}
                onSelect={this.selectMetadataConfirm}
                visible={this.state.metadataBrowserVisible}
                initialFolderId={this.state.currentProjectId}
                rootEntityId={this.state.rootEntityId}
                currentMetadataEntity={this.state.currentMetadataEntity.slice()}
              />
            ) : undefined
        }
      </Form>
    );
  }

  fireCloudSelectionChanged = (prevState) => {
    return (this.state.fireCloudMethodNamespace !== prevState.fireCloudMethodNamespace ||
      this.state.fireCloudMethodName !== prevState.fireCloudMethodName ||
      this.state.fireCloudMethodSnapshot !== prevState.fireCloudMethodSnapshot ||
      this.state.fireCloudMethodConfiguration !== prevState.fireCloudMethodConfiguration ||
      this.state.fireCloudMethodConfigurationSnapshot !==
        prevState.fireCloudMethodConfigurationSnapshot);
  };

  fetchUserRunCapabilities = () => {
    this.setState({
      userRunCapabilitiesPending: true
    }, () => {
      this.props.preferences
        .fetchIfNeededOrWait()
        .then(() => getUserCapabilities())
        .then((userRunCapabilities = []) => {
          let {runCapabilities} = this.state;
          if (
            !this.props.editConfigurationMode
          ) {
            runCapabilities = correctRequiredCapabilities(
              [...new Set([...(runCapabilities || []), ...userRunCapabilities])],
              this.props.preferences
            );
          }
          this.setState({
            userRunCapabilities,
            runCapabilities,
            userRunCapabilitiesPending: false
          });
        });
    });
  };

  componentDidMount () {
    // --------------------------
    this.updateFromProps();
    this.updateConfigurationsFromProps();
    // --------------------------
    this.fetchUserRunCapabilities();
    this.reset(true);
    this.evaluateEstimatedPrice({});
    if (this.props.parameters && this.props.parameters.docker_image) {
      this.loadToolSettings(this.props.parameters.docker_image);
    }
    this.prepare();
    if (this.props.isDetachedConfiguration && this.isFireCloudSelected) {
      this.loadFireCloudConfigurations();
    }
    this.props.onInitialized && this.props.onInitialized(this);
    this.initializeParametersNavigationCheck();
  }

  componentDidUpdateNew (prevProps, prevState) {
    const {
      parameters: prevParameters,
      configurationId: prevConfigurationId,
      currentConfigurationName: prevConfigurationName,
      detached: prevDetached = false
    } = prevProps;
    const {
      parameters,
      configurationId,
      currentConfigurationName,
      detached = false
    } = this.props;
    const {
      pipeline: prevPipeline = undefined
    } = prevState;
    const {
      pipeline = undefined
    } = this.state;
    if (
      parameters !== prevParameters ||
      detached !== prevDetached ||
      prevPipeline !== pipeline
    ) {
      this.updateFromProps();
      this.updateCustomValidators();
    }
    if (configurationId !== prevConfigurationId) {
      this.updateConfigurationsFromProps();
    } else if (currentConfigurationName !== prevConfigurationName) {
      this.updateRootEntityFromProps();
    }
  }

  abortAll = () => {
    this._loadingTokens = {};
  };

  abortLoading = (key) => {
    this._loadingTokens = this._loadingTokens || {};
    this._loadingTokens[key] = {};
  }

  createLoadingToken = (key) => {
    this.abortLoading(key);
    this._loadingTokens = this._loadingTokens || {};
    this._loadingTokens[key] = {};
    return this._loadingTokens[key];
  };

  getLoadingToken = (key) => {
    return this._loadingTokens ? this._loadingTokens[key] : undefined;
  };

  getLoadingState = (key) => {
    const {[`${key}Pending`]: pending = false, [`${key}Error`]: error = undefined} = this.state;
    return {pending, error};
  };

  wrapLoading = (key, fn) => {
    const token = this.createLoadingToken(key);
    const commitState = async (s) => {
      if (token === this.getLoadingToken(key)) {
        return new Promise((resolve) => {
          const state = (() => {
            if (typeof s === 'function') {
              return s(this.state);
            }
            return s;
          })();
          this.setState(state, () => resolve());
        });
      }
      return Promise.resolve();
    };
    (async () => {
      await commitState({
        [`${key}Pending`]: true,
        [`${key}Error`]: undefined
      });
      try {
        await fn(commitState);
        await commitState({
          [`${key}Pending`]: false
        });
      } catch (error) {
        await commitState({
          [`${key}Pending`]: false,
          [`${key}Error`]: error.message
        });
      }
    })();
  };

  updateFromProps = () => {
    const {
      parameters: payload,
      detached = false
    } = this.props;
    const {
      pipeline
    } = this.state;
    this.wrapLoading('parameters', async (commitState) => {
      let params = [];
      try {
        params = await parameterUtilities.readParametersFromConfiguration(
          payload,
          {
            detached,
            pipeline: pipeline !== undefined && pipeline !== null
          }
        );
      } catch (error) {
        console.log(`error initializing parameters: ${error.message}`);
      }
      await this.registerParametersPayloads(
        [{parameters: params, id: 'default'}],
        commitState
      );
    });
  };

  updateConfigurationsFromProps = () => {
    const {
      configurationId
    } = this.props;
    this.abortLoading('configurations');
    this.wrapLoading('configurations', async (commitState) => {
      if (configurationId) {
        const req1 = configurationsRequest.getConfiguration(configurationId);
        const folderProjectRequest = new FolderProject(configurationId, 'CONFIGURATION');
        await Promise.all([req1.fetchIfNeededOrWait(), folderProjectRequest.fetch()]);
        if (folderProjectRequest.error) {
          message.error(folderProjectRequest.error, 5);
        }
        const {
          id: currentProjectId,
          data: currentProjectMetadata
        } = folderProjectRequest.value || {};
        const {
          entries = []
        } = req1.value || {};
        let currentMetadataEntity = [];
        if (currentProjectId) {
          const metadataEntityFieldsRequest = new MetadataEntityFields(currentProjectId);
          await metadataEntityFieldsRequest.fetch();
          if (metadataEntityFieldsRequest.error) {
            message.error(metadataEntityFieldsRequest.error, 5);
          }
          currentMetadataEntity = metadataEntityFieldsRequest.value || [];
        }
        commitState((cur) => ({
          ...cur,
          detachedConfigurations: entries,
          currentProjectId,
          currentProjectMetadata,
          currentMetadataEntity
        }));
      } else {
        commitState((cur) => ({
          ...cur,
          detachedConfigurations: [],
          currentProjectId: undefined,
          currentProjectMetadata: undefined,
          currentMetadataEntity: []
        }));
      }
      this.updateRootEntityFromProps();
    });
  };

  updateRootEntityFromProps = () => {
    const rootEntityId = this.getDefaultRootEntityId();
    this.setState({
      rootEntityId
    });
  };

  getParameters = (payloadId = undefined) => {
    const {parameters = []} = (payloadId ? this.getParametersPayloadById(payloadId) : undefined) ??
    this.getCurrentParametersPayload();
    return parameters;
  }

  getParametersValidationResult = async (navigateToInvalidPayload = false) => {
    const payloads = this.getParametersPayloads().filter((p) => p.enabled);
    const results = payloads.map((payload) => {
      const {parameters = []} = payload;
      const firstNonValidParameter = parameters.find((p) => !p.valid);
      return {
        id: payload.id,
        valid: !firstNonValidParameter,
        nonValidParameter: firstNonValidParameter
      };
    });
    const firstInvalid = results.find((r) => !r.valid);
    if (firstInvalid && navigateToInvalidPayload) {
      await this.setCurrentParametersPayload(firstInvalid.id);
    }
    return firstInvalid;
  };

  onParametersChange = (newParameters) => {
    const current = this.getCurrentParametersPayload();
    const payload = {
      ...current,
      parameters: newParameters
    };
    const {parametersPayloads = []} = this.state;
    const idx = parametersPayloads.findIndex(p => p.id === payload.id);
    const updated = parametersPayloads.slice();
    if (idx >= 0) {
      updated.splice(idx, 1, {...payload});
    } else {
      updated.push(payload);
    }
    this.setState({
      parametersPayloads: updated
    }, () => {
      this.onValidateParameters();
      this.formFieldsChanged();
    });
  };

  /**
   * @typedef {Object} InitialParametersPayload
   * @property {string} id
   * @property {Parameter[]} parameters
   */
  /**
   * @typedef {InitialParametersPayload} ParametersPayload
   * @property {Parameter[]} initialParameters
   * @property {boolean} enabled
   */
  /**
   * @param {InitialParametersPayload[]} payloads
   * @param {function} [commitState]
   */
  registerParametersPayloads = async (payloads, commitState = undefined) => {
    commitState = commitState || ((st) => {
      if (typeof st === 'function') {
        this.setState(st(this.state));
      } else {
        this.setState(st);
      }
    });
    if (payloads.length === 0) {
      return;
    }
    const {id} = payloads[0];
    await commitState((cur) => ({
      ...cur,
      parametersPayloads: payloads.map((payload) => {
        const {
          id,
          parameters = [],
          initialParameters = parameters.map((p) => ({...p}))
        } = payload;
        return {
          id,
          enabled: true,
          parameters,
          initialParameters
        };
      }),
      currentParametersPayload: id
    }));
    this.onValidateParameters(commitState);
    this.formFieldsChanged();
  };

  /**
   * @returns {ParametersPayload}
   */
  getCurrentParametersPayload = () => {
    const {currentParametersPayload, parametersPayloads = []} = this.state;
    const d = {
      id: currentParametersPayload ?? 'default',
      parameters: [],
      initialParameters: [],
      enabled: true
    };
    return parametersPayloads.find((p) => p.id === currentParametersPayload) ??
      parametersPayloads[0] ?? d;
  };

  getParametersPayloadById = (id) => {
    const {parametersPayloads = []} = this.state;
    return parametersPayloads.find((p) => p.id === id);
  }

  setCurrentParametersPayload = async (key) => new Promise((resolve) => {
    this.setState({currentParametersPayload: key}, () => resolve());
  });

  updateParametersPayloads = (payloads) => {
    const current = this.getParametersPayloads();
    const result = current.slice();
    let changed = false;
    for (const payload of payloads) {
      const idx = result.findIndex((p) => p.id === payload.id);
      if (idx >= 0) {
        changed = true;
        result.splice(idx, 1, payload);
      }
    }
    if (changed) {
      this.setState({
        parametersPayloads: result
      }, () => {
        this.onValidateParameters();
      });
    }
  };

  removeParametersPayload = (key) => {
    const payloads = this.getParametersPayloads();
    const current = this.getCurrentParametersPayload();
    const result = payloads.filter((c) => c.id !== key);
    if (result.length === 0) {
      this.updateFromProps();
    } else {
      this.setState({
        currentParametersPayload: current.id === key ? result[0].id : current.id,
        parametersPayloads: result
      }, () => {
        this.onValidateParameters();
      });
    }
  };

  getParametersPayloads = () => {
    const {parametersPayloads = []} = this.state;
    return parametersPayloads;
  }

  /**
   * @param {ParametersPayload} payload
   */
  updateParametersPayload = async (payload) => {
    return new Promise((resolve) => {
      const {parametersPayloads = []} = this.state;
      const idx = parametersPayloads.findIndex(p => p.id === payload.id);
      const updated = parametersPayloads.slice();
      if (idx >= 0) {
        updated.splice(idx, 1, {...payload});
      } else {
        updated.push(payload);
      }
      this.setState({
        parametersPayloads: updated
      }, () => resolve());
    });
  };

  updateCustomValidators = async () => {
    const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
    await sleep(700);
    const stringToFunction = (str) => {
      try {
        // eslint-disable-next-line no-eval
        return eval(`(${str})`);
      } catch (e) {
        console.error('Invalid validator string: ', e);
      }
    };
    const validatorFn = stringToFunction(parameterUtilities.customValidateFnMock);
    this._customValidators['input_table_for_preprocessing'] = validatorFn;
    this.onValidateParameters();
  };

  _validateParametersTimeout;

  onValidateParameters = (commitState = undefined) => {
    if (this._validateParametersTimeout) {
      clearTimeout(this._validateParametersTimeout);
    }
    this._validateParametersTimeout = setTimeout(() => {
      this._onValidateParameters(commitState);
    }, VALIDATION_DEBOUNCE_TIMEOUT);
  };

  _onValidateParameters = async (commitState = undefined) => {
    commitState = commitState || ((st) => {
      if (typeof st === 'function') {
        this.setState(st(this.state));
      } else {
        this.setState(st);
      }
    });
    const {isRawEditEnabled} = this.state;
    const payloads = this.getParametersPayloads();
    /**
     * @param {ParametersPayload} payload
     */
    const validationFn = async (payload) => {
      const {parameters} = payload;
      let {
        changed,
        parameters: preResult
      } = parameterUtilities.validateParameters(
        parameters,
        isRawEditEnabled
      );
      const instanceTypeValue = this.getSectionFieldValue(EXEC_ENVIRONMENT)('type');
      const instanceType = this.instanceTypes.find(t => t.name === instanceTypeValue);
      const opts = {
        customValidators: this.customValidators,
        form: this.props.form,
        instanceType,
        parameters,
        api: {
          DataStorageItemSize
        }
      };
      const {
        parameters: result,
        changed: customChanged
      } = await parameterUtilities.customValidate(this.customValidators, preResult, opts);
      return {
        changed: customChanged || changed,
        payload: {
          ...payload,
          parameters: result
        }
      };
    };
    const validations = payloads.map(validationFn);
    const payloadsValidation = await Promise.all(validations);
    const changed = payloadsValidation
      .filter((pv) => pv.changed)
      .map((pv) => pv.payload);
    if (changed.length > 0) {
      const updated = payloads.slice();
      for (const p of changed) {
        const idx = updated.findIndex(c => c.id === p.id);
        if (idx >= 0) {
          updated.splice(idx, 1, p);
        }
      }
      commitState((cur) => ({
        ...cur,
        parametersPayloads: updated
      }));
    }
  };

  componentDidUpdate (prevProps, prevState) {
    // ----------------------------------
    this.componentDidUpdateNew(prevProps, prevState);
    // ----------------------------------
    if (this.state.fireCloudMethodName &&
      this.state.execEnvSelectValue !== FIRE_CLOUD_ENVIRONMENT) {
      // eslint-disable-next-line
      this.setState({execEnvSelectValue: FIRE_CLOUD_ENVIRONMENT});
    }
    if (prevState.dtsId !== this.state.dtsId && this.state.dtsId) {
      this.loadDtsClusterInfo();
    }
    if (prevProps.currentConfigurationName !== this.props.currentConfigurationName ||
      prevProps.configurationId !== this.props.configurationId) {
      this.prevParameters = {};
      this.reset();
      this.evaluateEstimatedPrice({});
      this.prepare(true);
    }
    if (prevProps.defaultPriceTypeIsSpot !== this.props.defaultPriceTypeIsSpot) {
      this.evaluateEstimatedPrice({});
    }
    if (!pipelinesEquals(prevProps.pipeline, this.props.pipeline) ||
      prevProps.version !== this.props.version ||
      prevProps.pipelineConfiguration !== this.props.pipelineConfiguration) {
      this.evaluateEstimatedPrice({});
      this.prepare();
    }
    if (this.props.isDetachedConfiguration && this.isFireCloudSelected &&
      this.fireCloudSelectionChanged(prevState)) {
      this.loadFireCloudConfigurations();
    } else if (this.props.isDetachedConfiguration && !this.isFireCloudSelected) {
      this._fireCloudConfigurations = null;
    }
    if (this.props.allowedInstanceTypes &&
      this.props.allowedInstanceTypes.loaded &&
      this.props.allowedInstanceTypes.changed) {
      this.correctAllowedInstanceValues();
      this.props.allowedInstanceTypes.handleChanged();
    }
    if ((prevProps.parameters || {}).docker_image !== (this.props.parameters || {}).docker_image) {
      if (this.props.parameters && this.props.parameters.docker_image) {
        this.loadToolSettings(this.props.parameters.docker_image);
      } else {
        this.resetToolSettings();
      }
    }
    if (prevProps.allowedInstanceTypes.loaded &&
      !this.state.estimatedPrice.evaluated &&
      !this.state.estimatedPrice.pending) {
      this.evaluateEstimatedPrice({});
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.fireCloudMethod && nextProps.fireCloudMethod.name &&
      this.state.execEnvSelectValue !== FIRE_CLOUD_ENVIRONMENT) {
      this.setState({execEnvSelectValue: FIRE_CLOUD_ENVIRONMENT});
    }
    if (nextProps.detached &&
      (nextProps.currentConfigurationName !== this.props.currentConfigurationName ||
      nextProps.configurationId !== this.props.configurationId)) {
      let execEnvSelectValue;
      let isDts = false;
      let dtsId = null;
      const [currentConfiguration] = nextProps.configurations
        .filter(config => config.name === nextProps.currentConfigurationName);

      switch (currentConfiguration.executionEnvironment) {
        case DTS_ENVIRONMENT:
          dtsId = currentConfiguration.dtsId;
          isDts = true;
          execEnvSelectValue = `${DTS_ENVIRONMENT}.${dtsId}`;
          break;
        case FIRE_CLOUD_ENVIRONMENT:
          execEnvSelectValue = FIRE_CLOUD_ENVIRONMENT;
          break;
        default:
          execEnvSelectValue = CLOUD_PLATFORM_ENVIRONMENT;
      }

      this.setState({execEnvSelectValue, isDts, dtsId});
    }
    if ((!this.props.fireCloudMethod && nextProps.fireCloudMethod) ||
      (nextProps.fireCloudMethod &&
        (this.props.fireCloudMethod.name !== nextProps.fireCloudMethod.name ||
        this.props.fireCloudMethod.namespace !== nextProps.fireCloudMethod.namespace ||
        this.props.fireCloudMethod.snapshot !== nextProps.fireCloudMethod.snapshot ||
        this.props.fireCloudMethod.configuration !== nextProps.fireCloudMethod.configuration ||
        this.props.fireCloudMethod.configurationSnapshot !==
          nextProps.fireCloudMethod.configurationSnapshot))) {
      this.setState({
        fireCloudMethodName: nextProps.fireCloudMethod.name,
        fireCloudMethodNamespace: nextProps.fireCloudMethod.namespace,
        fireCloudMethodSnapshot: nextProps.fireCloudMethod.snapshot,
        fireCloudMethodConfiguration: nextProps.fireCloudMethod.configuration,
        fireCloudMethodConfigurationSnapshot: nextProps.fireCloudMethod.configurationSnapshot,
        fireCloudInputs: {},
        fireCloudOutputs: {},
        fireCloudDefaultInputs: nextProps.fireCloudMethod.methodInputs,
        fireCloudDefaultOutputs: nextProps.fireCloudMethod.methodOutputs
      });
    } else if (this.props.fireCloudMethod && !nextProps.fireCloudMethod) {
      this.setState({
        fireCloudMethodName: null,
        fireCloudMethodNamespace: null,
        fireCloudMethodSnapshot: null,
        fireCloudMethodConfiguration: null,
        fireCloudMethodConfigurationSnapshot: null,
        fireCloudInputs: {},
        fireCloudOutputs: {},
        fireCloudDefaultInputs: [],
        fireCloudDefaultOutputs: []
      });
    }
  }

  componentWillUnmount () {
    cancelAnimationFrame(this.checkRAF);
    this.__formFieldsChangedToken = {};
    clearTimeout(this.__formFieldsChangedTimeout);
    this.abortAll();
  }
}

export default class extends React.Component {
  launchForm;

  onInitialized = (form) => {
    this.launchForm = form;
  };

  onValuesChange = (props, fields) => {
    const cloudRegionKey = `${EXEC_ENVIRONMENT}.cloudRegionId`;
    const spotKey = `${ADVANCED}.is_spot`;
    if (fields &&
      fields[cloudRegionKey] &&
      props.allowedInstanceTypes) {
      props.allowedInstanceTypes.setRegionId(+fields[cloudRegionKey]);
    } else if (fields &&
      fields[EXEC_ENVIRONMENT] &&
      fields[EXEC_ENVIRONMENT].cloudRegionId &&
      props.allowedInstanceTypes) {
      props.allowedInstanceTypes.setRegionId(+fields.exec.cloudRegionId);
    }
    if (fields &&
      fields[spotKey] &&
      fields[spotKey] !== undefined &&
      fields[spotKey] !== null &&
      props.allowedInstanceTypes) {
      props.allowedInstanceTypes.setIsSpot(`${fields[spotKey]}` === 'true');
    } else if (fields &&
      fields[ADVANCED] &&
      fields[ADVANCED].is_spot !== undefined &&
      fields[ADVANCED].is_spot !== null &&
      props.allowedInstanceTypes) {
      props.allowedInstanceTypes.setIsSpot(`${fields[ADVANCED].is_spot}` === 'true');
    }
  };

  onFieldsChange = (props, fields) => {
    if (this.launchForm &&
      this.launchForm.formFieldsChanged &&
      Object.values(fields).filter(v => !v.dirty).length > 0) {
      this.launchForm.formFieldsChanged();
    }
  };

  launchPipelineForm = Form.create({
    onValuesChange: this.onValuesChange,
    onFieldsChange: this.onFieldsChange
  })(LaunchPipelineForm);

  render () {
    const LaunchForm = this.launchPipelineForm;
    const props = this.props;
    return (
      <LaunchForm {...props} onInitialized={this.onInitialized} />
    );
  }
}
