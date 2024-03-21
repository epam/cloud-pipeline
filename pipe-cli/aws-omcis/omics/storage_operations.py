import re

from .cpapi import CloudPipelineClient, OmicsDataStorage
from .aws import AWSOmicsOperation


class OmicsStorageHelper(object):

    @classmethod
    def parse_path(cls, path):
        result = re.search(OmicsDataStorage.PATH_PATTERN, path)
        if result is not None:
            return result.group(1), result.group(2), result.group(5)
        else:
            return None, None, None


class OmicsStorageCopyOperation(object):

    @classmethod
    def copy(cls, api, parsed_args):
        pass


class OmicsStorageListingOptions:
    def __init__(self, storage, file_path, show_details, page_size):
        self.storage = storage
        self.file_path = file_path
        self.show_details = show_details
        self.page_size = page_size


class OmicsStorageObject:

    TYPE_FOLDER = "Folder"
    TYPE_FILE = "File"

    def __init__(self, type, name, path, labels):
        self.type = type
        self.name = name
        self.path = path
        self.labels = labels


class OmicsStorageFolder(OmicsStorageObject):

    def __init__(self, type, name, path, labels):
        OmicsStorageObject.__init__(self, type, name, path, labels)


class OmicsStorageFile(OmicsStorageObject):

    def __init__(self, type, name, path, labels,  size):
        OmicsStorageObject.__init__(self, type, name, path, labels)
        self.size = size


class OmicsStoreListingOperation(object):

    @classmethod
    def list(cls, api: CloudPipelineClient, args: dict):
        cls.__list(api, cls.filter_and_normalize_args(api, args))

    @classmethod
    def __list(cls, api: CloudPipelineClient, args: OmicsStorageListingOptions):
        if args.file_path is not None:
            AWSOmicsOperation(api).get_file_metadata(args.storage, args.file_path)
        else:
            AWSOmicsOperation(api).list_files(args.storage, None, args.page_size)

    @classmethod
    def filter_and_normalize_args(cls, api: CloudPipelineClient, args_to_parse: dict):
        path = args_to_parse.get("path", None)
        if path is None:
            raise RuntimeError("Path should be provided")

        schema, storage_path, file_path = OmicsStorageHelper.parse_path(path)
        if schema is None:
            raise RuntimeError()
        storage = api.load_storage(storage_path)
        show_details = bool(args_to_parse.get("show_details", False))
        page_size = int(args_to_parse.get("page", 0))
        return OmicsStorageListingOptions(storage, file_path, show_details, page_size)


def perform_storage_command(api, command, parsed_args):
    match command:
        case 'cp':
            OmicsStorageCopyOperation.copy(api, parsed_args)
        case 'ls':
            OmicsStoreListingOperation.list(api, parsed_args)
        case _:
            raise RuntimeError("Unknown command: " + command)
