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
import {Icon, Popover} from 'antd';

const RUN_LOADING_PLACEHOLDER_PROPERTY = '___loading_placeholder___';
const RUN_LOADING_ERROR_PROPERTY = '___loading_error___';

function RunLoadingPlaceholder (
  {
    run,
    children,
    empty
  }
) {
  if (!run) {
    return children || null;
  }
  const {
    [RUN_LOADING_PLACEHOLDER_PROPERTY]: placeholder = false,
    [RUN_LOADING_ERROR_PROPERTY]: error
  } = run;
  const renderReplacement = (replacement) => {
    if (empty) {
      return null;
    }
    return replacement;
  };
  if (placeholder) {
    return renderReplacement((
      <Icon type="loading" />
    ));
  }
  if (error) {
    return renderReplacement((
      <Popover
        content={error}
      >
        <Icon type="exclamation-circle-o" className="cp-danger" />
      </Popover>
    ));
  }
  return children || null;
}

RunLoadingPlaceholder.propTypes = {
  run: PropTypes.object,
  children: PropTypes.node,
  empty: PropTypes.bool
};

export {
  RUN_LOADING_PLACEHOLDER_PROPERTY,
  RUN_LOADING_ERROR_PROPERTY
};

export default RunLoadingPlaceholder;
