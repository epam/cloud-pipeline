function buildSources(fileSystem, sources) {
  return new Promise((resolve, reject) => {
    Promise.all((sources || []).map(source => fileSystem.buildSources(source)))
      .then(arrays => {
        resolve(arrays.reduce((res, cur) => ([...res, ...cur]), []));
      })
      .catch(reject);
  });
}

export default buildSources;
