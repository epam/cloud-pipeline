import classNames from 'classnames';
import {Alert, Button, Input, Row} from 'antd';
import {ArrowsAltOutlined} from '@ant-design/icons';
import {useQuery} from '@tanstack/react-query';
import LoadingView from '../special/LoadingView.tsx';
import OpenStaticPreview from '../special/metadata/special/open-static-preview';
import {SampleSheetPreview, utilities} from '../special/sample-sheet';
import auditStorageAccessManager from '../../utils/audit-storage-access';
import {base64toString} from '../../utils/base64';
import {
  dataStorageContentQueryOptions,
  dataStorageDownloadUrlQueryOptions,
  getQueryErrorMessage,
} from '../../queries';
import type {MetadataContext} from './types.ts';
import type {ReactNode} from 'react';

type FilePreviewPanelProps = {
  context?: MetadataContext;
  entityName?: string;
  fileIsEmpty?: boolean;
  downloadable?: boolean;
  metadataRenderFn?: () => ReactNode;
  openEditFileForm?: () => void;
};

function FilePreviewPanel({
  context,
  entityName,
  fileIsEmpty,
  downloadable = true,
  metadataRenderFn,
  openEditFileForm,
}: FilePreviewPanelProps) {
  const storageId = context?.isDataStorageTags ? Number(context.entityParentId) : undefined;
  const path = context?.isDataStorageTags ? String(context.entityId) : undefined;
  const version = context?.entityVersion;
  const shouldLoad = !!storageId && !!path && !fileIsEmpty;

  const {
    data: content,
    isFetching: contentPending,
    error: contentQueryError,
  } = useQuery({
    ...dataStorageContentQueryOptions(storageId, path, version, {enabled: shouldLoad}),
  });
  const contentError = getQueryErrorMessage(contentQueryError);
  const {data: downloadUrlData, isFetching: downloadUrlPending} = useQuery({
    ...dataStorageDownloadUrlQueryOptions(storageId, path, version, {enabled: shouldLoad}),
  });

  if (contentPending || downloadUrlPending) {
    return <LoadingView className={undefined} style={undefined} children={undefined} />;
  }

  const previewRes: ReactNode[] = [];
  if (context?.entityClass === 'DATA_STORAGE_ITEM' && storageId && path) {
    previewRes.push(
      <OpenStaticPreview
        key="open-static"
        storageId={storageId}
        path={path}
        style={{margin: '5px 0'}}
      />,
    );
  }

  if (metadataRenderFn) {
    return (
      <>
        {previewRes}
        {metadataRenderFn()}
      </>
    );
  }

  const preview = content?.content ? base64toString(content.content) : '';
  const truncated = content?.truncated;
  const noContent = !preview;
  const mayBeBinary = content?.mayBeBinary;
  const error = contentError;
  const downloadUrl = downloadUrlData?.url;

  if (!shouldLoad && fileIsEmpty) {
    previewRes.push(
      <Row
        key="preview body"
        className="cp-text-not-important"
        style={{height: 40, margin: '0 auto'}}
      >
        No preview available
      </Row>,
    );
    return <>{previewRes}</>;
  }

  if (!content && !fileIsEmpty) {
    previewRes.push(
      <Row
        key="preview body"
        className="cp-text-not-important"
        style={{height: 40, margin: '0 auto'}}
      >
        No preview available
      </Row>,
    );
    return <>{previewRes}</>;
  }

  if (error) {
    return (
      <>
        {previewRes}
        <div key="body" style={{width: '100%', flex: 1, overflowY: 'auto', paddingTop: 10}}>
          <Alert type="error" title={error} />
        </div>
      </>
    );
  }

  if (!mayBeBinary) {
    previewRes.push(
      <Row
        justify="space-between"
        key="preview heading"
        className="cp-text-not-important"
        style={{marginTop: 5, marginBottom: 5}}
      >
        <span>File preview</span>
        {openEditFileForm ? (
          <Button onClick={openEditFileForm} size="small" style={{border: 'none'}}>
            <ArrowsAltOutlined />
          </Button>
        ) : null}
      </Row>,
    );
  }

  if (noContent && !mayBeBinary) {
    previewRes.push(
      <Row
        key="preview body"
        className="cp-text-not-important"
        style={{height: 40, margin: '0 auto'}}
      >
        No content
      </Row>,
    );
    return <>{previewRes}</>;
  }

  if (!mayBeBinary && utilities.isSampleSheetContent(preview, path)) {
    previewRes.push(
      <div
        id="file-preview-container"
        key="preview body"
        style={{flex: 1, minHeight: 0, overflow: 'auto'}}
      >
        <SampleSheetPreview
          className={undefined}
          expandDataSection={undefined}
          style={{width: '100%', height: '100%', overflow: 'auto'}}
          content={preview}
          size="small"
        />
      </div>,
    );
  } else if (!mayBeBinary) {
    previewRes.push(
      <div id="file-preview-container" key="preview body" className="cp-text-not-important">
        <Input.TextArea
          spellCheck={false}
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="off"
          autoSize={false}
          className={classNames('metadata-disabled-textarea', 'cp-metadata-item-content-preview')}
          styles={{
            textarea: {resize: 'none', height: '100%'},
          }}
          value={preview}
          readOnly
          disabled
        />
      </div>,
    );
  }

  const renderDownloadLink = () => (
    <span>
      <a
        href={downloadUrl}
        target="_blank"
        rel="noreferrer"
        download={entityName ?? path}
        style={{marginLeft: 5, marginRight: 5}}
        onClick={() => {
          if (!storageId || !path) {
            return;
          }
          (
            auditStorageAccessManager as unknown as {
              reportReadAccess: (options: {
                storageId: number;
                path: string;
                reportStorageType: string;
              }) => void;
            }
          ).reportReadAccess({
            storageId,
            path,
            reportStorageType: 'S3',
          });
        }}
      >
        Download file
      </a>
      to view full contents
    </span>
  );

  if (mayBeBinary && downloadUrl) {
    previewRes.push(
      <Row
        key="preview footer"
        className="cp-text-not-important"
        style={{marginTop: 5, marginBottom: 5}}
      >
        File preview is not available. {downloadable ? renderDownloadLink() : null}
      </Row>,
    );
  } else if (!mayBeBinary && truncated && downloadUrl) {
    previewRes.push(
      <Row
        key="preview footer"
        className="cp-text-not-important"
        style={{marginTop: 5, marginBottom: 5}}
      >
        File is too large to be shown. {downloadable ? renderDownloadLink() : null}
      </Row>,
    );
  }

  return <>{previewRes}</>;
}

export {FilePreviewPanel};
