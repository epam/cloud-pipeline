export enum PipePlatform {
  Linux = "Linux",
  Windows = "Windows",
  MacOS = "MacOS",
  MacOS_ARM = "MacOS-ARM",
}

export function getPipePlatform(): PipePlatform {
  if (process.platform === "linux") {
    return PipePlatform.Linux;
  } else if (process.platform === "win32") {
    return PipePlatform.Windows;
  } else if (process.platform === "darwin") {
    if (process.arch === "x64") {
      return PipePlatform.MacOS;
    } else if (process.arch === "arm64") {
      return PipePlatform.MacOS_ARM;
    } else {
      throw new Error(`Unsupported MacOS architecture "${process.arch}".`);
    }
  } else {
    throw new Error(`Unsupported platform "${process.platform}".`);
  }
}

export const pipeUris = {
  [PipePlatform.Linux]: { uri: "/pipeline/pipe.tar.gz", exec: "pipe" },
  [PipePlatform.Windows]: { uri: "/pipeline/pipe.zip", exec: "pipe-cli.exe" },
  [PipePlatform.MacOS]: { uri: "/pipeline/pipe-osx.tar.gz", exec: "pipe" },
  [PipePlatform.MacOS_ARM]: {
    uri: "/pipeline/pipe-osx-arm.tar.gz",
    exec: "pipe",
  },
};
