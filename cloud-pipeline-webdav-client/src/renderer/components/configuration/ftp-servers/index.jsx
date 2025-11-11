import React, { useCallback, useMemo } from 'react';
import PropTypes from 'prop-types';
import { Button } from 'antd';
import Divider from '../divider';
import FTPServer from './ftp-server';
import '../configuration.css';

function useFTPServers(configuration, onChangeProperty) {
  const servers = configuration?.ftp;
  const onAddFTPServer = useCallback(() => {
    onChangeProperty('ftp', [...(servers || []), { protocol: 'ftp', useDefaultUser: true }]);
  }, [servers, onChangeProperty]);
  const onChangeFTPServer = useCallback((index, ftpServer) => {
    const ftpServers = (servers || []).slice();
    ftpServers.splice(index, 1, ftpServer);
    onChangeProperty('ftp', ftpServers);
  }, [servers, onChangeProperty]);
  const onRemoveFTPServer = useCallback((index) => {
    const ftpServers = (servers || []).slice();
    ftpServers.splice(index, 1);
    onChangeProperty('ftp', ftpServers);
  }, [servers, onChangeProperty]);
  return useMemo(() => ({
    servers,
    onAdd: onAddFTPServer,
    onChange: onChangeFTPServer,
    onRemove: onRemoveFTPServer,
  }), [servers, onAddFTPServer, onChangeFTPServer, onRemoveFTPServer]);
}

function FTPServers(
  {
    configuration,
    onChangeProperty,
  },
) {
  const {
    servers,
    onAdd,
    onChange,
    onRemove,
  } = useFTPServers(configuration, onChangeProperty);
  const onChangeServerProperty = useCallback((server, index) => (property) => (value) => {
    onChange(index, { ...server, [property]: value });
  }, [onChange]);
  const onRemoveServer = useCallback((index) => () => onRemove(index), [onRemove]);
  return (
    <>
      <Divider />
      <div
        className="configuration-row"
        style={{ marginBottom: 5 }}
      >
        FTP / SFTP servers:
      </div>
      {
        (servers || []).map((aServer, index) => (
          // eslint-disable-next-line react/no-array-index-key
          <React.Fragment key={`server-${index}`}>
            {
              index > 0 && (<Divider />)
            }
            <FTPServer
              aServer={aServer}
              onChangeProperty={onChangeServerProperty(aServer, index)}
              onRemove={onRemoveServer(index)}
              user={configuration?.user}
              password={configuration?.password}
            />
          </React.Fragment>
        ))
      }
      <div
        className="configuration-row"
        style={{ marginTop: 5 }}
      >
        <Button
          size="small"
          onClick={onAdd}
        >
          Add FTP server
        </Button>
      </div>
      <Divider />
    </>
  );
}

FTPServers.propTypes = {
  configuration: PropTypes.object,
  onChangeProperty: PropTypes.func,
};

FTPServers.defaultProps = {
  configuration: undefined,
  onChangeProperty: undefined,
};

export default FTPServers;
