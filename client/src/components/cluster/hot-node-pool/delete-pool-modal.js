/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {Alert, Button, Checkbox, Modal} from 'antd';
import {
  getCapacityReservationStatus,
  humanizeReservationStatus,
  needsForceToDeletePool
} from './edit-hot-node-pool/helpers';
import styles from './delete-pool-modal.css';

// A capacity-reservation (future dated) pool whose reservation has not reached a
// safe-to-delete status - see `needsForceToDeletePool` - still holds, or may still hold,
// capacity with the cloud provider: deleting the pool gives that reservation up too, and
// the provider may still charge for it. The backend refuses that implicit cancellation
// unless the caller forces it, so this modal collects the same consent before it tries -
// for an admin exactly as much as for the pool's own `ROLE_NODE_POOL_MANAGER` owner;
// neither role waives the disclaimer. A `Default` pool, which is never a capacity
// reservation, never shows one.
class DeletePoolModal extends React.Component {
  state = {
    force: false
  };

  componentDidUpdate (prevProps) {
    const {pool, visible} = this.props;
    const reopened = visible && !prevProps.visible;
    const changedPool = visible && pool !== prevProps.pool;
    if (reopened || changedPool) {
      // eslint-disable-next-line react/no-did-update-set-state
      this.setState({force: false});
    }
  }

  onChangeForce = (e) => {
    this.setState({force: e.target.checked});
  };

  onConfirm = () => {
    const {onConfirm} = this.props;
    const {force} = this.state;
    if (onConfirm) {
      onConfirm(force);
    }
  };

  render () {
    const {
      disabled,
      onCancel,
      pool,
      reservation,
      visible
    } = this.props;
    const {force} = this.state;
    // `pool` is guarded rather than early-returning `null` for its absence: this modal
    // is mounted unconditionally (`visible` is what the parent toggles), and returning
    // `null` while `visible` is still `true` - which is exactly what happens the instant
    // a successful delete resets the parent's selected pool - would unmount the `Modal`
    // directly instead of letting it close first. antd's Dialog only removes its
    // body-level portal node synchronously when `visible` is already `false`; left to
    // unmount while still visible, it defers that removal to a CSS transition's
    // `afterClose`, which does not fire in every environment - a stray node left behind
    // for the next pool this same instance is asked to confirm.
    const mustForce = pool ? needsForceToDeletePool(pool, reservation) : false;
    const status = pool ? getCapacityReservationStatus(pool, reservation) : undefined;
    return (
      <Modal
        title="Delete pool"
        visible={visible}
        closable={!disabled}
        maskClosable={!disabled}
        onCancel={disabled ? undefined : onCancel}
        footer={(
          <div className={styles.modalFooter}>
            <Button
              disabled={disabled}
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disabled || (mustForce && !force)}
              loading={disabled}
              type="danger"
              onClick={this.onConfirm}
            >
              DELETE
            </Button>
          </div>
        )}
      >
        {
          pool && (
            <div>
              <p>
                {`Are you sure you want to delete "${pool.name}" pool?`}
              </p>
              {
                mustForce && (
                  <div className={styles.disclaimer}>
                    <Alert
                      type="warning"
                      showIcon
                      message={
                        `This pool's capacity reservation is still ` +
                        `${humanizeReservationStatus(status).toLowerCase()} and has not ` +
                        'been given up yet. Deleting the pool now cancels the ' +
                        'reservation too, and the cloud provider may still charge for ' +
                        'the capacity it already committed.'
                      }
                    />
                    <Checkbox
                      className={styles.forceCheckbox}
                      disabled={disabled}
                      checked={force}
                      onChange={this.onChangeForce}
                    >
                      Force deletion
                    </Checkbox>
                  </div>
                )
              }
            </div>
          )
        }
      </Modal>
    );
  }
}

DeletePoolModal.propTypes = {
  disabled: PropTypes.bool,
  onCancel: PropTypes.func,
  onConfirm: PropTypes.func,
  pool: PropTypes.object,
  reservation: PropTypes.object,
  visible: PropTypes.bool
};

export default DeletePoolModal;
