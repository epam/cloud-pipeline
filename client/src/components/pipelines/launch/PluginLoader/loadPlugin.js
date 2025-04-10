export function loadPlugin (url, globalVarName, config = {}) {
  return new Promise((resolve, reject) => {
    if (window[globalVarName]) {
      console.log(`Plugin ${globalVarName} is already loaded, initializing with config`);
      try {
        const plugin = initializePlugin(window[globalVarName], config);
        resolve(plugin);
        return;
      } catch (error) {
        reject(new Error(`Failed to initialize existing plugin ${globalVarName}: ${error.message}`));
        return;
      }
    }

    const script = document.createElement('script');
    script.src = url;

    script.onload = () => {
      if (window[globalVarName]) {
        try {
          const plugin = initializePlugin(window[globalVarName], config);
          resolve(plugin);
        } catch (error) {
          reject(new Error(`Failed to initialize plugin ${globalVarName}: ${error.message}`));
        }
      } else {
        reject(new Error(`Micro frontend ${globalVarName} is not available after loading.`));
      }
    };

    script.onerror = () => reject(new Error(`Failed to load micro frontend: ${url}`));

    document.head.appendChild(script);
  });
}

function initializePlugin (pluginExport, config) {
  if (typeof pluginExport === 'function') {
    return pluginExport(config);
  }

  if (typeof pluginExport === 'object' && typeof pluginExport.init === 'function') {
    return pluginExport.init(config);
  }

  return pluginExport;
}
