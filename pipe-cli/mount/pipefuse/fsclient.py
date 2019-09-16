from collections import namedtuple

from abc import ABCMeta, abstractmethod

File = namedtuple('File', ['name', 'size', 'mtime', 'ctime', 'contenttype', 'is_dir'])


class FileSystemClient:
    __metaclass__ = ABCMeta

    @abstractmethod
    def is_available(self):
        pass

    @abstractmethod
    def exists(self, path):
        pass

    def attrs(self, path):
        """
        Returns a single file or folder by the given path.

        :param path: Relative path to a single file or folder.
        :return: File, folder or None if the given path doesn't exist.
        """
        listing = self.ls(path, depth=0)
        return listing[0] or None

    @abstractmethod
    def ls(self, path, depth):
        """
        Returns all files and folder for the given path.

        Both file and folder paths are supported.

        :param path: Relative path to list its files and folders.
        :param depth: If set to negative value then recursive listing is returned.
        :return: List of files and folders.
        """
        pass

    @abstractmethod
    def upload(self, buf, path):
        pass

    @abstractmethod
    def delete(self, path):
        pass

    @abstractmethod
    def mv(self, old_path, path):
        pass

    @abstractmethod
    def mkdir(self, path):
        pass

    @abstractmethod
    def rmdir(self, path):
        pass

    @abstractmethod
    def download_range(self, buf, path, offset, length):
        pass

    @abstractmethod
    def upload_range(self, buf, path, offset):
        pass

    def flush(self, path):
        pass

    def utimens(self, path, times=None):
        pass
