# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os.path

import jsonpickle

from .cloud_pipeline_api import CloudPipelineClient
from .aws import AWSOmicsOperation, AWSOmicsFile
from .util.omics_utils import OmicsUrl

S3_SCHEMA = "s3://"
OMICS_SCHEMA = "omics://"


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


class OmicsStorageCopyOptions:

    MODE_DOWNLOAD = "DOWNLOAD"
    MODE_UPLOAD = "UPLOAD"
    MODE_IMPORT = "IMPORT"

    def __init__(self, mode, source, destination):
        self.mode = mode
        self.source = source
        self.destination = destination

    @staticmethod
    def from_raw_input(args):
        if "recursive" in args and bool(args["recursive"]):
            raise ValueError(
                "'recursive' is not supported for Omics Store!"
            )

        if "source" not in args or "destination" not in args:
            raise ValueError(
                "'source', 'destination' should be provided to register Omics file!"
            )

        source = args["source"]
        destination = args["destination"]
        mode = OmicsStorageCopyOptions.__define_mode(source, destination)
        if mode == OmicsStorageCopyOptions.MODE_UPLOAD or mode == OmicsStorageCopyOptions.MODE_IMPORT:
            if "additional_options" not in args:
                raise ValueError(
                    "'additional_options' with values: "
                    "[name=<>,subject_id=<>,sample_id=<>,file_type=<>,description=<optional>,"
                    "generated_from=<optional>,reference=<optional>] should be provided to register Omics file!"
                )
            name, description, subject_id, sample_id, reference_path, generated_from, file_type \
                = OmicsStorageCopyOptions.parse_additional_options(args["additional_options"])
            return OmicsStorageUploadOptions(
                source=source,
                destination=destination,
                name=name,
                mode=mode,
                omics_file_type=file_type,
                description=description,
                subject_id=subject_id,
                sample_id=sample_id,
                reference_path=reference_path,
                generated_from=generated_from
            )
        elif mode == OmicsStorageCopyOptions.MODE_DOWNLOAD:
            return OmicsStorageDownloadOptions(
                source=source,
                destination=destination,
                mode=mode
            )

    @classmethod
    def __define_mode(cls, source, destination):
        if OMICS_SCHEMA in source:
            if S3_SCHEMA in destination:
                raise ValueError("Export omics job functionality is not supported.")
            return OmicsStorageCopyOptions.MODE_DOWNLOAD

        if OMICS_SCHEMA in destination:
            if S3_SCHEMA in source:
                return OmicsStorageCopyOptions.MODE_IMPORT
            else:
                return OmicsStorageCopyOptions.MODE_UPLOAD
        raise ValueError("Can't define copy mode!.")

    @classmethod
    def parse_additional_options(cls, additional_options_string):
        def _get_file_type(file_type_str):
            result = file_type_str.upper()
            if result in ["FASTQ", "BAM", "UBAM", "CRAM"]:
                return result
            else:
                raise ValueError("file_type could be one of 'FASTQ', 'BAM', 'UBAM', 'CRAM'")

        if not additional_options_string:
            raise ValueError("Additional options should be provided to register file in Omics store!")

        additional_options = dict([p.split("=") for p in additional_options_string.split(",")])

        file_type_str = additional_options.get("file_type", "")
        if not file_type_str:
            raise ValueError("file_type should be specified")

        return (additional_options.get("name", None),
                additional_options.get("description", None),
                additional_options.get("subject_id", None),
                additional_options.get("sample_id", None),
                additional_options.get("reference", None),
                additional_options.get("generated_from", None),
                _get_file_type(file_type_str))


class OmicsStorageUploadOptions(OmicsStorageCopyOptions):

    def __init__(self, name, mode, source, destination, omics_file_type, subject_id,
                 sample_id, description, reference_path, generated_from):
        OmicsStorageCopyOptions.__init__(self, mode, source, destination)
        self.name = name
        self.omics_file_type = omics_file_type
        self.subject_id = subject_id
        self.sample_id = sample_id
        self.description = description
        self.generated_from = generated_from
        self.reference_path = reference_path


class OmicsStorageDownloadOptions(OmicsStorageCopyOptions):

    def __init__(self, mode, source, destination):
        OmicsStorageCopyOptions.__init__(self, mode, source, destination)


class OmicsStorageOperation:

    def __init__(self, operation_config):
        self.operation_config = operation_config


