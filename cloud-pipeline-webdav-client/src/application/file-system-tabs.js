import {FileSystems} from "./models/file-systems";

const RootDirectories = {
  left: require('os').homedir(),
  right: undefined,
};

export {RootDirectories};

export default {
  left: FileSystems.local,
  right: FileSystems.webdav,
}
