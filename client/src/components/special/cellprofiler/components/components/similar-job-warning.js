/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Modal} from 'antd';
import CellProfilerJob from './cell-profiler-job';
import styles from './similar-job-warning.css';

function SimilarJobWarning (
  {
    className,
    visible,
    onCancel,
    onSubmit,
    onOpenSimilar,
    jobs = []
  }
) {
  return (
    <Modal
      className={className}
      visible={visible}
      onCancel={onCancel}
      footer={(
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end'
          }}
        >
          <Button
            style={{marginRight: 5}}
            onClick={onCancel}
          >
            NO
          </Button>
          <Button
            type="primary"
            onClick={onSubmit}
          >
            YES
          </Button>
        </div>
      )}
    >
      <div
        style={{
          fontWeight: 'bold'
        }}
      >
        {/* eslint-disable-next-line max-len */}
        It is possible that the same or similar analysis job{jobs.length > 1 ? 's' : ''} {jobs.length > 1 ? 'were' : 'was'} launched recently:
      </div>
      {
        jobs.map(similarJob => (
          <CellProfilerJob
            key={`similar-job-${similarJob.job.id}`}
            className={styles.job}
            job={similarJob.job}
            onClick={() => onOpenSimilar(similarJob.job)}
          />
        ))
      }
      <div style={{
        fontWeight: 'bold'
      }}>
        Are you sure you want to launch a new one?
      </div>
    </Modal>
  );
}

SimilarJobWarning.propTypes = {
  className: PropTypes.string,
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onSubmit: PropTypes.func,
  onOpenSimilar: PropTypes.func,
  jobs: PropTypes.oneOfType([
    PropTypes.object,
    PropTypes.array
  ])
};

export default SimilarJobWarning;
