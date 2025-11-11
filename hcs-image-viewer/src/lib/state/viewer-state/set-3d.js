export function set3D(state, action) {
  const { loader3DIndex, use3D = true, renderingModeIdx } = action;
  if (!use3D) {
    return {
      ...state,
      loader3DIndex: undefined,
      use3D: false,
    };
  }
  const {
    loadersInfo = [], renderingModes3D = [], renderingModeIdx: currentRenderingModeIdx, xSlice: currentXSlice, ySlice: currentYSlice, zSlice: currentZSlice,
  } = state;
  const {
    xSlice = currentXSlice,
    ySlice = currentYSlice,
    zSlice = currentZSlice,
  } = action;
  let modeIdx = renderingModeIdx === undefined ? currentRenderingModeIdx : renderingModeIdx;
  if (modeIdx === undefined || !renderingModes3D.find((m) => m.id === modeIdx)) {
    modeIdx = renderingModes3D[0]?.id;
  }
  if (!use3D) {
    modeIdx = undefined;
  }
  if (loader3DIndex === undefined) {
    const smallest = loadersInfo.slice().filter((l) => l.loadable).sort((a, b) => b.bytesPerChannel - a.bytesPerChannel).pop();
    if (!smallest) {
      return {
        ...state,
        loader3DIndex: undefined,
        use3D: false,
      };
    }
    return {
      ...state,
      loader3DIndex: smallest.loaderIdx,
      renderingModeIdx: modeIdx,
      use3D: true,
      xSlice,
      ySlice,
      zSlice,
    };
  }
  const loader3D = loadersInfo.find((l) => l.loaderIdx === loader3DIndex);
  if (loader3D === undefined || typeof loader3D !== 'object' || !loader3D.loadable) {
    return {
      ...state,
      loader3DIndex: undefined,
      use3D: false,
    };
  }
  return {
    ...state,
    loader3DIndex,
    use3D: true,
    xSlice,
    ySlice,
    zSlice,
    renderingModeIdx: modeIdx,
  };
}
