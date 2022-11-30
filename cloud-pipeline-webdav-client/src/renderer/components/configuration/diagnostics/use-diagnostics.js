import { useCallback, useMemo, useState } from 'react';
import { useDiagnoseBlocker } from './index';
import ipcResponse from '../../../common/ipc-response';

function useDiagnostics(type) {
  const setPending = useDiagnoseBlocker();
  const [error, setError] = useState(undefined);
  const [diagnosed, setDiagnosed] = useState(false);
  const [result, setResult] = useState(false);
  const [logs, setLogs] = useState(undefined);
  const diagnose = useCallback((payload) => {
    setPending(true);
    let fn;
    switch (type) {
      case 'api': fn = () => ipcResponse('diagnoseApi', payload);
        break;
      case 'ftp': fn = () => ipcResponse('diagnoseFtp', payload);
        break;
      case 'webdav': fn = () => ipcResponse('diagnoseWebdav', payload);
        break;
      default:
        break;
    }
    if (fn) {
      fn()
        .catch((diagnoseError) => {
          setError(diagnoseError.message);
          setResult(false);
        })
        .then((fnResult) => {
          const {
            error: diagnoseError,
            logs: diagnoseLogs,
            result: diagnoseResult,
          } = fnResult || {};
          setError(diagnoseError);
          setLogs(diagnoseLogs);
          setResult(diagnoseResult);
        })
        .then(() => {
          setPending(false);
          setDiagnosed(true);
        });
    }
  }, [setPending, type]);
  return useMemo(() => ({
    error,
    diagnosed,
    result,
    diagnose,
    logs,
  }), [
    error,
    diagnosed,
    result,
    diagnose,
    logs,
  ]);
}

export function useAPIDiagnostics() {
  return useDiagnostics('api');
}

export function useWebdavDiagnostics() {
  return useDiagnostics('webdav');
}

export function useFTPDiagnostics() {
  return useDiagnostics('ftp');
}
