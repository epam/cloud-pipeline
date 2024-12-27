import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {
  Button,
  ConfigProvider,
  message,
} from 'antd';
import StringProperty from './string-property';
import useConfiguration from '../../common/use-configuration';
import BooleanProperty from './boolean-property';
import ipcAction from '../../common/ipc-action';
import ipcResponse from '../../common/ipc-response';
import { useAPIDiagnostics, useWebdavDiagnostics } from './diagnostics/use-diagnostics';
import Diagnostics, { useDiagnosing } from './diagnostics';
import DiagnoseState from './diagnostics/diagnose-state';
import FTPServers from './ftp-servers';
import Divider from './divider';
import AutoUpdateButton from '../auto-update/button';
import './configuration.css';

function onClose() {
  ipcAction('closeConfigurationWindow');
}

async function onSave(configuration) {
  try {
    await ipcResponse('saveConfiguration', configuration);
    onClose();
  } catch (error) {
    message.error(error.message, 5);
  }
}

const DIAGNOSE_PROPERTY_STYLE = { width: 180 };

function Configuration(
  {
    className,
    style,
  },
) {
  const {
    pending,
    configuration,
    createOnChangeCallback,
    onChangeProperty,
  } = useConfiguration();
  const onChangeServer = createOnChangeCallback('server');
  const onChangeAPI = createOnChangeCallback('api');
  const onChangeUser = createOnChangeCallback('user');
  const onChangePassword = createOnChangeCallback('password');
  const onChangeIgnoreCertificateErrors = createOnChangeCallback('ignoreCertificateErrors');
  const onChangeUpdatePermissions = createOnChangeCallback('updatePermissions');
  const onChangeLogsEnabled = createOnChangeCallback('logsEnabled');
  const onSaveCallback = useCallback(() => onSave(configuration), [configuration]);

  const {
    error: apiDiagnosticsError,
    logs: apiDiagnosticsLogs,
    diagnosed: apiDiagnosticsDiagnosed,
    diagnose: diagnoseAPI,
  } = useAPIDiagnostics();

  const {
    error: webdavDiagnosticsError,
    logs: webdavDiagnosticsLogs,
    diagnosed: webdavDiagnosticsDiagnosed,
    diagnose: diagnoseWebdav,
  } = useWebdavDiagnostics();

  const diagnosing = useDiagnosing();

  const testApi = useCallback(() => diagnoseAPI({
    url: configuration?.api,
    user: configuration?.user,
    password: configuration?.password,
    ignoreCertificateErrors: configuration?.ignoreCertificateErrors,
  }), [
    diagnoseAPI,
    configuration?.api,
    configuration?.user,
    configuration?.password,
    configuration?.ignoreCertificateErrors,
  ]);

  const testWebdav = useCallback(() => diagnoseWebdav({
    url: configuration?.server,
    user: configuration?.user,
    password: configuration?.password,
    ignoreCertificateErrors: configuration?.ignoreCertificateErrors,
  }), [
    diagnoseWebdav,
    configuration?.server,
    configuration?.user,
    configuration?.password,
    configuration?.ignoreCertificateErrors,
  ]);

  return (
    <ConfigProvider componentSize="small">
      <div
        className={
          classNames(
            className,
            'configuration',
          )
        }
        style={style}
      >
        <div className="configuration-body">
          <StringProperty
            property="Data Server"
            disabled={pending}
            value={configuration?.server}
            onChange={onChangeServer}
            suffix={(
              <DiagnoseState
                diagnosed={webdavDiagnosticsDiagnosed}
                error={webdavDiagnosticsError}
              />
            )}
          >
            <Button
              disabled={!configuration?.server || !configuration?.password || diagnosing}
              onClick={testWebdav}
            >
              TEST
            </Button>
          </StringProperty>
          <StringProperty
            property="API Server"
            disabled={pending}
            value={configuration?.api}
            onChange={onChangeAPI}
            suffix={(
              <DiagnoseState
                diagnosed={apiDiagnosticsDiagnosed}
                error={apiDiagnosticsError}
              />
            )}
          >
            <Button
              disabled={!configuration?.api || !configuration?.password || diagnosing}
              onClick={testApi}
            >
              TEST
            </Button>
          </StringProperty>
          <StringProperty
            property="User name"
            disabled={pending}
            value={configuration?.user}
            onChange={onChangeUser}
          />
          <StringProperty
            property="Password"
            disabled={pending}
            secure
            value={configuration?.password}
            onChange={onChangePassword}
          />
          <FTPServers
            configuration={configuration}
            onChangeProperty={onChangeProperty}
          />
          <BooleanProperty
            value={configuration?.ignoreCertificateErrors}
            onChange={onChangeIgnoreCertificateErrors}
            property="Ignore certificate errors"
          />
          <BooleanProperty
            value={configuration?.updatePermissions}
            onChange={onChangeUpdatePermissions}
            property="Update destination file permissions after copy / create operation"
          />
          <BooleanProperty
            value={configuration?.logsEnabled}
            onChange={onChangeLogsEnabled}
            property="Enable logging"
          />
          {
            (webdavDiagnosticsLogs || apiDiagnosticsLogs) && (
              <>
                <Divider />
                <StringProperty
                  property="WebDAV Network log file"
                  value={webdavDiagnosticsLogs}
                  propertyStyle={DIAGNOSE_PROPERTY_STYLE}
                />
                <StringProperty
                  property="API Network log file"
                  value={apiDiagnosticsLogs}
                  propertyStyle={DIAGNOSE_PROPERTY_STYLE}
                />
              </>
            )
          }
          {
            (configuration?.version || configuration?.componentVersion) && (
              <>
                <Divider />
                {
                  configuration?.version && (
                    <div className="version-row">
                      App Version:
                      <span className="version">{configuration?.version}</span>
                    </div>
                  )
                }
                {
                  configuration?.componentVersion && (
                    <div className="version-row">
                      App Component Version:
                      <span className="version">{configuration?.componentVersion}</span>
                    </div>
                  )
                }
              </>
            )
          }
          <AutoUpdateButton />
        </div>
        <div
          className="configuration-footer"
        >
          <Button
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button
            type="primary"
            onClick={onSaveCallback}
          >
            Save
          </Button>
        </div>
      </div>
    </ConfigProvider>
  );
}

Configuration.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

Configuration.defaultProps = {
  className: undefined,
  style: undefined,
};

function ConfigurationHOC({ className, style }) {
  return (
    <Diagnostics>
      <Configuration className={className} style={style} />
    </Diagnostics>
  );
}

ConfigurationHOC.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

ConfigurationHOC.defaultProps = {
  className: undefined,
  style: undefined,
};

export default ConfigurationHOC;
