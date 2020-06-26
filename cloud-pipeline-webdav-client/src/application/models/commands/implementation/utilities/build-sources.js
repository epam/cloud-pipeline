function buildSources(fileSystem, sources) {
  return new Promise((resolve, reject) => {
    Promise.all((sources || []).map(fileSystem.buildSources))
      .then(arrays => {
        resolve(arrays.reduce((res, cur) => ([...res, ...cur]), []));
      })
      .catch(reject);
  });
}

export default buildSources;
