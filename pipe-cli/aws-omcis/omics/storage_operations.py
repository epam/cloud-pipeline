from .cloud_pipeline_api import CloudPipelineClient, OmicsDataStorage
from .aws import AWSOmicsOperation, AWSOmicsFile

S3_SCHEMA = "s3://"
OMICS_SCHEMA = "omics://"


class OmicsStorageCopyOptions:

    MODE_DOWNLOAD = "DOWNLOAD"
    MODE_UPLOAD = "UPLOAD"
    MODE_IMPORT = "IMPORT"

    def __init__(self, mode, source, destination, recursive):
        self.mode = mode
        self.source = source
        self.destination = destination
        self.recursive = recursive

    @staticmethod
    def from_raw_input(args):
        mode = OmicsStorageCopyOptions.define_mode(args["source"], args["destination"])
        match mode:
            case OmicsStorageCopyOptions.MODE_UPLOAD:
                return OmicsStorageUploadOptions(
                    source=args["source"],
                    destination=args["destination"],
                    mode=mode,
                    recursive=args.get("recursive", False)
                )

    @classmethod
    def define_mode(cls, source, destination):
        if OMICS_SCHEMA in source:
            if S3_SCHEMA in destination:
                raise ValueError("Export omics job functionality is not supported.")
            return OmicsStorageCopyOptions.MODE_DOWNLOAD

        if OMICS_SCHEMA in destination:
            if S3_SCHEMA in source:
                return OmicsStorageCopyOptions.MODE_IMPORT
            else:
                return OmicsStorageCopyOptions.MODE_UPLOAD


class OmicsStorageUploadOptions(OmicsStorageCopyOptions):

    def __init__(self, mode, source, destination, recursive,
                 omics_file_type, subject_id, sample_id, description, reference_id, generated_from):
        OmicsStorageCopyOptions.__init__(self, mode, source, destination, recursive)
        self.omics_file_type = omics_file_type
        self.subject_id = subject_id
        self.sample_id = sample_id
        self.description = description
        self.generated_from = generated_from
        self.reference_id = reference_id


class OmicsStorageDownloadOptions(OmicsStorageCopyOptions):

    def __init__(self, mode, source, destination, recursive):
        OmicsStorageCopyOptions.__init__(self, mode, source, destination, recursive)


class OmicsStorageCopyOperation(object):

    @classmethod
    def copy(cls, api, args):
        if 'source' not in args or 'destination' not in args:
            raise ValueError("Parameters source and destination should be provided for copy operation!")

    @classmethod
    def _filter_and_normalize_args(cls, api: CloudPipelineClient, args_to_parse: dict):
        path = args_to_parse.get("path", None)
        if path is None:
            raise RuntimeError("Path should be provided")

        schema, storage_path, file_path = OmicsDataStorage.parse_path(path)
        if schema is None:
            raise RuntimeError()
        storage = api.load_storage(storage_path)
        show_details = bool(args_to_parse.get("show_details", False))
        page_size = int(args_to_parse.get("page", 0))
        show_all = bool(args_to_parse.get("show_all", False))
        return OmicsStorageListingOptions(storage, file_path, show_details, page_size, show_all)


class OmicsStorageListingOptions:
    def __init__(self, storage, file_path, show_details, page_size, show_all):
        self.storage = storage
        self.file_path = file_path
        self.show_details = show_details
        self.page_size = page_size
        self.show_all = show_all


class OmicsStorageObject:

    TYPE_FOLDER = "Folder"
    TYPE_FILE = "File"

    def __init__(self, type, name, path, changed, size, labels):
        self.name = name
        self.path = path
        self.changed = changed
        self.type = type
        self.size = size
        self.labels = labels

    @staticmethod
    def map_children(omics_file: AWSOmicsFile):
        if omics_file.files is None or len(omics_file.files) == 0:
            raise ValueError("Can't parse AWSOmicsFile children objects")
        return [
            OmicsStorageObject(
                OmicsStorageObject.TYPE_FILE,
                omics_file.name,
                "{id}/{name}".format(id=omics_file.id, name=name),
                omics_file.modified,
                # TODO not sure that it is valid to use this value (omics bills based on base pairs)
                f["contentLength"],
                labels={
                    "status": omics_file.status,
                    "type": omics_file.type
                }
            ) for name, f in omics_file.files.items()
        ]

    @staticmethod
    def map(omics_file: AWSOmicsFile):
        return OmicsStorageObject(
            OmicsStorageObject.TYPE_FOLDER,
            omics_file.name,
            omics_file.id,
            omics_file.modified,
            omics_file.size,
            labels={
                "status": omics_file.status,
                "type": omics_file.type
            }
        )


class OmicsStoreListingOperation(object):

    @classmethod
    def list(cls, api: CloudPipelineClient, args: dict):
        return cls.__list(api, cls._filter_and_normalize_args(api, args))

    @classmethod
    def __list(cls, api: CloudPipelineClient, args: OmicsStorageListingOptions):
        if args.file_path is not None:
            return OmicsStorageObject.map_children(
                AWSOmicsOperation(api).get_file_metadata(args.storage, args.file_path)
            )
        else:
            return [
                OmicsStorageObject.map(f) for f
                in AWSOmicsOperation(api).list_files(args.storage, None, args.page_size, args.show_all)
            ]

    @classmethod
    def _filter_and_normalize_args(cls, api: CloudPipelineClient, args_to_parse: dict):
        path = args_to_parse.get("path", None)
        if path is None:
            raise RuntimeError("Path should be provided")

        schema, storage_path, file_path = OmicsDataStorage.parse_path(path)
        if schema is None:
            raise RuntimeError()
        storage = api.load_storage(storage_path)
        show_details = bool(args_to_parse.get("show_details", False))
        page_size = int(args_to_parse.get("page", 0))
        show_all = bool(args_to_parse.get("show_all", False))
        return OmicsStorageListingOptions(storage, file_path, show_details, page_size, show_all)


def perform_storage_command(api, command, parsed_args):
    match command:
        case 'cp':
            return OmicsStorageCopyOperation.copy(api, parsed_args)
        case 'ls':
            return OmicsStoreListingOperation.list(api, parsed_args)
        case _:
            raise RuntimeError("Unknown command: " + command)
