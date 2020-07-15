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
import {Icon, Popover, Row} from 'antd';
import styles from './LaunchPipelineForm.css';

function renderHint (localizedStringFn, hint, placement, style) {
  return (
    <Popover placement={placement || 'right'} content={hint(localizedStringFn)} trigger="hover">
      <Icon type="question-circle" className={styles.hint} style={style || {marginLeft: 5}} />
    </Popover>
  );
}

const pipelineHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    Specify a <b>{localizedStringFn('pipeline')}</b> that will be used to run current configuration.
    <br />
    When this option is defined - <b>command template</b> and <b>parameters list</b> can not be modified,
    as they will be inherited from a selected <b>{localizedStringFn('pipeline')}</b>.
  </Row>
);

const dockerImageHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    This <b>docker image</b> will be used to execute a current job script. <br />
    Select <b>docker image</b> that provides required environment and set of tools
  </Row>
);

const awsRegionHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    Select <b>cloud region</b>.<br />
  </Row>
);

const awsRegionRestrictedByToolSettingsHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    <b>Cloud region</b> selection is restricted to a specific region by the current <b>Docker image</b>'s settings.
  </Row>
);

const instanceTypeHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    Select <b>type</b> of a calculation <b>instance</b> that will execute current job. <br />
    Tune type of an instance to fulfill job's hardware requirements in terms of <b>CPUs</b>, <b>GPUs</b> and <b>Memory</b>.
    <br />
    <br />
    To launch a number of calculation instances - tick <b>"Launch cluster"</b> checkbox and specify number of additional <b>worker nodes</b>.
    <br />
    This will run current job on a master node that is connected to the workers via SSH
  </Row>
);

const diskHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    Define <b>disk storage</b> for the selected calculation instance type.
    <br />
    This volume will be available for the job within an instance at runtime
  </Row>
);

const startIdleHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    <b>Command template</b> is a shell script that will be executed as first process within a calculation instance.
    It can be treated as an entrypoint for the job.
    Typically job initialization script shall be called in command template.
    <br />
    <br />
    All values that are specified in the <b>"Parameters"</b> section - will be available within a command
    template and downstream processes as environment variables.
    <br />
    <br />
    If no specific job is required to run and only SSH access is required - tick <b>"Start idle"</b> checkbox.
    <br />
    When a job is started idle - container will be executed with "sleep infinity" command and will be available via SSH
  </Row>
);

const priceTypeHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    <b>{localizedStringFn('Spot')}</b> type will provide ~3 times lower prices, but may introduce longer startup time and accidental node failure.<br />
    <b>{localizedStringFn('On-demand')}</b> type is more expensive, but provides solid init and run time behavior.<br />
    Use <b>{localizedStringFn('Spot')}</b> for testing and debugging purposes
  </Row>
);

const autoPauseHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    You can disable auto-pause for this run
  </Row>
);

const timeOutHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    If defined - will terminate job when specified duration in minutes is elapsed
  </Row>
);

const limitMountsHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    You can specify storages that should be mounted
  </Row>
);

const prettyUrlHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    This value will be used in the <b>Endpoint URL</b> instead of the general
    "/pipeline-run_id-port_number" string.
    This value shall be unique across all runs. <br />
    Or you can choose specific <b>domain name</b> with or without <b>endpoint</b>.<br />

    For example: <br />
    - <i>endpoint-url</i><br />
    - <i>my.host.com</i><br />
    - <i>my.host.com/endpoint-url</i>
  </Row>
);

const endpointNameHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    This value specifies which <b>tool endpoint</b> will be used to process <b>serverless API</b> calls
  </Row>
);

const stopAfterHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    This value specifies how long shall the job be kept running after the last <b>serverless API</b> call
  </Row>
);

const executionEnvironmentSummaryHint = (localizedStringFn) => (
  <Row style={{maxWidth: 300}}>
    General amount of resources that will be allocated during the run execution. Notice that in some specific configurations such as <b>hybrid autoscaling clusters</b> amount of resources can vary beyond the shown interval.
  </Row>
);

const hints = {
  renderHint,
  pipelineHint,
  dockerImageHint,
  instanceTypeHint,
  awsRegionHint,
  awsRegionRestrictedByToolSettingsHint,
  diskHint,
  startIdleHint,
  priceTypeHint,
  autoPauseHint,
  timeOutHint,
  limitMountsHint,
  prettyUrlHint,
  executionEnvironmentSummaryHint,
  endpointNameHint,
  stopAfterHint
};

export default hints;
