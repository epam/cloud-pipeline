import React, { useContext } from 'react';

const SplitPanelRowContext = React.createContext(0);

function useSplitPanelRow() {
  return useContext(SplitPanelRowContext);
}

export {
  SplitPanelRowContext,
  useSplitPanelRow,
};
