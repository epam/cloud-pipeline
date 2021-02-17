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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  Input,
  Row,
  Select
} from 'antd';
import LoadTool from '../../../../models/tools/LoadTool';
import LoadToolAttributes from '../../../../models/tools/LoadToolAttributes';
import LoadingView from '../../../special/LoadingView';
import highlightText from '../../../special/highlightText';
import styles from './packages.css';

@inject((stores, {params}) => {
  return {
    toolId: params.id,
    version: params.version,
    tool: new LoadTool(params.id),
    versions: new LoadToolAttributes(params.id, params.version)
  };
})
@observer
export default class Packages extends React.Component {

  state = {
    filterDependencies: null,
    selectedEcosystem: null
  };

  @computed
  get ecosystems () {
    if (this.props.versions.loaded &&
        this.props.versions.value.scanResult) {
      const result = (this.props.versions.value.scanResult.dependencies || [])
        .map(d => d.ecosystem)
        .filter((ecosystem, index, array) => array.indexOf(ecosystem) === index);
      result.sort();
      return result;
    }
    return [];
  }

  filterDependenciesFn = (dependency) => {
    if (this.state.filterDependencies) {
      return (dependency.name || '').toLowerCase().indexOf(this.state.filterDependencies.toLowerCase()) >= 0;
    }
    return true;
  };

  static sortDependencies = (a, b) => {
    if ((a.name || '').toLowerCase() > (b.name || '').toLowerCase()) {
      return 1;
    } else if ((a.name || '').toLowerCase() < (b.name || '').toLowerCase()) {
      return -1;
    }
    return 0;
  };

  @computed
  get dependencies () {
    if (this.props.versions.loaded &&
        this.state.selectedEcosystem &&
        this.ecosystems.length > 0) {
      const result = (this.props.versions.value.scanResult.dependencies || [])
        .filter(d => d.ecosystem === this.state.selectedEcosystem);
      result.sort(Packages.sortDependencies);
      return result;
    }
    return [];
  }

  @computed
  get filteredDependencies () {
    if (this.state.filterDependencies && this.props.versions.loaded &&
      this.props.versions.value.scanResult) {
      const filterDependenciesString = (this.state.filterDependencies || '').toLowerCase();
      const result = (this.props.versions.value.scanResult.dependencies || [])
        .filter(
          d => d.ecosystem !== this.state.selectedEcosystem &&
          (d.name || '').toLowerCase().indexOf(filterDependenciesString) >= 0
        );
      result.sort(Packages.sortDependencies);
      return result;
    }
    return [];
  };

  onChangeEcoSystem = (e) => {
    this.setState({
      selectedEcosystem: e
    });
  };

  renderSeparator = (text) => {
    return (
      <Row type="flex" style={{margin: 10}}>
        <table style={{width: '100%'}}>
          <tbody>
          <tr>
            <td style={{width: '50%'}}>
              <div
                style={{
                  margin: '0 5px',
                  verticalAlign: 'middle',
                  height: 1,
                  backgroundColor: '#ccc'
                }}>{'\u00A0'}</div>
            </td>
            <td style={{width: 1, whiteSpace: 'nowrap'}}><b>{text}</b></td>
            <td style={{width: '50%'}}>
              <div
                style={{
                  margin: '0 5px',
                  verticalAlign: 'middle',
                  height: 1,
                  backgroundColor: '#ccc'
                }}>{'\u00A0'}</div>
            </td>
          </tr>
          </tbody>
        </table>
      </Row>
    );
  };

  onSearchChanged = (e) => {
    this.setState({filterDependencies: e.target.value});
  };

  renderDependency = (includePackage=false) => (d, index) => {
    return (
      <li key={index} className={styles.dependency}>
        <b>{highlightText(d.name, this.state.filterDependencies)} v{d.version}</b>
        {
          includePackage
            ? <span>({d.ecosystem})</span>
            : undefined
        }
        {
          d.description
            ? <span className={styles.description}> - {d.description}</span>
            : undefined
        }
      </li>
    );
  };

  render () {
    if (!this.props.versions.loaded && this.props.versions.pending) {
      return <LoadingView />;
    }
    if (this.props.versions.error) {
      return <Alert type="error" message={this.props.versions.error} />;
    }
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" align="middle">
          <b>Ecosystem:</b>
          <Select
            value={this.state.selectedEcosystem}
            onSelect={this.onChangeEcoSystem}
            style={{flex: 1, marginLeft: 5}}
            showSearch
            filterOption={(input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }>
            {this.ecosystems.map(e => <Select.Option key={e} value={e}>{e}</Select.Option>)}
          </Select>
          <Input.Search
            value={this.state.filterDependencies}
            placeholder="Filter dependencies"
            onChange={this.onSearchChanged}
            style={{flex: 1, marginLeft: 5}} />
        </Row>
        <div style={{marginTop: 8, flex: 1, overflow: 'auto'}}>
          <ul
            className={styles.dependencies}>
            {
              this.dependencies.filter(this.filterDependenciesFn).map(this.renderDependency())
            }
          </ul>
          {
            this.filteredDependencies.length > 0 &&
            <div>
              {this.renderSeparator('Global search')}
              <ul
                className={styles.dependencies}>
                {
                  this.filteredDependencies.map(this.renderDependency(true))
                }
              </ul>
            </div>
          }
        </div>
      </div>
    );
  }

  componentDidUpdate () {
    if (!this.state.selectedEcosystem && this.ecosystems.length > 0) {
      this.setState({
        selectedEcosystem: this.ecosystems[0]
      });
    }
  }
}
