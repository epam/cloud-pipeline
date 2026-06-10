import FileUpload from './file-upload';

class FileUploadList {
  constructor(files, uploadStorageId, uploadPath) {
    this.files = files.map(
      (file) =>
        new FileUpload(file, uploadStorageId, FileUpload.generateUploadPath(file, uploadPath)),
    );
    this.listeners = [];
  }

  destroy = () => {
    this.listeners = [];
    this.files.forEach((file) => {
      file.destroy();
    });
    this.files = [];
  };

  addEventListener = (listener) => {
    this.removeEventListener(listener);
    this.listeners.push(listener);
  };

  removeEventListener = (listener) => {
    this.listeners = this.listeners.filter((l) => l !== listener);
  };

  getState = () => {
    const stats = this.files.map((fileUpload) => fileUpload.getState());
    return {
      files: stats,
      done: !stats.some((stat) => !stat.done),
      aborted: stats.some((stat) => stat.aborted),
      hasErrors: stats.some((stat) => !!stat.error),
    };
  };

  report = () => {
    const state = this.getState();
    for (const listener of this.listeners) {
      listener(state);
    }
  };

  upload = async () => {
    await Promise.all(this.files.map((file) => file.doUpload()));
  };

  abort = async () => {
    await Promise.all(this.files.map((file) => file.abort()));
  };
}

export default FileUploadList;
