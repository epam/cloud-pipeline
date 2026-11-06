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
import {Button, Modal} from 'antd';
import DownloadsManager from './downloads-manager';

// The Modal shell shown from the storage page's presentation dropdown
// ("Show downloads", pipelines/browser/data-storage/index.js). A class, not
// the plain function client/AGENTS.md otherwise calls for: it needs the one
// lifecycle method below.
//
// `{visible ? <DownloadsManager /> : null}` is load-bearing: antd 2.13.14's
// Modal has no `destroyOnClose` and rc-dialog 6.5.11 implements no
// equivalent, so a Modal's children stay mounted after it closes.
// `DownloadsManager`'s mount/unmount starts/stops its poller and
// pauses/resumes the shared task tracker, so mounting it only while
// `visible` ties its lifecycle to the modal actually being open; closing the
// Modal from the parent instead (never rendering this shell while closed)
// would cut the Modal's own close animation short.
//
// `width="80%"` is the house's convention for a wide modal (e.g.
// settings/system-management/system-jobs/system-job-log.js). The explicit
// `footer` is how this codebase gets a single-button footer out of antd
// 2.13.14 - download-storage-archive-modal.js documents the same pattern for
// its own Cancel/OK footer.
class DownloadsModal extends React.Component {
  // A callback ref, not `React.createRef()`: this app is React 15
  // (client/AGENTS.md), and `createRef` is a 16.3 addition.
  bodyRef = null;

  setBodyRef = (instance) => {
    this.bodyRef = instance;
  };

  // Covers the storage page unmounting this shell (a navigation away, or its
  // own early `LoadingView`/`Alert` return) while the modal is still open:
  // rc-dialog's `DialogWrap.componentWillUnmount` keeps the portal's content
  // rendered, deferring removal to a leave-animation callback that never
  // fires once this shell is gone - so this unmount is the only synchronous
  // signal there is. `bodyRef` reaches the body despite the portal: the ref
  // is set during this shell's own render, regardless of which DOM container
  // rc-dialog later moves the element into.
  componentWillUnmount () {
    const body = this.bodyRef;
    if (body && body.wrappedInstance) {
      body.wrappedInstance.shutdown();
    }
  }

  render () {
    const {visible, onClose} = this.props;
    return (
      <Modal
        visible={visible}
        title="Downloads"
        width="80%"
        onCancel={onClose}
        footer={[
          <Button key="close" size="large" onClick={onClose}>Close</Button>
        ]}
      >
        {visible ? <DownloadsManager ref={this.setBodyRef} /> : null}
      </Modal>
    );
  }
}

DownloadsModal.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func
};

DownloadsModal.defaultProps = {
  visible: false
};

export default DownloadsModal;