class OmicsStorageCopyOperation(OmicsStorageOperation):

    def __init__(self, config):
        OmicsStorageOperation.__init__(self, config)

    def copy(self, args):
        self.__copy(OmicsStorageCopyOptions.from_raw_input(args))

    def __copy(self, options: OmicsStorageCopyOptions):
        storage = self._load_storage(options)
        if options.mode == OmicsStorageCopyOptions.MODE_DOWNLOAD:
            AWSOmicsOperation(self.operation_config).download_file(storage, options.source, options.destination)
        elif options.mode == OmicsStorageCopyOptions.MODE_UPLOAD or options.mode == OmicsStorageCopyOptions.MODE_IMPORT:
            sources = self._prepare_sources(options.source)
            if options.mode == OmicsStorageCopyOptions.MODE_UPLOAD:
                AWSOmicsOperation(self.operation_config).upload_file(
                    storage, sources, options.name, options.omics_file_type, options.subject_id,
                    options.sample_id, options.description, options.generated_from,
                    OmicsUrl.path_to_arn(options.reference_path)
                )
            else:
                response = self.operation_config.api.import_omics_file(
                    storage, sources, options.name, options.omics_file_type, options.description,
                    options.sample_id, options.subject_id,  options.generated_from,
                    options.reference_path
                )
                if response:
                    print("AWS Omics Import Job: '{}' successfully initiated!".format(response.id))
        else:
            raise RuntimeError("Wrong copy mode of OmicsCopyOperation: '{}'".format(options.mode))

    def _load_storage(self, options):
        if options.mode == OmicsStorageCopyOptions.MODE_DOWNLOAD:
            _, storage_path, _ = OmicsUrl.parse_path(options.source)
            return self.operation_config.api.load_storage(storage_path)
        elif options.mode == OmicsStorageCopyOptions.MODE_UPLOAD or options.mode == OmicsStorageCopyOptions.MODE_IMPORT:
            _, storage_path, _ = OmicsUrl.parse_path(options.destination)
            return self.operation_config.api.load_storage(storage_path)
        else:
            raise RuntimeError("Wrong copy mode of OmicsCopyOperation: '{}'".format(options.mode))

    def _prepare_sources(self, source):
        def _validate(path):
            if not path.startswith("s3://") and not os.path.isfile(path):
                raise ValueError("Local file {} not found!".format(path))
            return path.strip()

        sources = source.split(",")
        if len(sources) > 2:
            raise ValueError("Source can have not more that 2 files!")
        return [_validate(s) for s in sources]


class OmicsStorageListingOptions:
    def __init__(self, storage, file_path, show_details, page_size, show_all):
        self.storage = storage
        self.file_path = file_path
        self.show_details = show_details
        self.page_size = page_size
        self.show_all = show_all


class OmicsStoreListingOperation(OmicsStorageOperation):

    def __init__(self, config):
        OmicsStorageOperation.__init__(self, config)

    def list(self, args: dict):
        def __dumps_to_json(object):
            return jsonpickle.encode(object, unpicklable=False)

        print(
            __dumps_to_json(
                self.__list(self._filter_and_normalize_args(self.operation_config.api, args))
            )
        )

    def __list(self, args: OmicsStorageListingOptions):
        if args.file_path is not None:
            return OmicsStorageObject.map_children(
                AWSOmicsOperation(self.operation_config).get_file_metadata(args.storage, args.file_path)
            )
        else:
            return [
                OmicsStorageObject.map(f) for f
                in AWSOmicsOperation(self.operation_config).list_files(
                    args.storage, None, args.page_size, args.show_all
                )
            ]

    @classmethod
    def _filter_and_normalize_args(cls, api: CloudPipelineClient, args_to_parse: dict):
        path = args_to_parse.get("path", None)
        if path is None:
            raise RuntimeError("Path should be provided")

        schema, storage_path, file_path = OmicsUrl.parse_path(path)
        if schema is None:
            raise RuntimeError("Fail to parse Omics path url!")
        storage = api.load_storage(storage_path)
        show_details = bool(args_to_parse.get("show_details", False))
        page_arg = args_to_parse.get("page", 0)
        page_size = 0 if page_arg is None else int(page_arg)
        show_all = bool(args_to_parse.get("show_all", False))
        return OmicsStorageListingOptions(storage, file_path, show_details, page_size, show_all)


def perform_storage_command(config, command, parsed_args):
    if command == 'cp':
        OmicsStorageCopyOperation(config).copy(parsed_args)
    elif command == 'ls':
        OmicsStoreListingOperation(config).list(parsed_args)
    else:
        raise RuntimeError("Unknown command: " + command)


