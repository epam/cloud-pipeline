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
import {
  getCompleteMlBlockCandidates,
  getOfferingsForCandidate,
  formatCapacityBlockInstanceType
} from '../helpers';
import styles from '../../edit-hot-node-pool.css';

// A candidate row is "complete" exactly when `getCompleteMlBlockCandidates` would keep
// it - the same rule the search itself uses. "No offerings" is gated on `offeringsSearched`
// rather than on `offerings` being non-empty: a settled search that matched nothing and a
// search that has not run yet both leave the list empty, and only the flag tells them apart.
function CapacityBlockCandidatesControl ({
  candidates,
  instanceTypes,
  instanceTypesPending,
  instanceTypesError,
  offerings,
  offeringsPending,
  offeringsError,
  offeringsSearched,
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

  // The search covers every complete row at once, so its pending/error state is shown
  // once for the whole control rather than repeated in every row's own right half. Cards
  // are plain (not `interactive`): the row below is the selectable unit now.
  const renderRowOfferings = (row, rowOfferings) => {
    if (!completeIds.includes(row.id) || offeringsPending || offeringsError) {
      return null;
    }
    if (rowOfferings.length === 0) {
      return offeringsSearched ? <span>No offerings</span> : null;
    }
    return rowOfferings.map((offering) => (
      <CapacityBlockOfferingCard key={offering.offeringId} offering={offering} />
    ));
  };

  const renderRow = (row) => {
    // Another row's own type is unavailable here, so a flat offering response can be
    // attributed back to a row by `instanceType` alone.
    const usedTypes = rows
      .filter(candidate => candidate.id !== row.id)
      .map(candidate => (candidate.instanceType || '').toLowerCase())
      .filter(Boolean);
    const availableTypes = (instanceTypes || [])
      .filter(info => !usedTypes.includes((info.instanceType || '').toLowerCase()));
    const rowOfferings = getOfferingsForCandidate(offerings, row);
    // The endpoint returns at most one offering per candidate; a row that somehow gets
    // more still renders every one of them, but selecting the row picks the first.
    const hasOffering = rowOfferings.length > 0;
    const canSelectRow = hasOffering && !disabled && !readOnly;
    const rowSelected = hasOffering &&
      rowOfferings.some(offering => offering.offeringId === selectedOfferingId);
    const selectRow = () => {
      if (canSelectRow) {
        onSelectOffering(rowOfferings[0].offeringId);
      }
    };
    const onRowKeyDown = (event) => {
      if (canSelectRow && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault();
        selectRow();
      }
    };
    // The row owns the click; the inputs swallow their own so a click or key press
    // there never also selects the row.
    const stopBubble = (event) => event.stopPropagation();
    return (
      <div
        key={row.id}
        className={classNames(
          styles.capacityBlockCandidateRow,
          {
            'cp-table-element': hasOffering,
            'cp-table-element-selected': rowSelected,
            'cp-outline-bordered': rowSelected
          }
        )}
        role={canSelectRow ? 'button' : undefined}
        tabIndex={canSelectRow ? 0 : undefined}
        onClick={canSelectRow ? selectRow : undefined}
        onKeyDown={canSelectRow ? onRowKeyDown : undefined}
      >
        <div
          className={styles.capacityBlockCandidateInputs}
          onClick={stopBubble}
          onKeyDown={stopBubble}
        >
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
          {renderRowOfferings(row, rowOfferings)}
        </div>
      </div>
    );
  };

  return (
    <div>
      <div className={styles.formRow}>
        <span className={styles.label}>
          Instance types:
        </span>
      </div>
      {
        instanceTypesError && (
          <div className={styles.formRow}>
            <Alert type="error" message={instanceTypesError} />
          </div>
        )
      }
      {
        offeringsPending && (
          <div className={styles.formRow}>
            <span>Searching for offerings...</span>
          </div>
        )
      }
      {
        offeringsError && (
          <div className={styles.formRow}>
            <Alert type="error" message={offeringsError} />
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
  offerings: PropTypes.array,
  offeringsPending: PropTypes.bool,
  offeringsError: PropTypes.string,
  offeringsSearched: PropTypes.bool,
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
  offerings: [],
  onAdd: () => {},
  onRemove: () => {},
  onChange: () => {},
  onSelectOffering: () => {}
};

export default CapacityBlockCandidatesControl;
