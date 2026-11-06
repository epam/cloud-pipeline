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
import {observable} from 'mobx';
import {
  renderWithStores,
  screen,
  click,
  waitFor,
  fn,
  stubApi,
  useFakeTimers,
  useRealTimers
} from '@test';
import DownloadsModal from './downloads-modal';

const WHOAMI_ROUTE = '/whoami';
const FILTER_ROUTE = '/async-task/filter';
const USER_NAME = 'current-user';
// Mirrors DownloadsManager's own POLL_INTERVAL_MS. Not imported: that
// constant is not part of the module's public surface.
const POLL_INTERVAL_MS = 5000;

// A plain observable object is a sufficient store double (rule 10).
// `pausePolling`/`resumePolling` are deliberately absent from the default
// double - DownloadsManager calls both through a `typeof` guard.
function asyncTasksDouble (overrides = {}) {
  return observable(Object.assign({
    cancelTask: fn().mockResolvedValue({}),
    consumeTask: fn().mockResolvedValue(undefined)
  }, overrides));
}

function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: USER_NAME, admin}
  });
}

function dataStoragesDouble (overrides = {}) {
  return observable(Object.assign({
    loaded: true,
    value: [{id: 7, name: 'my-storage'}]
  }, overrides));
}

function stores (overrides = {}) {
  return {
    asyncTasks: asyncTasksDouble(overrides),
    authenticatedUserInfo: authenticatedUserInfoDouble(),
    dataStorages: dataStoragesDouble()
  };
}

function stubFilter (api, tasks, total = (tasks || []).length) {
  api.reply(WHOAMI_ROUTE, {userName: USER_NAME});
  api.reply(FILTER_ROUTE, {elements: tasks, totalCount: total});
}

function filterCalls (api) {
  return api.calls.filter(c => c.path === FILTER_ROUTE);
}

