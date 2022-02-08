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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  Collapse
} from 'antd';
import {parseSampleSheet} from './utilities';
import SectionData from './section-data';
import SamplesTable from './samples-table';
import styles from './sample-sheet.css';

function SampleSheetPreview (
  {
    className,
    content,
    expandDataSection,
    size = 'default',
    style
  }
) {
  const parsed = parseSampleSheet(content);
  if (!parsed) {
    return null;
  }
  const {
    header,
    sections,
    dataSectionName = 'Data',
    titles = [],
    samples = []
  } = parsed;
  return (
    <div
      className={
        classNames(
          className
        )
      }
      style={style}
    >
      <Collapse
        className={
          classNames(
            {
              'cp-collapse-small': /^small$/i.test(size)
            }
          )
        }
        bordered={false}
        defaultActiveKey={
          [
            header ? header.name : undefined,
            expandDataSection && dataSectionName ? dataSectionName : undefined
          ].filter(Boolean)
        }
      >
        {
          header &&
          <Collapse.Panel key={header.name} header={header.name}>
            <SectionData>
              {
                (header.data || []).map((dataRow, index) => (
                  <SectionData.Row
                    key={`${header.name}-data-row-${index}`}
                    data={dataRow}
                  />
                ))
              }
            </SectionData>
          </Collapse.Panel>
        }
        {
          sections.map(section => (
            <Collapse.Panel key={section.name} header={section.name}>
              <SectionData>
                {
                  (section.data || []).map((dataRow, index) => (
                    <SectionData.Row
                      key={`${section.name}-data-row-${index}`}
                      data={dataRow}
                    />
                  ))
                }
              </SectionData>
            </Collapse.Panel>
          ))
        }
        {
          samples.length > 0 && (
            <Collapse.Panel
              key={dataSectionName}
              header={`${dataSectionName} (${samples.length})`}
              className="cp-collapse-body-no-padding"
            >
              <SamplesTable>
                <SamplesTable.Header>
                  {titles}
                </SamplesTable.Header>
                {
                  samples.map((sample, index) => (
                    <SamplesTable.Sample key={`sample-${index}`}>
                      {sample}
                    </SamplesTable.Sample>
                  ))
                }
              </SamplesTable>
            </Collapse.Panel>
          )
        }
      </Collapse>
      {
        samples.length === 0 && (
          <div
            className={
              classNames(
                styles.noSamplesWarning,
                'cp-text-not-important'
              )
            }
          >
            <i>No samples</i>
          </div>
        )
      }
    </div>
  );
}

SampleSheetPreview.propTypes = {
  className: PropTypes.string,
  content: PropTypes.string,
  style: PropTypes.object,
  expandDataSection: PropTypes.bool,
  size: PropTypes.oneOf(['small', 'default'])
};

export default SampleSheetPreview;
