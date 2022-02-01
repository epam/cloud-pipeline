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
  Alert,
  Button,
  Icon,
  Modal
} from 'antd';
import EditSection from './section';
import SamplesEditor from './samples';
import {buildSampleSheet, isSampleSheetContent, parseSampleSheet} from '../utilities';
import styles from './sample-sheet-edit-form.css';

class SampleSheetEditForm extends React.Component {
  state = {
    error: undefined,
    header: undefined,
    sections: [],
    data: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.content !== this.props.content ||
      prevProps.editable !== this.props.editable
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {content} = this.props;
    if (content && isSampleSheetContent(content)) {
      const {
        header = {name: 'Header', data: []},
        sections = [],
        titles = [],
        samples = [],
        dataSectionName
      } = parseSampleSheet(content);
      const data = [
        titles,
        ...samples
      ];
      this.setState({
        error: undefined,
        header,
        sections: sections.map((o, id) => ({...o, identifier: id, deleted: false})),
        data: {
          name: dataSectionName,
          data
        }
      });
    } else if (content) {
      this.setState({
        error: 'Not a valid SampleSheet.',
        header: undefined,
        sections: [],
        data: undefined
      });
    } else {
      this.setState({
        error: undefined,
        header: undefined,
        sections: [],
        data: undefined
      });
    }
  };

  onChange = () => {
    const {
      header,
      sections = [],
      data
    } = this.state;
    const {onChange} = this.props;
    if (onChange) {
      onChange(buildSampleSheet({
        header,
        sections: sections.filter(o => !o.deleted),
        data
      }));
    }
  };

  onChangeHeader = (header) => {
    this.setState({
      header
    }, this.onChange);
  };

  onChangeData = (newData) => {
    const {data} = this.state;
    this.setState({
      data: {
        ...data,
        data: newData.slice()
      }
    }, this.onChange);
  };

  onChangeSection = (identifier) => (sectionData) => {
    const {sections = []} = this.state;
    const index = sections.findIndex(o => o.identifier === identifier);
    if (index >= 0) {
      const newSections = [...sections];
      newSections.splice(index, 1, {identifier, ...sectionData});
      this.setState({
        sections: newSections
      }, this.onChange);
    }
  };

  onRemoveSection = (identifier) => () => {
    const {sections = []} = this.state;
    const index = sections.findIndex(o => o.identifier === identifier);
    if (index >= 0) {
      const section = sections[index];
      const onConfirm = () => {
        const newSections = [...sections];
        newSections.splice(index, 1, {identifier, deleted: true});
        this.setState({
          sections: newSections
        }, this.onChange);
      };
      if (section.name || (section.data && section.data.length > 0)) {
        Modal.confirm({
          title: section && section.name
            ? `Are you sure you want to remove section "${section.name}"?`
            : 'Are you sure you want to remove section?',
          style: {
            wordWrap: 'break-word'
          },
          onOk: () => onConfirm(),
          okText: 'REMOVE',
          cancelText: 'CANCEL'
        });
      } else {
        onConfirm();
      }
    }
  };

  onAddSection = () => {
    const {sections = []} = this.state;
    this.setState({
      sections: [...sections, {name: '', data: [], identifier: sections.length}]
    });
  };

  render () {
    const {
      className,
      style,
      editable
    } = this.props;
    const {
      header,
      sections = [],
      data,
      error
    } = this.state;
    const {
      name: dataSectionName = 'Data',
      data: samplesData = []
    } = data || {};
    if (error) {
      return (
        <div
          className={
            classNames(
              className,
              styles.container
            )
          }
          style={style}
        >
          <Alert type="error" message={error} />
        </div>
      );
    }
    return (
      <div
        className={className}
        style={style}
      >
        <EditSection
          onChange={this.onChangeHeader}
          title={header ? header.name : 'Header'}
          data={header ? header.data : []}
          titleReadOnly
          editable={editable}
        />
        {
          sections
            .filter(section => !section.deleted)
            .map(section => (
              <EditSection
                key={`section-${section.identifier}`}
                onChange={this.onChangeSection(section.identifier)}
                onRemove={this.onRemoveSection(section.identifier)}
                title={section.name}
                data={section.data}
                removable
                editable={editable}
                otherSectionNames={[
                  header ? header.name : 'Header',
                  dataSectionName,
                  ...sections
                    .filter(s => !s.deleted && s.identifier !== section.identifier)
                    .map(o => o.name)
                ]}
              />
            ))
        }
        {
          editable && (
            <div className={styles.actionsRow}>
              <Button
                className={styles.addSection}
                onClick={this.onAddSection}
              >
                <Icon type="plus" /> Add section
              </Button>
            </div>
          )
        }
        <SamplesEditor
          data={samplesData}
          onChange={this.onChangeData}
          editable={editable}
        />
      </div>
    );
  }
}

SampleSheetEditForm.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  onChange: PropTypes.func,
  content: PropTypes.string,
  editable: PropTypes.bool
};

export default SampleSheetEditForm;
