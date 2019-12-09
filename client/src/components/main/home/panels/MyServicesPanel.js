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
import ToolImage from '../../../../models/tools/ToolImage';
import LoadingView from '../../../special/LoadingView';
import localization from '../../../../utils/localization';
import parseRunServiceUrl from '../../../../utils/parseRunServiceUrl';
import {Alert, Icon, Row} from 'antd';
import CardsPanel from './components/CardsPanel';
import getServiceActions from './components/getServiceActions';
import roleModel from '../../../../utils/roleModel';
import styles from './Panel.css';
import {AccessTypes} from '../../../../models/pipelines/PipelineRunUpdateSids';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('dockerRegistries')
@observer
export default class MyServicesPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    panelKey: PropTypes.string,
    services: PropTypes.object,
    onInitialize: PropTypes.func
  };

  getTool = (dockerImage) => {
    if (this.props.dockerRegistries.loaded && dockerImage) {
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
          return [imageRegistry, imageGroup, im];
        }
      }
    }
    return [];
  };

  renderService = (service) => {
    let name = service.name || service.url;
    if (!name && service.sshAccess) {
      name = 'SSH Access';
    }
    const [imageRegistry, , tool] = this.getTool(service.run.dockerImage);
    const [reg, group, dockerImage] = service.run.dockerImage.split('/');
    const renderMainInfo = () => {
      return [
        <Row key="name" style={{fontSize: 'larger', fontWeight: 'bold'}}>
          <span type="main">{name}</span>
        </Row>,
        <Row key="docker image" style={{fontSize: 'smaller'}}>
          <span>{imageRegistry ? imageRegistry.description || imageRegistry.path : reg}</span>
          <Icon type="caret-right" style={{fontSize: 'smaller', margin: '0 2px'}} />
          <span>{group}</span>
          <Icon type="caret-right" style={{fontSize: 'smaller', margin: '0 2px'}} />
          <span>{dockerImage}</span>
        </Row>,
        <Row key="run" style={{fontSize: 'smaller'}}>
          <span>{service.run.podId || `run #${service.run.id}`}</span>
        </Row>
      ];
    };
    if (tool && tool.iconId) {
      return (
        <Row type="flex" align="middle" justify="start" style={{height: '100%'}}>
          <div style={{marginRight: 10, overflow: 'hidden', width: 44, height: 44, position: 'relative'}}>
            <img src={ToolImage.url(tool.id, tool.iconId)} style={{width: '100%'}} />
            <Icon
              type="right-square"
              style={{
                position: 'absolute',
                bottom: 0,
                right: 0,
                zIndex: 1,
                fontSize: 'larger',
                backgroundColor: 'white'
              }} />
          </div>
          <div style={{flex: 1, display: 'flex', flexDirection: 'column'}}>
            {renderMainInfo()}
          </div>
        </Row>
      );
    } else {
      return (
        <div
          style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
          {renderMainInfo()}
        </div>
      );
    }
  };

  renderContent = () => {
    let content;
    if (!this.props.services.loaded && this.props.services.pending) {
      content = <LoadingView />;
    } else if (this.props.services.error) {
      content = <Alert type="warning" message={this.props.services.error} />;
    } else if (
      !this.props.authenticatedUserInfo.loaded &&
      this.props.authenticatedUserInfo.pending
    ) {
      content = <LoadingView />;
    } else if (this.props.authenticatedUserInfo.error) {
      content = <Alert type="warning" message={this.props.authenticatedUserInfo.error} />;
    } else {
      const userName = this.props.authenticatedUserInfo.value;
      const services = (this.props.services.value || [])
        .filter(r => r.status === 'RUNNING')
        .map(r => {
          const {runSids} = r;
          const [accessType] = (runSids || [])
            .filter(s => s.name === userName && s.isPrincipal)
            .map(s => s.accessType);
          return {
            run: r,
            sshAccess: accessType === AccessTypes.ssh || roleModel.isOwner(r),
            urls: r.serviceUrl ? (parseRunServiceUrl(r.serviceUrl) || []) : []
          };
        })
        .reduce((array, obj) => {
          const urls = obj.urls.map(u => ({...u, run: obj.run}));
          if (urls.length === 0) {
            // only ssh
            urls.push({run: obj.run, sshAccess: obj.sshAccess});
          }
          array.push(...urls);
          return array;
        }, []);
      const navigate = ({url}) => {
        if (url) {
          window.open(url, '_blank').focus();
        }
      };
      content = (
        <CardsPanel
          panelKey={this.props.panelKey}
          onClick={navigate}
          emptyMessage="There are no services"
          cardStyle={{width: '100%'}}
          actions={
            getServiceActions(
              this.props.authenticatedUserInfo,{
                ssh: (url) => window.open(url, '_blank').focus()
              })
          }
          childRenderer={this.renderService}>
          {services}
        </CardsPanel>
      );
    }
    return content;
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container}>
        {this.renderContent()}
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }
}
