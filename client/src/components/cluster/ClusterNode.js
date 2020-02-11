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

import React, {Component} from 'react';
import {Alert, Menu, Row, Col, Card, Button} from 'antd';
import AdaptedLink from '../special/AdaptedLink';
import {Link} from 'react-router';
import clusterNodes from '../../models/cluster/ClusterNodes';
import {ChartsData} from './charts';
import {inject, observer} from 'mobx-react';
import styles from './ClusterNode.css';
import parentStyles from './Cluster.css';
import {renderNodeLabels as generateNodeLabels} from './renderers';
import {PIPELINE_INFO_LABEL} from './node-roles';

@inject((stores, {params, location}) => {
  const {from, to} = location?.query;
  return {
    name: params.nodeName,
    node: clusterNodes.getNode(params.nodeName),
    chartsData: new ChartsData(params.nodeName, from, to)
  };
})
@observer
class ClusterNode extends Component {
  refreshNodeInstance = () => {
    if (!this.props.node.pending) {
      this.props.node.fetch();
    }
    if (!this.props.chartsData.pending) {
      this.props.chartsData.fetch();
    }
  };

  renderError = () => {
    if (!this.props.node.pending && this.props.node.error) {
      const activeTab = this.props.router.location.pathname.split('/').slice(-1)[0];
      if (/^monitor$/i.test(activeTab)) {
        return null;
      }
      return (
        <div key="error">
          <br />
          <Alert
            message={`The node '${this.props.name}' was not found or was removed`}
            type="warning"
          />
        </div>
      );
    }
    return null;
  };

  renderMenu = () => {
    if (this.props.node.pending || this.props.node.error) {
      return null;
    }
    const activeTab = this.props.router.location.pathname.split('/').slice(-1)[0];
    return (
      <Row gutter={16} type="flex" className={styles.rowMenu} key="menu">
        <Menu
          mode="horizontal"
          selectedKeys={[activeTab]}
          className={styles.tabsMenu}>
          <Menu.Item key="info">
            <AdaptedLink
              id="cluster-node-tab-info"
              to={`/cluster/${this.props.name}/info`}
              location={this.props.router.location}>General info</AdaptedLink>
          </Menu.Item>
          <Menu.Item key="jobs">
            <AdaptedLink
              id="cluster-node-tab-jobs"
              to={`/cluster/${this.props.name}/jobs`}
              location={this.props.router.location}>Jobs</AdaptedLink>
          </Menu.Item>
          <Menu.Item key="monitor">
            <AdaptedLink
              id="cluster-node-tab-monitor"
              to={`/cluster/${this.props.name}/monitor`}
              location={this.props.router.location}>Monitor</AdaptedLink>
          </Menu.Item>
        </Menu>
      </Row>
    );
  };

  renderNodeLabels = () => {
    if (this.props.node.error) {
      return null;
    }
    const labels = Object.assign({}, this.props.node.value ? this.props.node.value.labels : {});

    if (this.props.node.value && this.props.node.value.pipelineRun) {
      const {pipelineName, version, dockerImage} = this.props.node.value.pipelineRun;
      if (pipelineName) {
        labels[PIPELINE_INFO_LABEL] =
          `${pipelineName} (${version})`;
      } else if (dockerImage) {
        const parts = dockerImage.split('/');
        labels[PIPELINE_INFO_LABEL] = `${parts[parts.length - 1]}`;
      }
    }

    return generateNodeLabels(
      labels,
      {
        className: parentStyles.nodeLabel,
        onlyKnown: true,
        additionalStyle: {
          fontSize: 'smaller',
          marginBottom: 2
        },
        location: this.props.router.location,
        pipelineRun: this.props.node.value ? this.props.node.value.pipelineRun : null
      });
  };

  render () {
    const result = [
      this.renderError(),
      this.renderMenu(),
      React.Children.map(this.props.children,
        (child) => React.cloneElement(
          child,
          {
            node: this.props.node,
            chartsData: this.props.chartsData
          }
        )
      )
    ];
    const nodeLabels = this.renderNodeLabels();
    return (
      <Card
        key={this.props.name}
        className={styles.nodeCard}
        bodyStyle={{
          padding: 15,
          display: 'flex',
          flexDirection: 'column',
          flex: 'auto',
          height: '100%'
        }}>
        <Row align="middle">
          <Col span={1}>
            <Link id="back-button" to="/cluster"><Button type="link" icon="arrow-left" /></Link>
          </Col>
          <Col span={21}>
            <span className={parentStyles.nodeMainInfo}>
              Node: {this.props.name}{nodeLabels}</span>
          </Col>
          <Col span={2} className={parentStyles.refreshButtonContainer}>
            <Button
              id="refresh-cluster-node-button"
              onClick={this.refreshNodeInstance}
              disabled={this.props.node.pending || this.props.chartsData.pending}>
              Refresh
            </Button>
          </Col>
        </Row>
        {result}
      </Card>
    );
  }
}

export default ClusterNode;
