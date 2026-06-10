import React from 'react';
import {FilePreviewLink} from '../../../../../../special/file-preview';
import {ReportFilePreviewHeader} from './report-file-preview-header';

export const PathList = ({paths, rule, onPreviewVisibilityChanged}) => {
  return (
    <ul>
      {paths.map((path) => (
        <li key={path}>
          <FilePreviewLink
            style={{display: 'block'}}
            filePath={path}
            title="Report preview"
            header={<ReportFilePreviewHeader filePath={path} rule={rule} />}
            preventDefault={false}
            onPreviewVisibilityChanged={onPreviewVisibilityChanged}
          />
        </li>
      ))}
    </ul>
  );
};
