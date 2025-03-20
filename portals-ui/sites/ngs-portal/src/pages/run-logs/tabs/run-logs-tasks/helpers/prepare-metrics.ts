export const prepareMetrics = (metrics: string) => {
  return metrics.split('\n').reduce((metrics, line) => {
    const [key, value] = line.split('=');

    if (key) {
      metrics.push([key, value]);
    }

    return metrics;
  }, [] as string[][]);
};
