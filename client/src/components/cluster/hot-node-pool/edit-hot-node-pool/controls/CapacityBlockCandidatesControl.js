/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Alert, Button, Icon, InputNumber, Select} from 'antd';
import classNames from 'classnames';
import CapacityBlockOfferingCard from './CapacityBlockOfferingCard';
import {getCompleteMlBlockCandidates, formatCapacityBlockInstanceType} from '../helpers';
import {ML_BLOCK_OFFERINGS_ERROR_MESSAGE} from '../constants';
import styles from '../../edit-hot-node-pool.css';

// A candidate row is "complete" exactly when `getCompleteMlBlockCandidates` would keep
// it. Each row renders from its own `offeringsByCandidate[row.id]` entry only, so one
// row's own pending/error search never hides another, already-settled row's cards.
//
// The endpoint can return more than one offering per candidate (every AWS match, not just
// the nearest); each is rendered as its own card, which is the only click target.
function CapacityBlockCandidatesControl ({
  candidates,
  instanceTypes,
  instanceTypesPending,
  instanceTypesError,
  offeringsByCandidate,
  fullRefetchPending,
  selectedOfferingId,
  disabled,
  readOnly,
  error,
  touched,
  onAdd,
  onRemove,
  onChange,
  onSelectOffering
}) {
  const rows = candidates || [];
  const completeIds = getCompleteMlBlockCandidates(rows).map(row => row.id);
  const addDisabled = disabled || instanceTypesPending || Boolean(instanceTypesError);

  const renderRowOfferings = (row) => {
    if (!completeIds.includes(row.id) || fullRefetchPending) {
      return null;
    }
    const entry = offeringsByCandidate[row.id] || {};
    if (entry.pending) {
      // The spinner alone, not a stale card: a visible card is always clickable, and
      // one found for criteria the user has since changed must not be. The wrapper's
      // own min-height matches the alert's, so the row does not shrink meanwhile.
      return (
        <span className={styles.capacityBlockOfferingPending}>
          <Icon type="loading" aria-label="Loading" />
        </span>
      );
    }
    if (entry.error !== undefined && entry.error !== null) {
      // Same geometry as the other three states (min-height, reserved icon space, the
      // re-centred antd icon) so a failed search reads as the same kind of object.
      return (
        <Alert
          type="error"
          showIcon
          message={
            <span className={styles.capacityBlockOfferingCardMessage}>
              {entry.error || ML_BLOCK_OFFERINGS_ERROR_MESSAGE}
            </span>
          }
          className={styles.capacityBlockOfferingCard}
        />
      );
    }
    const rowOfferings = entry.offerings || [];
    if (rowOfferings.length === 0) {
      // A settled empty search is a `warning`, carrying the same card padding and height
      // as a found offering so all three right-hand states read as one kind of object.
      return entry.searched ? (
        <Alert
          type="warning"
          showIcon
          message={<span className={styles.capacityBlockOfferingCardMessage}>No offerings</span>}
          className={styles.capacityBlockOfferingCard}
        />
      ) : null;
    }
    return rowOfferings.map((offering) => (
      <CapacityBlockOfferingCard
        key={offering.offeringId}
        offering={offering}
        selected={offering.offeringId === selectedOfferingId}
        disabled={disabled || readOnly}
        onSelect={onSelectOffering}
      />
    ));
  };

  const renderRow = (row) => {
    // Another row's own type is unavailable here, so a flat offering response can be
    // attributed back to a row by `instanceType` alone.
    const usedTypes = rows
      .filter(candidate => candidate.id !== row.id)
      .map(candidate => (candidate.instanceType || '').toLowerCase())
      .filter(Boolean);
    // Sorted by name - AWS does not return the catalogue in a stable order, and an
    // unsorted list would land the same click on a different instance type between opens.
    const availableTypes = (instanceTypes || [])
      .filter(info => !usedTypes.includes((info.instanceType || '').toLowerCase()))
      .slice()
      .sort((a, b) => (a.instanceType || '').localeCompare(b.instanceType || ''));
    return (
      <div key={row.id} className={styles.capacityBlockCandidateRow}>
        <div className={styles.capacityBlockCandidateInputs}>
          <Select
            showSearch
            disabled={disabled || readOnly}
            className={styles.capacityBlockCandidateType}
            placeholder="Instance type"
            value={row.instanceType || undefined}
            onChange={(value) => onChange(row.id, 'instanceType', value)}
          >
            {
              availableTypes.map((info) => (
                <Select.Option key={info.instanceType} value={info.instanceType}>
                  {formatCapacityBlockInstanceType(info)}
                </Select.Option>
              ))
            }
          </Select>
          <InputNumber
            disabled={disabled || readOnly}
            min={1}
            className={styles.capacityBlockCandidateCount}
            placeholder="Nodes count"
            value={row.instanceCount}
            onChange={(value) => onChange(row.id, 'instanceCount', value)}
          />
          {
            !readOnly && (
              <Button
                size="small"
                type="danger"
                disabled={disabled}
                onClick={() => onRemove(row.id)}
              >
                <Icon type="delete" />
              </Button>
            )
          }
        </div>
        <div className={styles.capacityBlockCandidateOfferings}>
          {renderRowOfferings(row)}
        </div>
      </div>
    );
  };

  return (
    <div>
      <div className={classNames(styles.formRow, styles.capacityBlockInstanceTypesRow)}>
        <span className={styles.label}>
          Instance types:
        </span>
        {
          // No candidate can be complete before the catalogue itself has arrived, so the
          // two searches cannot meaningfully overlap - instanceTypesPending wins if they
          // somehow do.
          instanceTypesPending ? (
            <span className={styles.capacityBlockPendingIndicator}>
              Fetching instance types...
              <Icon
                type="loading"
                aria-label="Loading"
                className={styles.capacityBlockPendingIndicatorIcon}
              />
            </span>
          ) : fullRefetchPending && (
            <span className={styles.capacityBlockPendingIndicator}>
              Search for offerings...
              <Icon
                type="loading"
                aria-label="Loading"
                className={styles.capacityBlockPendingIndicatorIcon}
              />
            </span>
          )
        }
      </div>
      {
        instanceTypesError && (
          <div className={styles.formRow}>
            <Alert type="error" message={instanceTypesError} />
          </div>
        )
      }
      {rows.map(renderRow)}
      {
        !readOnly && (
          <div className={styles.formRow}>
            <Button
              disabled={addDisabled}
              onClick={onAdd}
              type="dashed"
            >
              <Icon type="plus" />
              Add instance type
            </Button>
          </div>
        )
      }
      {
        error && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {error}
          </div>
        )
      }
    </div>
  );
}

CapacityBlockCandidatesControl.propTypes = {
  candidates: PropTypes.array,
  // MobX wraps a Remote's resolved value in an ObservableArray, which fails a strict
  // PropTypes.array (Array.isArray) check despite behaving as one.
  instanceTypes: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  instanceTypesPending: PropTypes.bool,
  instanceTypesError: PropTypes.string,
  offeringsByCandidate: PropTypes.object,
  fullRefetchPending: PropTypes.bool,
  selectedOfferingId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  disabled: PropTypes.bool,
  readOnly: PropTypes.bool,
  error: PropTypes.string,
  touched: PropTypes.bool,
  onAdd: PropTypes.func,
  onRemove: PropTypes.func,
  onChange: PropTypes.func,
  onSelectOffering: PropTypes.func
};

CapacityBlockCandidatesControl.defaultProps = {
  candidates: [],
  instanceTypes: [],
  offeringsByCandidate: {},
  onAdd: () => {},
  onRemove: () => {},
  onChange: () => {},
  onSelectOffering: () => {}
};

export default CapacityBlockCandidatesControl;
