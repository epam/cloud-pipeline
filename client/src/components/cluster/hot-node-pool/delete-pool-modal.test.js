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
import {render, screen, click, fn} from '@test';
import DeletePoolModal from './delete-pool-modal';

const defaultPool = {id: 1, name: 'default-pool', isCapacityReservation: false};
const reservationPool = {id: 2, name: 'future-dated-pool', isCapacityReservation: true};

let closeCurrentModal;

function renderModal (props = {}) {
  const merged = {
    visible: true,
    pool: defaultPool,
    onCancel: fn(),
    onConfirm: fn(),
    ...props
  };
  const result = render(<DeletePoolModal {...merged} />);
  // Keeping `pool` (and every other prop) when flipping `visible` to `false` matters:
  // dropping `pool` makes the component return `null` outright, which unmounts the
  // `Modal` directly rather than telling it to close - see the comment below.
  closeCurrentModal = () => result.rerender(
    <DeletePoolModal {...merged} visible={false} />
  );
  return result;
}

function deleteButton () {
  return screen.getByText('DELETE').closest('button');
}

// antd's Dialog only removes its body-level portal node synchronously when `visible`
// is already `false` by the time the tree unmounts - left open, it defers removal to a
// CSS transition's `afterClose` that jsdom never fires, leaking one test's modal into
// the next (see the identical note in `cluster/hot-node-pool/index.test.js`). Closing it
// here, rather than in every test, is what `renderModal` is for.
afterEach(() => {
  if (closeCurrentModal) {
    closeCurrentModal();
    closeCurrentModal = undefined;
  }
});

describe('DeletePoolModal', () => {
  // `pool` is never absent while the parent shows this modal, but the component itself
  // must still tolerate it: see the comment on `render` for why it does not early-return
  // `null` instead.
  it('shows no confirmation text without a pool, and does not crash', () => {
    renderModal({pool: undefined});
    expect(screen.queryByText(/Are you sure/)).toBeNull();
    expect(screen.queryByText('Force deletion')).toBeNull();
  });

  it('asks to confirm, with no disclaimer, for a Default pool', () => {
    renderModal();
    expect(screen.getByText('Are you sure you want to delete "default-pool" pool?'))
      .toBeInTheDocument();
    expect(screen.queryByText('Force deletion')).toBeNull();
    expect(deleteButton()).not.toBeDisabled();
  });

  it('deletes a Default pool with no force', async () => {
    const onConfirm = fn();
    renderModal({onConfirm});
    await click(deleteButton());
    expect(onConfirm).toHaveBeenCalledWith(false);
  });

  it('shows no disclaimer for a reservation pool with no matching record', () => {
    renderModal({pool: reservationPool, reservation: undefined});
    expect(screen.queryByText('Force deletion')).toBeNull();
    expect(deleteButton()).not.toBeDisabled();
  });

  it('shows no disclaimer once the reservation is safe to delete', () => {
    renderModal({pool: reservationPool, reservation: {status: 'REQUIRED_APPROVE'}});
    expect(screen.queryByText('Force deletion')).toBeNull();
    expect(deleteButton()).not.toBeDisabled();
  });

  it('disables DELETE behind a disclaimer while the reservation is still open', () => {
    renderModal({pool: reservationPool, reservation: {status: 'ACTIVE'}});
    expect(screen.getByText(/active and has not been given up yet/))
      .toBeInTheDocument();
    expect(screen.getByText('Force deletion')).toBeInTheDocument();
    expect(deleteButton()).toBeDisabled();
  });

  it('enables DELETE once "Force deletion" is checked, and sends force=true', async () => {
    const onConfirm = fn();
    renderModal({pool: reservationPool, reservation: {status: 'ACTIVE'}, onConfirm});
    await click(screen.getByRole('checkbox'));
    expect(deleteButton()).not.toBeDisabled();
    await click(deleteButton());
    expect(onConfirm).toHaveBeenCalledWith(true);
  });

  it('disables every control while a delete request is in flight', () => {
    renderModal({disabled: true});
    expect(deleteButton()).toBeDisabled();
    expect(screen.getByText('CANCEL').closest('button')).toBeDisabled();
  });

  it('resets "Force deletion" when reopened for a different pool', async () => {
    const {rerender} = renderModal({
      pool: reservationPool,
      reservation: {status: 'ACTIVE'}
    });
    await click(screen.getByRole('checkbox'));
    expect(deleteButton()).not.toBeDisabled();

    const anotherReservationPool = {
      id: 3,
      name: 'another-pool',
      isCapacityReservation: true
    };
    rerender(
      <DeletePoolModal
        visible
        pool={anotherReservationPool}
        reservation={{status: 'ACTIVE'}}
        onCancel={fn()}
        onConfirm={fn()}
      />
    );
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(deleteButton()).toBeDisabled();
  });
});
