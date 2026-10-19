/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Button,
  Icon,
  message
} from 'antd';
import {computed} from 'mobx';
import LoadingView from '../../special/LoadingView';
import EditHotNodePool from './edit-hot-node-pool';
import clusterNodes from '../../../models/cluster/ClusterNodes';
import pools from '../../../models/cluster/HotNodePools';
import reservations from '../../../models/cluster/CapacityReservations';
import HotNodePoolUpdate from '../../../models/cluster/HotNodePoolUpdate';
import HotNodePoolDelete from '../../../models/cluster/HotNodePoolDelete';
import HotNodePoolScheduleUpdate from '../../../models/cluster/HotNodePoolScheduleUpdate';
import HotNodePoolScheduleDelete from '../../../models/cluster/HotNodePoolScheduleDelete';
import PoolCard from './pool-card';
import DeletePoolModal from './delete-pool-modal';
import styles from './hot-node-pool.css';
import roleModel from '../../../utils/roleModel';
import {
  getPoolPermissions,
  canEditPool,
  canDeletePool,
  canViewPoolUsage
} from './permissions';
import {MODE_DEFAULT, MODE_FUTURE_DATED} from './edit-hot-node-pool/constants';

async function updateSchedule (payload, scheduleId) {
  const scheduleRequest = new HotNodePoolScheduleUpdate();
  await scheduleRequest.send({...(payload || {}), id: scheduleId});
  if (scheduleRequest.error || !scheduleRequest.loaded) {
    throw new Error(scheduleRequest.error || 'Error creating schedule');
  }
  const {id} = scheduleRequest.value;
  return id;
}

async function removeSchedule (scheduleId) {
  const scheduleDeleteRequest = new HotNodePoolScheduleDelete(scheduleId);
  await scheduleDeleteRequest.send();
  if (scheduleDeleteRequest.error || !scheduleDeleteRequest.loaded) {
    throw new Error(scheduleDeleteRequest.error || 'Error removing schedule');
  }
}

@roleModel.authenticationInfo
@inject(() => ({
  pools,
  clusterNodes,
  reservations
}))
@observer
class HotCluster extends React.Component {
  state = {
    createNewPool: false,
    editablePool: undefined,
    poolToRemove: undefined,
    pending: false
  };

  operationWrapper = (fn) => (...opts) => {
    this.setState({
      pending: true
    }, () => {
      const onFinish = () => {
        this.setState({
          pending: false
        });
      };
      fn(...opts)
        .then(onFinish)
        .catch(onFinish);
    });
  };

  @computed
  get permissions () {
    const {authenticatedUserInfo} = this.props;
    return getPoolPermissions(authenticatedUserInfo);
  }

