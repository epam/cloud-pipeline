import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import { Button } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import StringProperty from '../string-property';
import SelectProperty from '../select-property';
import BooleanProperty from '../boolean-property';
import { useFTPDiagnostics } from '../diagnostics/use-diagnostics';
import DiagnoseState from '../diagnostics/diagnose-state';
import { useDiagnosing } from '../diagnostics';
import '../configuration.css';

const Protocols = {
  ftp: 'ftp',
  ftps: 'ftp-ssl implicit',
  ftpes: 'ftp-ssl explicit',
  sftp: 'sftp',
};

const ProtocolNames = {
  [Protocols.ftp]: 'FTP',
  [Protocols.ftps]: 'FTP-SSL (implicit)',
  [Protocols.ftpes]: 'FTP-SSL (explicit)',
  [Protocols.sftp]: 'SFTP',
};

const PROTOCOLS = [Protocols.ftp, Protocols.sftp, Protocols.ftps, Protocols.ftpes]
  .map((protocol) => ({
    value: protocol,
    name: ProtocolNames[protocol],
  }));

const DIAGNOSE_PROPERTY_STYLE = { width: 180 };

function FTPServer(
  {
    aServer,
    onChangeProperty,
    onRemove,
    user,
    password,
  },
) {
  const pending = useDiagnosing();
  const {
    error,
    diagnose,
    diagnosed,
    logs,
  } = useFTPDiagnostics();
  const onDiagnose = useCallback(() => {
    const payload = {
      ...aServer,
    };
    if (aServer.useDefaultUser) {
      payload.user = user;
      payload.password = password;
    }
    diagnose(payload);
  }, [
    aServer,
    diagnose,
    user,
    password,
  ]);
  return (
    <>
      <StringProperty
        property="Server"
        value={aServer.url}
        onChange={onChangeProperty('url')}
        placeholder="server:port"
        suffix={(<DiagnoseState diagnosed={diagnosed} error={error} />)}
      >
        <Button
          size="small"
          disabled={pending}
          onClick={onDiagnose}
          style={{ marginRight: 5 }}
        >
          TEST
        </Button>
        <Button
          size="small"
          danger
          type="primary"
          onClick={onRemove}
        >
          <DeleteOutlined />
        </Button>
      </StringProperty>
      <SelectProperty
        property="Protocol"
        values={PROTOCOLS}
        value={aServer?.protocol}
        onChange={onChangeProperty('protocol')}
      />
      <StringProperty
        disabled={aServer?.useDefaultUser}
        property="User"
        value={aServer.user}
        onChange={onChangeProperty('user')}
        placeholder="anonymous"
      >
        <BooleanProperty
          value={aServer?.useDefaultUser}
          onChange={onChangeProperty('useDefaultUser')}
          property="Use default credentials"
        />
      </StringProperty>
      <StringProperty
        property="Password"
        disabled={aServer?.useDefaultUser || !aServer?.user}
        value={aServer?.password}
        onChange={onChangeProperty('password')}
        secure
      />
      {
        logs && (
          <StringProperty
            property="Network log file"
            value={logs}
            propertyStyle={DIAGNOSE_PROPERTY_STYLE}
          />
        )
      }
      <BooleanProperty
        onChange={onChangeProperty('enableLogs')}
        property="Enable protocol logging"
        value={aServer?.enableLogs}
      />
    </>
  );
}

FTPServer.propTypes = {
  aServer: PropTypes.shape({
    url: PropTypes.string,
    protocol: PropTypes.string,
    user: PropTypes.string,
    password: PropTypes.string,
    useDefaultUser: PropTypes.bool,
    enableLogs: PropTypes.bool,
  }).isRequired,
  user: PropTypes.string,
  password: PropTypes.string,
  onChangeProperty: PropTypes.func,
  onRemove: PropTypes.func,
};

FTPServer.defaultProps = {
  onChangeProperty: undefined,
  onRemove: undefined,
  user: undefined,
  password: undefined,
};

export default FTPServer;
