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
import {Alert, Row} from 'antd';
import LoadingView from '../../../special/LoadingView';
import highlightText from '../../../special/highlightText';
import AWSRegionTag from '../../../special/AWSRegionTag';
import roleModel from '../../../../utils/roleModel';
import CardsPanel from './components/CardsPanel';
import {getDisplayOnlyFavourites} from '../utils/favourites';
import styles from './Panel.css';

@roleModel.authenticationInfo
@inject('dataStorages')
@observer
export default class MyDataPanel extends React.Component {

  static propTypes = {
    panelKey: PropTypes.string,
    onInitialize: PropTypes.func
  };

  state = {
    search: null
  };

  searchStorageFn = (item, search) => {
    return !search || search.length === 0 ||
      item.name.toLowerCase().indexOf(search.toLowerCase()) >= 0 ||
      (item.pathMask && item.pathMask.toLowerCase().indexOf(search.toLowerCase()) >= 0);
  };

  @computed
  get storages () {
    if (this.props.dataStorages.loaded && this.props.authenticatedUserInfo.loaded) {
      const result = (this.props.dataStorages.value || [])
        .map(s => s)
        .filter(s => roleModel.writeAllowed(s) || roleModel.readAllowed(s) || roleModel.isOwner(s));
      result.sort((sA, sB) => {
        const sAisOwner = sA.owner && sA.owner.toLowerCase() === this.props.authenticatedUserInfo.value.userName.toLowerCase();
        const sBisOwner = sB.owner && sB.owner.toLowerCase() === this.props.authenticatedUserInfo.value.userName.toLowerCase();
        if (sAisOwner !== sBisOwner) {
          if (sAisOwner) {
            return -1;
          } else if (sBisOwner) {
            return 1;
          }
        }
        return sB.mask - sA.mask;
      });
      return result;
    }
    return [];
  }

  renderStorage = (storage, search) => {
    return [
      <Row
        key="title"
        type="flex"
        align="middle"
        style={{fontWeight: 'bold', fontSize: 'larger'}}>
        <span className={styles.storageType} type={storage.type.toUpperCase()}>{storage.type.toUpperCase()}</span>
        <span type="main" style={{marginLeft: 5}}>
          <span className="storage-name">{highlightText(storage.name, search)}</span>
          <AWSRegionTag regionId={storage.regionId} size="small" />
        </span>
      </Row>,
      <Row key="path">
        {highlightText(storage.pathMask, search)}
      </Row>
    ];
  };

  renderContent = () => {
    const navigate = ({id}) => {
      this.props.router && this.props.router.push(`/storage/${id}`);
    };
    return (
      <div key="cards" style={{flex: 1, overflow: 'auto'}}>
        <CardsPanel
          panelKey={this.props.panelKey}
          onClick={navigate}
          favouriteEnabled
          displayOnlyFavourites={getDisplayOnlyFavourites()}
          search={{
            placeholder: 'Search storages',
            searchFn: this.searchStorageFn
          }}
          emptyMessage={(search) =>
            search && search.length
              ? `No personal data storages found for '${search}'`
              : 'There are no personal data storages'
          }
          childRenderer={this.renderStorage}>
          {this.storages}
        </CardsPanel>
      </div>
    );
  };

  render () {
    if (!this.props.dataStorages.loaded && this.props.dataStorages.pending) {
      return <LoadingView />;
    }
    if (this.props.dataStorages.error) {
      return <Alert type="warning" message={this.props.dataStorages.error} />;
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
    this.props.dataStorages.fetch();
  }
}