  @computed
  get currentUserName () {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded && authenticatedUserInfo.value) {
      return authenticatedUserInfo.value.userName;
    }
    return undefined;
  }

  openEditPoolModal = (options) => (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    const {
      isNew,
      pool
    } = options || {};
    if (isNew) {
      this.setState({
        createNewPool: true,
        editablePool: undefined
      });
    } else if (pool) {
      if (!canEditPool(pool, this.props.authenticatedUserInfo)) {
        return;
      }
      this.setState({
        createNewPool: false,
        editablePool: pool
      });
    }
  };

  onRemovePool = (pool) => (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    if (pool && canDeletePool(pool, this.props.authenticatedUserInfo)) {
      this.setState({poolToRemove: pool});
    }
  };

  closeRemovePoolModal = () => {
    this.setState({poolToRemove: undefined});
  };

  removePool = async (force) => {
    const {poolToRemove} = this.state;
    if (!poolToRemove) {
      return;
    }
    const {pools, clusterNodes: nodes} = this.props;
    const hide = message.loading('Removing pool...', -1);
    const request = new HotNodePoolDelete(poolToRemove.id, force);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error, 5);
      return;
    }
    if (poolToRemove.schedule && poolToRemove.schedule.id) {
      const scheduleRequest = new HotNodePoolScheduleDelete(poolToRemove.schedule.id);
      await scheduleRequest.fetch();
      if (scheduleRequest.error) {
        hide();
        message.error(scheduleRequest.error, 5);
        return;
      }
    }
    await pools.fetch();
    await nodes.fetch();
    hide();
    this.closeRemovePoolModal();
  };

  closeEditPoolModal = () => {
    this.setState({
      createNewPool: false,
      editablePool: undefined
    });
  };

  onSaveEditablePool = async (pool, schedule) => {
    const {createNewPool, editablePool} = this.state;
    const {pools, clusterNodes: nodes, reservations: res} = this.props;
    const loadingTitle = createNewPool ? 'Creating new pool...' : 'Update pool...';
    const hide = message.loading(loadingTitle, -1);
    try {
      const {id, schedule: currentSchedule} = editablePool || {};
      const currentPoolId = createNewPool ? undefined : id;
      const currentScheduleId = currentSchedule && !createNewPool
        ? currentSchedule.id
        : undefined;
      let scheduleId = currentScheduleId;
      const hasSchedule = schedule &&
        schedule.scheduleEntries &&
        schedule.scheduleEntries.length > 0;
      if (hasSchedule) {
        scheduleId = await updateSchedule(schedule, scheduleId);
      } else {
        scheduleId = undefined;
      }
      const request = new HotNodePoolUpdate();
      await request.send({
        id: currentPoolId,
        scheduleId,
        ...pool
      });
      if (request.error) {
        const errorTitle = createNewPool ? 'Error creating pool' : 'Error updating pool';
        const message = `${errorTitle}: ${request.error}`;
        throw new Error(message);
      }
      if (currentScheduleId && !hasSchedule) {
        await removeSchedule(currentScheduleId);
      }
      await Promise.all([pools.fetch(), nodes.fetch(), res.fetch()]);
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
    }
    this.closeEditPoolModal();
  };

  refresh = async () => {
    const {pools: poolsRequest, clusterNodes: nodes, reservations: res} = this.props;
    await Promise.all([poolsRequest.fetch(), nodes.fetch(), res.fetch()]);
  };

  onPoolClick = (pool) => {
    const {router} = this.props;
    router.push(`/cluster?pool_id=${pool.id}`);
  }

  render () {
    const {
      pools: poolsRequest,
      clusterNodes: nodes,
      reservations: res,
      router
    } = this.props;
    if (
      (poolsRequest.pending && !poolsRequest.loaded) ||
      (nodes.pending && !nodes.loaded)
    ) {
      return (
        <LoadingView />
      );
    }
    if (poolsRequest.error || !poolsRequest.loaded) {
      return (
        <Alert
          type="error"
          message={poolsRequest.error || 'Error fetching hot node pools'}
        />
      );
    }
    if (nodes.error || !nodes.loaded) {
      return (
        <Alert
          type="error"
          message={nodes.error || 'Error fetching cluster nodes'}
        />
      );
    }
    const {value: pools = []} = poolsRequest;
    // Reservations are rendered without a loading guard, so `res.value` can still be
    // the model's `defaultValue` here. MobX also wraps a resolved value in an
    // ObservableArray, which fails a `PropTypes.array` check, so copy it into a plain
    // array before passing it down — the same thing `nodes` does below.
    const reservationsList = ((res || {}).value || []).map(reservation => reservation);
    const {
      createNewPool,
      editablePool,
      poolToRemove,
      pending
    } = this.state;
    const reservationToRemove = poolToRemove
      ? reservationsList.find(r => r.nodePoolId === poolToRemove.id)
      : undefined;
    const {authenticatedUserInfo} = this.props;
    const {canCreate, canCreateAnyMode} = this.permissions;
    return (
      <div
        className="cp-panel cp-panel-transparent"
      >
        <div
          className={styles.header}
        >
          <span className={styles.title}>Hot Node Pools</span>
          <div
            className={styles.actions}
          >
            {canCreate && (
              <Button
                disabled={pending}
                type="primary"
                onClick={this.openEditPoolModal({isNew: true})}
              >
                <Icon type="plus" />
                Create
              </Button>
            )}
            <Button
              disabled={pending}
              onClick={this.refresh}
            >
              Refresh
            </Button>
          </div>
        </div>
        <div>
          {
            pools.map(pool => {
              const canEdit = canEditPool(pool, authenticatedUserInfo);
              return (
                <PoolCard
                  disabled={pending}
                  canEdit={canEdit}
                  canDelete={canDeletePool(pool, authenticatedUserInfo)}
                  canViewUsage={canViewPoolUsage(authenticatedUserInfo)}
                  key={pool.id}
                  pool={pool}
                  onEdit={this.openEditPoolModal({pool})}
                  onRemove={this.onRemovePool(pool)}
                  onClick={canEdit ? () => this.onPoolClick(pool) : undefined}
                  nodes={(nodes.value || []).map(node => node)}
                  reservations={reservationsList}
                  router={router}
                />
              );
            })
          }
        </div>
        <EditHotNodePool
          disabled={pending}
          visible={!!editablePool || createNewPool}
          pool={editablePool}
          currentUserName={this.currentUserName}
          allowedModes={canCreateAnyMode ? [MODE_DEFAULT, MODE_FUTURE_DATED] : [MODE_FUTURE_DATED]}
          reservations={reservationsList}
          onCancel={this.closeEditPoolModal}
          onSave={this.operationWrapper(this.onSaveEditablePool)}
        />
        <DeletePoolModal
          disabled={pending}
          visible={!!poolToRemove}
          pool={poolToRemove}
          reservation={reservationToRemove}
          onCancel={this.closeRemovePoolModal}
          onConfirm={this.operationWrapper(this.removePool)}
        />
      </div>
    );
  }
}

export default HotCluster;
