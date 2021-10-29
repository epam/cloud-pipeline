/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import S3FilePreview from './S3FilePreview';
// import DefaultPreview from './DefaultPreview';

export default function preview (props) {
  if (!props.item) {
    return null;
  }
  const Content = S3FilePreview;

  if (!Content) {
    return null;
  }
  return (
    <Content
      item={props.item}
      lightMode={props.lightMode}
      onPreviewLoaded={props.onPreviewLoaded}
      fullscreen={props.fullscreen}
      onFullScreenChange={props.onFullScreenChange}
      fullScreenAvailable={props.fullScreenAvailable}
      storageId={props.storageId}
    />
  );
}
