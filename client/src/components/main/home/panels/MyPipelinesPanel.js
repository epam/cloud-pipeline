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
import {computed} from 'mobx';
import {Alert, message, Row} from 'antd';
import LoadPipeline from '../../../../models/pipelines/Pipeline';
import LoadingView from '../../../special/LoadingView';
import highlightText from '../../../special/highlightText';
import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';
import CardsPanel from './components/CardsPanel';
import {getDisplayOnlyFavourites} from '../utils/favourites';
import styles from './Panel.css';

@roleModel.authenticationInfo
@inject('pipelines')
@localization.localizedComponent
@observer
export default class MyPipelinesPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    panelKey: PropTypes.string,
    onInitialize: PropTypes.func
  };

  searchPipelineFn = (pipeline, search) => {
    return !search || search.length === 0 ||
      pipeline.name.toLowerCase().indexOf(search.toLowerCase()) >= 0;
  };

  @computed
  get pipelines () {
    if (this.props.pipelines.loaded && this.props.authenticatedUserInfo.loaded) {
      const result = (this.props.pipelines.value || [])
        .map(s => s)
        .filter(s => roleModel.writeAllowed(s) || roleModel.readAllowed(s) || roleModel.isOwner(s));
      result.sort((pA, pB) => {
        const pAisOwner = pA.owner && pA.owner.toLowerCase() === this.props.authenticatedUserInfo.value.userName.toLowerCase();
        const pBisOwner = pB.owner && pB.owner.toLowerCase() === this.props.authenticatedUserInfo.value.userName.toLowerCase();
        if (pAisOwner !== pBisOwner) {
          if (pAisOwner) {
            return -1;
          } else if (pBisOwner) {
            return 1;
          }
        }
        return pB.mask - pA.mask;
      });
      return result;
    }
    return [];
  }

  renderPipeline = (pipeline, search) => {
    return [
      <Row
        key="title"
        type="flex"
        align="middle"
        style={{fontWeight: 'bold', fontSize: 'larger', height: pipeline.description ? '50%' : '100%'}}>
        <span type="main">
          {highlightText(pipeline.name, search)}
        </span>
      </Row>,
      <Row
        className={styles.description}
        style={{height: pipeline.description ? '50%' : '100%'}}
        key="path">
        {pipeline.description}
      </Row>
    ];
  };

  renderContent = () => {
    const navigate = ({id}) => {
      this.props.router && this.props.router.push(`/${id}`);
    };
    const launch = async ({id}) => {
      const hide = message.loading(`Fetching ${this.localizedString('pipeline')}'s info...`, -1);
      const request = new LoadPipeline(id);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error);
      } else if (!request.value.currentVersion) {
        hide();
        message.error('Error fetching last version', 5);
      } else {
        hide();
        this.props.router &&
        this.props.router.push(`/launch/${id}/${request.value.currentVersion.name}`);
      }
    };
    const history = async ({id}) => {
      const hide = message.loading(`Fetching ${this.localizedString('pipeline')}'s info...`, -1);
      const request = new LoadPipeline(id);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error);
      } else if (!request.value.currentVersion) {
        hide();
        message.error('Error fetching last version', 5);
      } else {
        hide();
        this.props.router &&
        this.props.router.push(`/${id}/${request.value.currentVersion.name}/history`);
      }
    };
    return (
      <div key="cards" style={{flex: 1, overflow: 'auto'}}>
        <CardsPanel
          panelKey={this.props.panelKey}
          onClick={navigate}
          favouriteEnabled
          displayOnlyFavourites={getDisplayOnlyFavourites()}
          search={{
            placeholder: 'Search pipelines',
            searchFn: this.searchPipelineFn
          }}
          emptyMessage={search =>
            search && search.length
              ? `No ${this.localizedString('pipeline')}s found for '${search}'`
              : `There are no ${this.localizedString('pipeline')}s you have access to`
          }
          actions={[
            {
              title: 'RUN',
              icon: 'play-circle-o',
              action: launch
            },
            {
              title: 'HISTORY',
              icon: 'compass',
              action: history
            }
          ]}
          cardClassName={styles.pipelineCard}
          childRenderer={this.renderPipeline}>
          {this.pipelines}
        </CardsPanel>
      </div>
    );
  };

  render () {
    if (!this.props.pipelines.loaded && this.props.pipelines.pending) {
      return <LoadingView />;
    }
    if (this.props.pipelines.error) {
      return <Alert type="warning" message={this.props.pipelines.error} />;
    }
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container} style={{display: 'flex', flexDirection: 'column'}}>
        {this.renderContent()}
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
    this.props.pipelines.fetch();
  }
}
