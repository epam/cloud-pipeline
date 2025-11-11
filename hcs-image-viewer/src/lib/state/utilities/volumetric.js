function getLoaderStats(loader, resolution) {
  const { shape, labels } = loader;
  const height = shape[labels.indexOf('y')];
  const width = shape[labels.indexOf('x')];
  const depth = shape[labels.indexOf('z')];
  const depthDownsampled = Math.max(1, depth >> resolution);
  // Check memory allocation limits
  const totalBytes = 4 * height * width * depthDownsampled;
  return {
    height, width, depthDownsampled, totalBytes,
  };
}

export function canLoadResolution(loader, resolution) {
  const {
    totalBytes, height, width, depthDownsampled,
  } = getLoaderStats(
    loader,
    resolution,
  );
  const maxHeapSize = window.performance?.memory
    && window.performance?.memory?.jsHeapSizeLimit / 2;
  const maxSize = maxHeapSize || 2 ** 31 - 1;
  return (
    totalBytes < maxSize
    && height < 2048
    && depthDownsampled < 2048
    && width < 2048
    && depthDownsampled > 1
  );
}

export function getLoadersDownSampleInfo(loader) {
  const loaders = Array.isArray(loader) ? loader : [loader];
  return loaders.map((aLoader, idx) => {
    const loadable = canLoadResolution(aLoader, idx);
    const {
      depthDownsampled, totalBytes,
    } = getLoaderStats(aLoader, idx);
    return {
      loaderIdx: idx,
      loadable,
      bytesPerChannel: totalBytes,
      depthDownsampled,
    };
  });
}
