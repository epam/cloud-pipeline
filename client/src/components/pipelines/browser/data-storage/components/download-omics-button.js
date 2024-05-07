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
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {Icon, Button, message} from 'antd';
import OmicsStorage from '../../../../../models/omics-download/omics-download';

const ServiceTypes = {
  omicsRef: 'AWS_OMICS_REF',
  omicsSeq: 'AWS_OMICS_SEQ'
};

const FILE = 'File';

const FILE_TYPE = {
  BAM: 'BAM',
  UBAM: 'UBAM',
  CRAM: 'CRAM',
  FASTQ: 'FASTQ',
  REFERENCE: 'REFERENCE'
};

const FILE_SOURCE = {
  SOURCE1: 'source1',
  SOURCE2: 'source2',
  INDEX: 'index',
  SOURCE: 'source'
};

const TYPE_EXTENSION = {
  [FILE_TYPE.BAM]: {
    [FILE_SOURCE.SOURCE1]: '.bam',
    [FILE_SOURCE.INDEX]: '.bam.bai'
  },
  [FILE_TYPE.UBAM]: {
    [FILE_SOURCE.SOURCE1]: '.bam',
    [FILE_SOURCE.INDEX]: '.bam.bai'
  },
  [FILE_TYPE.CRAM]: {
    [FILE_SOURCE.SOURCE1]: '.cram',
    [FILE_SOURCE.INDEX]: '.cram.crai'
  },
  [FILE_TYPE.FASTQ]: {
    [FILE_SOURCE.SOURCE1]: '_1.fastq.gz',
    [FILE_SOURCE.SOURCE2]: '_2.fastq.gz'
  },
  [FILE_TYPE.REFERENCE]: {
    [FILE_SOURCE.SOURCE]: '.fasta',
    [FILE_SOURCE.INDEX]: '.fasta.fai'
  }
};

@inject('preferences')
@observer
export default class DownloadOmicsButton extends React.Component {

  static propTypes = {
    className: PropTypes.string,
    style: PropTypes.object,
    storageInfo: PropTypes.object,
    region: PropTypes.string,
    itemPath: PropTypes.string,
    itemType: PropTypes.string
  };

  state = {
    pending: false
  };

  get readSetId () {
    if (this.props.itemType === FILE) {
      return this.props.itemPath.split('/')[0];
    }
    return this.props.itemPath;
  }

  get sourceName () {
    if (this.props.itemType === FILE) {
      return this.props.itemPath.split('/')[1];
    }
    return undefined;
  }

  get storageType () {
    return (this.props.storageInfo || {}).type;
  }

  get storeId () {
    if (this.storageType === ServiceTypes.omicsSeq) {
      return this.props.storageInfo.cloudStorageId;
    } else {
      const path = this.props.storageInfo.path;
      const storeId = path.split('/')[1];
      return storeId;
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {downloadError} = this.omicsStorage || {};
    if (downloadError) {
      message.error(downloadError, 5);
      this.omicsStorage.downloadError = null;
    }
  }

  getFileName = (file) => {
    return `${file.name}${TYPE_EXTENSION[file.type][file.fileSource]}`;
  }

  handleClick = (event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    this.setState({
      pending: true
    }, async () => {
      this.createDownload();
    });
  };

  createDownload = async () => {
    const clientCreated = await this.createOmicClient();
    if (clientCreated) {
      const files = await this.omicsStorage.downloadFiles(this.storageType, this.sourceName);
      for (const file of files) {
        const url = URL.createObjectURL(file.blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = this.getFileName(file);
        document.body.appendChild(link);
        link.click();
        setTimeout(() => {
          URL.revokeObjectURL(url);
          document.body.removeChild(link);
        }, 0);
      }
      this.setState({
        pending: false
      });
    }
  }

  createOmicClient = async () => {
    const config = {
      region: this.props.region,
      storage: this.props.storageInfo,
      readSetId: this.readSetId,
      storeId: this.storeId
    };
    try {
      this.omicsStorage = new OmicsStorage(config);
      return await this.omicsStorage.createClient();
    } catch (err) {
      return false;
    }
  }

  render () {
    const {className, style} = this.props;
    const {pending} = this.state;
    return (
      <Button
        id="download omics files"
        key="download"
        size="small"
        style={style}
        className={classNames('cp-button', className)}
        onClick={(e) => this.handleClick(e)}>
        <Icon type={pending ? 'loading' : 'download'} />
      </Button>
    );
  }
}