// The Modal shell - the module's default export, wrapping the DownloadsManager
// class (downloads-manager.js). These cover the conditional mounting of its
// body: antd 2.13.14's Modal keeps its children mounted after `visible` turns
// false (no `destroyOnClose` in node_modules/antd/lib/modal/Modal.js or
// node_modules/rc-dialog, 6.5.11), which is why the shell renders
// `{visible ? <DownloadsManager /> : null}` instead of relying on the Modal
// to stop the class's poller and resume the shared tracker by itself.
describe('DownloadsModal', () => {
  // antd's Modal (rc-dialog) portals its content to a node it appends to
  // `document.body` itself, outside the container `render()` tracks, and
  // only detaches it synchronously on unmount when `visible` is already
  // `false` - otherwise it first re-renders the dialog closed and detaches
  // from that transition's `afterClose`, which never fires in jsdom (no
  // real CSS animations). See download-storage-archive-modal.test.jsx's own
  // copy of this comment. So every test that opens the modal closes it
  // again before the suite's own `afterEach(cleanup)` unmounts it, through
  // `renderWithStores` into the same container rather than a hand-rolled
  // `<Provider>` (rule 11) - or the next test would see a leftover
  // "Downloads" dialog still in the document.
  let openModal = null;

  afterEach(() => {
    if (openModal) {
      const {container, storeSet} = openModal;
      renderWithStores(
        <DownloadsModal visible={false} onClose={fn()} />,
        storeSet,
        {container}
      );
      openModal = null;
    }
  });

  function renderModal (props, storeSet = stores()) {
    const utils = renderWithStores(
      <DownloadsModal visible onClose={fn()} {...props} />,
      storeSet
    );
    openModal = {container: utils.container, storeSet};
    return utils;
  }

  test('visible={false} renders no table and issues no request', async () => {
    const api = stubApi();

    renderWithStores(
      <DownloadsModal visible={false} onClose={fn()} />,
      stores()
    );

    // Nothing to `waitFor` here - a request that never fires has nothing to
    // settle into. The absence is asserted immediately, on the same api
    // double a mounted body would have recorded a call on (see the next
    // test), which is what actually proves the poller never started rather
    // than merely that nothing rendered.
    expect(filterCalls(api).length).toBe(0);
  });

  test('visible renders the table and the title', async () => {
    const api = stubApi();
    stubFilter(api, []);

    renderModal();

    expect(screen.getByText('Downloads')).toBeInTheDocument();
    // The table only exists once `DownloadsManager` itself has mounted
    // inside the Modal's body.
    expect(await screen.findByRole('table')).toBeInTheDocument();
    await waitFor(() => expect(filterCalls(api).length).toBe(1));
  });

  test('the footer\'s Close calls onClose', async () => {
    const api = stubApi();
    stubFilter(api, []);
    const onClose = fn();

    renderModal({onClose});

    await waitFor(() => expect(filterCalls(api).length).toBe(1));
    // Not `getByRole('button', {name: /close/i})`: rc-dialog's own X button
    // carries `aria-label="Close"` too, so that query matches both and
    // throws for ambiguity. The footer's button is the only one with
    // "Close" as its actual text content.
    await click(screen.getByText('Close').closest('button'));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  // Re-uses the exact double the class's own "pauses the shared background
  // poll on mount, resumes it on unmount" test (downloads-manager.test.jsx)
  // proves pausePolling/resumePolling against - the same guarantee, now
  // driven by the Modal's `visible` prop turning false rather than by
  // unmounting the whole tree, since that is the path an actual Close/Cancel
  // click takes.
  test(
    'going from visible to not visible unmounts the body: the poller stops ' +
    'and the tracker resumes',
    async () => {
      const api = stubApi();
      stubFilter(api, []);
      const pausePolling = fn();
      const resumePolling = fn();
      const asyncTasks = asyncTasksDouble({pausePolling, resumePolling});
      const storeSet = {
        asyncTasks,
        authenticatedUserInfo: authenticatedUserInfoDouble(),
        dataStorages: dataStoragesDouble()
      };

      const utils = renderModal({}, storeSet);

      await waitFor(() => expect(filterCalls(api).length).toBe(1));
      expect(pausePolling).toHaveBeenCalledTimes(1);
      expect(resumePolling).not.toHaveBeenCalled();

      // Renders again into the SAME container, through `renderWithStores`
      // again rather than `utils.rerender` (rule 11 - no hand-rolled
      // `<Provider>`): both calls build the identical `<Provider>` root, so
      // React reconciles this as a plain update of the existing tree, not a
      // root-type swap - which is what actually exercises `visible` turning
      // false as a prop change, the path a real Close/Cancel click takes,
      // rather than unmounting the whole container the way
      // `utils.unmount()` below would.
      renderWithStores(
        <DownloadsModal visible={false} onClose={fn()} />,
        storeSet,
        {container: utils.container}
      );
      // Already closed - the suite-level `afterEach` above would otherwise
      // render the same `visible={false}` update a second time, harmlessly,
      // but marking it done here keeps the intent obvious.
      openModal = null;

      expect(resumePolling).toHaveBeenCalledTimes(1);
      expect(pausePolling).toHaveBeenCalledTimes(1);
    }
  );

  // The case the fix above exists for: the storage page unmounts the shell
  // itself (a navigation away, or its own early `LoadingView`/`Alert`
  // return) while the modal is still `visible`. Unlike the test above, rc-
  // dialog's own `DialogWrap.componentWillUnmount` keeps the body mounted in
  // its detached portal container (deferred to the leave animation's
  // `afterClose`, which never fires here) - so without `DownloadsModal`'s own
  // `componentWillUnmount` reaching the body through `bodyRef` and calling
  // its `shutdown()` directly, neither `resumePolling` nor the poller's own
  // stop would ever happen. `utils.unmount()` - unmounting the whole tree,
  // not a `visible` prop change - is what actually exercises that path.
  test(
    'unmounting the shell while still visible shuts the body down too: the poller ' +
    'stops and the tracker resumes',
    async () => {
      useFakeTimers();
      try {
        const api = stubApi();
        stubFilter(api, []);
        const pausePolling = fn();
        const resumePolling = fn();
        const asyncTasks = asyncTasksDouble({pausePolling, resumePolling});
        const storeSet = {
          asyncTasks,
          authenticatedUserInfo: authenticatedUserInfoDouble(),
          dataStorages: dataStoragesDouble()
        };

        const utils = renderModal({}, storeSet);

        await waitFor(() => expect(filterCalls(api).length).toBe(1));
        expect(pausePolling).toHaveBeenCalledTimes(1);
        expect(resumePolling).not.toHaveBeenCalled();

        utils.unmount();
        // Already gone - nothing left for the suite-level `afterEach` to
        // close.
        openModal = null;

        expect(resumePolling).toHaveBeenCalledTimes(1);
        expect(pausePolling).toHaveBeenCalledTimes(1);

        // Not merely "resumePolling fired": proves the poller itself
        // actually stopped, the same technique the class's own "keeps
        // refreshing on its own while mounted, and stops once unmounted"
        // test (downloads-manager.test.jsx) uses - advancing a full
        // interval past the first call and expecting the `waitFor` for a
        // second one to time out.
        await expect(waitFor(() => expect(
          filterCalls(api).length
        ).toBe(2), {timeout: POLL_INTERVAL_MS * 2})).rejects.toThrow();

        expect(filterCalls(api).length).toBe(1);
      } finally {
        useRealTimers();
      }
    }
  );
});
