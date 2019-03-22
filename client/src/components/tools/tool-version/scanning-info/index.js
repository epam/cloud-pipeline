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
import {inject, observer} from 'mobx-react/index';
import LoadTool from '../../../../models/tools/LoadTool';
import LoadToolScanTags from '../../../../models/tools/LoadToolScanTags';
import {
  Alert,
  Table,
  Popover
} from 'antd';
import {computed} from 'mobx';
import LoadingView from '../../../special/LoadingView';
import VersionScanResult from '../../elements/VersionScanResult';
import roleModel from '../../../../utils/roleModel';
import styles from './ToolScanningInfo.css';

const ASCEND = 'ascend';
const DESCEND = 'descend';

const PAGE_SIZE = 40;

@inject('preferences', 'dockerRegistries')
@inject((stores, {params}) => {
  return {
    ...stores,
    toolId: params.id,
    version: params.version,
    tool: new LoadTool(params.id),
    versions: new LoadToolScanTags(params.id)
  };
})
@observer
export default class ToolScanningInfo extends React.Component {
  state = {
    featureSortOrder: ASCEND,
    severitySortOrder: DESCEND
  };

  @computed
  get dockerRegistry () {
    if (this.props.dockerRegistries.loaded && this.props.tool.loaded) {
      return (this.props.dockerRegistries.value.registries || [])
        .filter(r => r.id === this.props.tool.value.registryId)[0];
    }
    return null;
  }

  @computed
  get scanningInfo () {
    if (this.props.versions.loaded &&
      this.props.versions.value &&
      this.props.versions.value.toolVersionScanResults) {
      return this.props.versions.value.toolVersionScanResults[this.props.version];
    }
    return null;
  }

  @computed
  get groupingVulnerabilities () {
    let groupingVulnerabilities = [];
    if (this.scanningInfo && this.scanningInfo.vulnerabilities) {
      this.scanningInfo.vulnerabilities.forEach(vulnerability => {
        const name = `${vulnerability.feature} ${vulnerability.featureVersion}`;
        vulnerability.keyName = `${name}_${vulnerability.name}`;
        if (groupingVulnerabilities.map(v => v.name).includes(name)) {
          const group = groupingVulnerabilities.find(t => t.name === name);
          group.children.push(vulnerability);
        } else {
          groupingVulnerabilities.push({
            feature: vulnerability.feature,
            featureVersion: vulnerability.featureVersion,
            name: name,
            children: [vulnerability],
            isFeature: true,
            keyName: name
          });
        }
      });
    }
    const getFeatureVulnerabilitiesInfo = (feature) => {
      return (feature.children || [])
        .map(i => (i.severity || 'Unknown').toLowerCase())
        .reduce((info, obj) => {
          if (info.hasOwnProperty(obj)) {
            info[obj] += 1;
          } else {
            info[obj] = 1;
          }
          return info;
        }, {});
    };
    groupingVulnerabilities.forEach(vulnerability => {
      vulnerability.children.sort((a, b) => -this.severitySorter(a, b));
      vulnerability.info = getFeatureVulnerabilitiesInfo(vulnerability);
    });
    return groupingVulnerabilities.sort(this.featureNameSorter);
  }

  severitySorter = (a, b) => {
    if (a.info || b.info) {
      return this.severityInfoSorter(a, b);
    }
    const sortPriority = {
      critical: 4,
      high: 3,
      medium: 2,
      low: 1,
      negligible: 0,
      unknown: -1
    };

    const severityA = sortPriority[(a.severity || 'unknown').toLowerCase()];
    const severityB = sortPriority[(b.severity || 'unknown').toLowerCase()];
    if (severityA === severityB) {
      return 0;
    } else if (severityA > severityB) {
      return 1;
    } else {
      return -1;
    }
  };

  severityInfoSorter = (a, b) => {
    const infoA = a.info;
    const infoB = b.info;
    if (!infoA && !infoB) {
      return 0;
    } else if (!infoA && infoB) {
      return -1;
    } else if (infoA && !infoB) {
      return 1;
    } else {
      const getComparisonResult = (severity) => {
        const severityA = infoA[severity] || 0;
        const severityB = infoB[severity] || 0;
        if (severityA > severityB) {
          return 1;
        } else if (severityA < severityB) {
          return -1;
        } else {
          return undefined;
        }
      };
      return getComparisonResult('critical') ||
        getComparisonResult('high') ||
        getComparisonResult('medium') ||
        getComparisonResult('low') ||
        getComparisonResult('negligible') || 0;
    }
  };

  featureNameSorter = (a, b) => {
    const nameA = a.name;
    const nameB = b.name;
    if (nameA === nameB) {
      return 0;
    } else if (nameA > nameB) {
      return 1;
    } else {
      return -1;
    }
  };

  renderVulnerabilityTable = () => {
    const columns = [{
      title: 'Component',
      key: 'feature',
      dataIndex: 'name',
      sorter: this.featureNameSorter,
      render: (name, item) => {
        if (item.isFeature) {
          return <b>{name}</b>;
        } else {
          let nameComponent;
          if (item.link) {
            nameComponent = <a href={item.link} target="_blank">{name}</a>;
          } else {
            nameComponent = name;
          }
          let nameComponentWithDesciption;
          if (item.description) {
            nameComponentWithDesciption = (
              <Popover
                placement="right"
                content={
                  <div style={{width: 300}}>
                    {item.description}
                  </div>
                }>
                <span>{nameComponent}</span>
              </Popover>
            );
          } else {
            nameComponentWithDesciption = nameComponent;
          }
          if (item.fixedBy) {
            return <span>{nameComponentWithDesciption} <span className={styles.fixedBy}>fixed by {item.fixedBy}</span></span>;
          } else {
            return nameComponentWithDesciption;
          }
        }
      }
    }, {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      sorter: this.severitySorter,
      className: styles.severityColumn,
      render: (severity, item) => {
        if (item.isFeature && item.info) {
          return (
            <div style={{width: 100, height: 7}}>
              <VersionScanResult result={item.info} />
            </div>
          );
        } else if (severity) {
          return <span className={styles[`vulnerabilitySeverity${severity}`]}>{severity}</span>;
        } else {
          return null;
        }
      }
    }];
    return (
      <Table
        className={styles.vulnerabilityTable}
        columns={columns}
        rowKey="keyName"
        rowClassName={(item) => item.isFeature ? undefined : styles.vulnerabilityRow}
        pagination={{pageSize: PAGE_SIZE}}
        dataSource={this.groupingVulnerabilities}
        locale={{emptyText: 'No vulnerabilities found'}}
        size="small" />
    );
  };

  render () {
    if (!this.props.preferences.toolScanningEnabledForRegistry(this.dockerRegistry)) {
      return null;
    }
    if ((!this.props.versions.loaded && this.props.versions.pending) ||
      (!this.props.tool.loaded && this.props.tool.pending)) {
      return <LoadingView />;
    }
    if (this.props.versions.error) {
      return <Alert type="error" message={this.props.versions.error} />;
    }
    if (this.props.tool.error) {
      return <Alert type="error" message={this.props.tool.error} />;
    }
    if (!roleModel.readAllowed(this.props.tool.value)) {
      return (
        <Alert type="error" message="You have no permissions to view tool details" />
      );
    }

    return this.renderVulnerabilityTable();
  }
}
