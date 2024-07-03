/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import GeneralPresentation from './general';
import DatePresentation from './date';
import Size from './size';
import Folder from './folder';
import KeyValue from './key-value';
import {SearchItemTypes} from '../../../../../models/search';

export default function DocumentListPresentation ({className, document, extraColumns}) {
  if (!document) {
    return null;
  }
  const {type} = document;
  const extra = [
    (<Folder key="folder" document={document} />),
    (<Size key="size" document={document} />),
    (
      <DatePresentation
        key="last modified"
        document={document}
        field="lastModified"
        label="changed"
      />
    )
  ];
  switch (type) {
    case SearchItemTypes.run:
      extra.push(
        (
          <DatePresentation
            key="started"
            document={document}
            label="started"
            field="startDate"
          />
        )
      );
      extra.push(
        (
          <DatePresentation
            key="finished"
            document={document}
            label="finished"
            field="endDate"
          />
        )
      );
      break;
    default:
      break;
  }
  return (
    <GeneralPresentation
      className={className}
      document={document}
      showDescription={document?.name !== document?.description}
      extra={extra}
    >
      {
        extraColumns && extraColumns.length > 0 && (extraColumns || [])
          .map(column => (
            <KeyValue
              key={column.key}
              field={column.key}
              name={column.name}
              document={document}
            />
          ))
      }
    </GeneralPresentation>
  );
}
