import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {DownloadOutlined, ExportOutlined} from '@ant-design/icons';
import {FileExternalPreview} from '../../../../../../special/file-preview';
import DataStorageFileDownloadButton from '../../../../../../special/data-storage-file-download-button';
import styles from './path-cell.module.css';

export const ReportFilePreviewHeader = ({className, style, filePath, rule}) => {
  const infos = [];
  if (rule && rule.name) {
    infos.push(
      <tr key="rule-name">
        <th className={styles.keyCell}>Name:</th>
        <td className={styles.valueCell}>{rule.name}</td>
      </tr>,
    );
  }
  infos.push(
    <tr key="file-path">
      <th className={styles.keyCell}>Path:</th>
      <td className={styles.valueCell}>{filePath}</td>
    </tr>,
  );
  return (
    <div className={classNames(className, styles.reportFilePreviewHeader)} style={style}>
      <table className={styles.reportFilePreviewHeaderTable}>
        <tbody>{infos}</tbody>
      </table>
      <div className={styles.reportFilePreviewHeaderActions}>
        <FileExternalPreview
          filePath={filePath}
          mode="button"
          className={styles.reportFilePreviewHeaderAction}
        >
          <span style={{display: 'inline-flex', alignItems: 'center'}}>
            <ExportOutlined style={{marginRight: 5}} />
            <span>Open in a new tab</span>
          </span>
        </FileExternalPreview>
        <DataStorageFileDownloadButton
          filePath={filePath}
          mode="button"
          className={styles.reportFilePreviewHeaderAction}
        >
          <span style={{display: 'inline-flex', alignItems: 'center'}}>
            <DownloadOutlined style={{marginRight: 5}} />
            <span>Download file</span>
          </span>
        </DataStorageFileDownloadButton>
      </div>
    </div>
  );
};

ReportFilePreviewHeader.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filePath: PropTypes.string,
  rule: PropTypes.object,
};
