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
  message,
  Modal
} from 'antd';
import {computed} from 'mobx';
import LoadingView from '../../special/LoadingView';
import EditHotNodePool from './edit-hot-node-pool';
import clusterNodes from '../../../models/cluster/ClusterNodes';
import pools from '../../../models/cluster/HotNodePools';
import HotNodePoolUpdate from '../../../models/cluster/HotNodePoolUpdate';
import HotNodePoolDelete from '../../../models/cluster/HotNodePoolDelete';
import HotNodePoolScheduleUpdate from '../../../models/cluster/HotNodePoolScheduleUpdate';
import HotNodePoolScheduleDelete from '../../../models/cluster/HotNodePoolScheduleDelete';
import PoolCard from './pool-card';
import styles from './hot-node-pool.css';
import roleModel from '../../../utils/roleModel';
import {isAdmin} from '../utilities/access-permissinos';

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
@inject(() => {
  return {
    pools,
    clusterNodes
  };
})
@observer
class HotCluster extends React.Component {
  state = {
    createNewPool: false,
    editablePool: undefined,
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
  get isReadOnly () {
    const {authenticatedUserInfo} = this.props;
    return !authenticatedUserInfo.loaded || !isAdmin(authenticatedUserInfo.value);
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
    if (pool) {
      const {pools, clusterNodes: nodes} = this.props;
      const onConfirm = async () => {
        const hide = message.loading('Removing pool...', -1);
        const request = new HotNodePoolDelete(pool.id);
        await request.fetch();
        if (request.error) {
          hide();
          message.error(request.error, 5);
        } else if (pool.schedule && pool.schedule.id) {
          const scheduleRequest = new HotNodePoolScheduleDelete(pool.schedule.id);
          await scheduleRequest.fetch();
          if (scheduleRequest.error) {
            hide();
            message.error(request.error, 5);
          } else {
            await pools.fetch();
            await nodes.fetch();
            hide();
          }
        } else {
          await pools.fetch();
          await nodes.fetch();
          hide();
        }
      };
      Modal.confirm({
        title: `Are you sure you want to delete "${pool.name}" pool?`,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          return onConfirm();
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    }
  };

  closeEditPoolModal = () => {
    this.setState({
      createNewPool: false,
      editablePool: undefined
    });
  };

  onSaveEditablePool = async (pool, schedule) => {
    const {createNewPool, editablePool} = this.state;
    const {pools, clusterNodes: nodes} = this.props;
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
      await Promise.all([pools.fetch(), nodes.fetch()]);
    } catch (error) {
      message.error(error.message, 5);
    } finally {
      hide();
    }
    this.closeEditPoolModal();
  };

  refresh = async () => {
    const {pools: poolsRequest, clusterNodes: nodes} = this.props;
    await poolsRequest.fetch();
    await nodes.fetch();
  };

  onPoolClick = (pool) => {
    const {router} = this.props;
    router.push(`/cluster?pool_id=${pool.id}`);
  }

  render () {
    const {
      pools: poolsRequest,
      clusterNodes: nodes,
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
    const {
      createNewPool,
      editablePool,
      pending
    } = this.state;
    const readOnly = this.isReadOnly;
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
            {!readOnly && (
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
            pools.map(pool => (
              <PoolCard
                disabled={pending}
                readOnly={readOnly}
                key={pool.id}
                pool={pool}
                onEdit={this.openEditPoolModal({pool})}
                onRemove={this.onRemovePool(pool)}
                onClick={() => this.onPoolClick(pool)}
                nodes={(nodes.value || []).map(node => node)}
                router={router}
              />
            ))
          }
        </div>
        <EditHotNodePool
          disabled={pending}
          visible={!!editablePool || createNewPool}
          pool={editablePool}
          onCancel={this.closeEditPoolModal}
          onSave={this.operationWrapper(this.onSaveEditablePool)}
        />
      </div>
    );
  }
}

export default HotCluster;
