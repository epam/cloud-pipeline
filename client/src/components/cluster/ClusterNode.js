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
import ClusterNodeUsage from '../../models/cluster/ClusterNodeUsage';
import {inject, observer} from 'mobx-react';
import styles from './ClusterNode.css';
import parentStyles from './Cluster.css';
import {renderNodeLabels} from './renderers';
import {PIPELINE_INFO_LABEL} from './node-roles';

@inject(({}, {params}) => {
  return {
    name: params.nodeName,
    node: clusterNodes.getNode(params.nodeName),
    usage: new ClusterNodeUsage(params.nodeName)
  };
})
@observer
export default class ClusterNode extends Component {

  refreshNodeInstance = () => {
    if (!this.props.node.pending) {
      this.props.node.fetch();
    }
    if (!this.props.usage.pending) {
      this.props.usage.fetch();
    }
  };

  render () {
    const activeTab = this.props.router.location.pathname.split('/').slice(-1)[0];
    let result = null;
    if (!this.props.node.pending && this.props.node.error) {
      result = (
        <div>
          <br />
          <Alert message={`The node '${this.props.name}' was not found or was removed`} type="warning"/>
        </div>
      );
    } else {
      result = [
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
        </Row>,
        React.Children.map(this.props.children,
          (child) => React.cloneElement(
            child,
            {
              node: this.props.node,
              usage: this.props.usage
            }
          )
        )
      ];
    }

    const labels = Object.assign({}, this.props.node.value ? this.props.node.value.labels : {});

    if (this.props.node.value && this.props.node.value.pipelineRun) {
      if (this.props.node.value.pipelineRun.pipelineName) {
        labels[PIPELINE_INFO_LABEL] = `${this.props.node.value.pipelineRun.pipelineName} (${this.props.node.value.pipelineRun.version})`;
      } else if (this.props.node.value.pipelineRun.dockerImage) {
        const parts = this.props.node.value.pipelineRun.dockerImage.split('/');
        labels[PIPELINE_INFO_LABEL] = `${parts[parts.length - 1]}`;
      }
    }

    const nodeLabels = renderNodeLabels(
      labels,
      {
        className: parentStyles.nodeLabel,
        onlyKnown: true,
        additionalStyle: {
          fontSize: 'smaller',
          marginLeft: 10,
          marginBottom: 2
        },
        location: this.props.router.location,
        pipelineRun: this.props.node.value ? this.props.node.value.pipelineRun : null
      });

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
              disabled={this.props.node.pending || this.props.usage.pending}>
              Refresh
            </Button>
          </Col>
        </Row>
        {result}
      </Card>
    );
  }

}
