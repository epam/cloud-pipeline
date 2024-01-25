import React, { useContext, useMemo, useState } from 'react';
import PropTypes from 'prop-types';

function useCreateDiagnoseStore() {
  const [blocked, setBlocked] = useState(false);
  return useMemo(() => ({
    blocked,
    setBlocked,
  }), [blocked, setBlocked]);
}

const DiagnosticsContext = React.createContext({
  blocked: false,
  setBlocked: (() => {}),
})

function Diagnostics(
  {
    children,
  },
) {
  const store = useCreateDiagnoseStore();
  return (
    <DiagnosticsContext.Provider value={store}>
      {children}
    </DiagnosticsContext.Provider>
  );
}

export function useDiagnosing() {
  const { blocked } = useContext(DiagnosticsContext);
  return blocked;
}

export function useDiagnoseBlocker() {
  const { setBlocked } = useContext(DiagnosticsContext);
  return setBlocked;
}

Diagnostics.propTypes = {
  children: PropTypes.node.isRequired,
};

export default Diagnostics;
