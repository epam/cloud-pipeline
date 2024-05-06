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
import platform
import re
import sys

from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials, Credentials
from botocore.session import get_session
from omics.uriparse.uri_parse import OmicsUriParser
from omics.transfer.manager import TransferManager
from .cloud_pipeline_api import OmicsStoreType
from .util.exception import PipeOmicsException
from .util.progress_utils import ProgressBarSubscriber, FinalEventSubscriber

UPLOAD_FAILED = "UPLOAD_FAILED"


class AWSOmicsFileDownloadRequest:

    def __init__(self, omics_store_id, omics_resource_id, omics_file_name, destination_dir, local_file_name, size):
        self.omics_store_id = omics_store_id
        self.omics_resource_id = omics_resource_id
        self.omics_file_name = omics_file_name
        self.destination_dir = destination_dir
        self.local_file_name = local_file_name
        self.size = size


class AWSOmicsFile:

    def __init__(self):
        self.name = None
        self.id = None
        self.type = None
        self.status = None
        self.status_message = None
        self.description = None
        self.size = None
        self.sizes = {}
        self.modified = None
        self.raw = None
        self.files = {}

    @classmethod
    def from_aws_omics_ref_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.type = "REFERENCE"
        file.size, file.sizes = cls._get_size(file)
        return file

    @classmethod
    def from_aws_omics_seq_response(cls, response):
        file = cls._with_common_metadata_fields(response)
        file.type = response.get("fileType", None)
        file.size, file.sizes = cls._get_size(file)
        return file

    @classmethod
    def _with_common_metadata_fields(cls, response):
        file = AWSOmicsFile()
        file.name = response["name"]
        file.id = response["id"]
        file.status = response["status"]
        file.status_message = response.get("statusMessage", "")
        file.description = response.get("description", None)
        file.modified = response.get("updateTime", response["creationTime"])
        file.raw = response
        file.files = response.get("files", {})
        return file

    @classmethod
    def _get_size(cls, file):
        sizes = {}
        size = 0
        for file_name in ["index", "source", "source1", "source2"]:
            if file_name in file.files:
                file_size = file.files.get(file_name, {}).get("contentLength", 0)
                size += file_size
                sizes[file_name] = file_size
        return size, sizes


class AWSOmicsOperation:

    def __init__(self, operation_config):
        self.operation_config = operation_config

    def get_file_metadata(self, storage, file_id):
        omics = self.get_omics(storage, storage.region_name, read=True)
        if storage.type == OmicsStoreType.OMICS_REF:
            response = omics.get_reference_metadata(id=file_id, referenceStoreId=storage.cloud_store_id)
            return AWSOmicsFile.from_aws_omics_ref_response(response)
        elif storage.type == OmicsStoreType.OMICS_SEQ:
            response = omics.get_read_set_metadata(id=file_id, sequenceStoreId=storage.cloud_store_id)
            return AWSOmicsFile.from_aws_omics_seq_response(response)

    def list_files(self, storage, token, page_size, show_all):

        # Helper method to get one page with AWS SDK
        def _get_page(aws_omics_method, req_kwars, object_field, object_mapping, token, page_size):
            if token is not None:
                req_kwars["nextToken"] = token
            if page_size is not None and page_size > 0:
                req_kwars["maxResults"] = page_size
            try:
                response = aws_omics_method(**req_kwars)
                return response.get("nextToken", None), [object_mapping(e) for e in response.get(object_field, [])]
            except Exception as e:
                raise RuntimeError("Something went wrong during the request to AWS.", e)

        # Based on storage type choose appropriate SKD method and object mapping
        omics = self.get_omics(storage, storage.region_name, list=True)
        if storage.type == OmicsStoreType.OMICS_REF:
            aws_omics_method = omics.list_references
            req_kwars = {'referenceStoreId': storage.cloud_store_id}
            object_mapping = AWSOmicsFile.from_aws_omics_ref_response
            object_field = 'references'
        elif storage.type == OmicsStoreType.OMICS_SEQ:
            aws_omics_method = omics.list_read_sets
            req_kwars = {'sequenceStoreId': storage.cloud_store_id}
            object_mapping = AWSOmicsFile.from_aws_omics_seq_response
            object_field = 'readSets'
        else:
            raise RuntimeError("Unexpected storage type: " + storage.type)

        # Perform listing
        result = []
        if show_all:
            next_token, page = _get_page(aws_omics_method, req_kwars, object_field, object_mapping, token, page_size)
            result.extend(page)
            while next_token is not None:
                next_token, page = _get_page(
                    aws_omics_method, req_kwars, object_field, object_mapping, next_token, page_size
                )
                result.extend(page)
        else:
            _, result = _get_page(aws_omics_method, req_kwars, object_field, object_mapping, token, page_size)
        return result

    def download_file(self, storage, source, destination, force):

        def __define_local_location(path, source, force):
            parent_dir = os.path.dirname(path)
            if not parent_dir:
                parent_dir = "."
            basename = os.path.basename(path)

            # source matches path to specific file in readSet/reference
            if re.search(".*/(index|source|source1|source2)", source):
                if os.path.isdir(path):
                    return path, None
                elif os.path.exists(path):
                    if force:
                        return parent_dir, basename
                    else:
                        raise PipeOmicsException("File with path {} already exists! "
                                                 "Specify --force/-f to overwrite the file.".format(path))
                else:
                    # if local path doesn't exist and source is referring to the exact file
                    # we assume that user specified local path also as a path to exact file local destination
                    # e.g. basename is a file name
                    return parent_dir, basename
            else:
                # if we try to download the whole readSet/reference from AWS Omics - return path as a location_dir
                # if path is a directory and force specified - download anyway
                # if path is a file - throw an exception as it won't allow to create a directory
                if os.path.exists(path):
                    if not os.path.isdir(path):
                        raise PipeOmicsException("File with path {} already exists!".format(path))

                    if not force:
                        raise PipeOmicsException("Directory with path {} already exists! "
                                                 "Specify --force/-f to download files anyway.".format(path))
                    else:
                        return path, None
                else:
                    return path, None

        # omics tools adds gz even to bam or cram files (it just checked if a file is gziped or not)
        # this method will rename file back as it was configured by user or defined in this code
        def __rename_file_if_needed(download_request: AWSOmicsFileDownloadRequest):
            downloaded_file = os.path.join(download_request.destination_dir, download_request.local_file_name) + ".gz"
            if os.path.exists(downloaded_file):
                # remove last 3 character from the file name
                # because it was added by aws-omics-tools lib
                os.rename(downloaded_file, downloaded_file[:-3])

        # Remove tailing slash if any, because OmicsUriParser doesn't expect
        # it when parsing omics readSet or reference url
        source = source.strip('/')

        destination_dir, destination_file_name = __define_local_location(destination, source, force)

        if not os.path.exists(destination_dir):
            os.makedirs(destination_dir)

        manager = TransferManager(self.get_omics(storage, storage.region_name, read=True, write=True))

        download_requests = self.__fetch_files_to_download(storage, source, destination_dir, destination_file_name)

        for download_request in download_requests:
            subscribers = [
                ProgressBarSubscriber(
                    download_request.size,
                    "{}/{}".format(download_request.omics_resource_id, download_request.omics_file_name),
                    self.operation_config.piped_stdout
                )
            ]

            local_file_absolute_path = str(
                os.path.join(download_request.destination_dir, download_request.local_file_name)
            )
            if storage.type == OmicsStoreType.OMICS_SEQ:
                manager.download_read_set_file(
                    download_request.omics_store_id, download_request.omics_resource_id,
                    download_request.omics_file_name,
                    client_fileobj=local_file_absolute_path, subscribers=subscribers
                )
            elif storage.type == OmicsStoreType.OMICS_REF:
                manager.download_reference_file(
                    storage.cloud_store_id, download_request.omics_resource_id,
                    download_request.omics_file_name,
                    client_fileobj=local_file_absolute_path, subscribers=subscribers
                )

            __rename_file_if_needed(download_request)

    def upload_file(self, storage, sources, name, file_type, subject_id, sample_id,
                    description=None, generated_from=None, reference_arn=None):
        if storage.type == OmicsStoreType.OMICS_REF:
            raise RuntimeError("Direct upload to Omics Reference store isn't supported!")
        manager = TransferManager(self.get_omics(storage, storage.region_name, read=True, write=True))
        sys.stdout.write("Omics {} file(s) {}: upload started! Please wait...\n".format(file_type, sources))
        sys.stdout.flush()
        subscribers = [FinalEventSubscriber()]
        manager.upload_read_set(sources, storage.cloud_store_id, file_type, name, subject_id,
                                sample_id, reference_arn, generated_from, description, subscribers=subscribers)

    def get_omics(self, storage, region, list=False, read=False, write=False):
        return self.__assumed_session(storage, list, read, write).client('omics', config=Config(), region_name=region)

    def __assumed_session(self, storage, list, read, write):
        def __refresh():
            credentials = self.operation_config.api.get_temporary_credentials(storage, list, read, write)
            return dict(
                access_key=credentials.access_key_id,
                secret_key=credentials.secret_key,
                token=credentials.session_token,
                expiry_time=credentials.expiration)

        fresh_metadata = __refresh()
        if 'token' not in fresh_metadata or not fresh_metadata['token']:
            session_credentials = Credentials(fresh_metadata['access_key'], fresh_metadata['secret_key'])
        else:
            session_credentials = RefreshableCredentials.create_from_metadata(
                metadata=fresh_metadata,
                refresh_using=__refresh,
                method='sts-assume-role')

        s = get_session()
        # hack to work on windows machine, we pre-built hte pyinstaller bundle with data folder from botocore
        # included in 'botocore-data' folder and here add additional place for botocore to look in
        if platform.system() == 'Windows':
            s.get_component('data_loader').search_paths.append(
                os.path.join(os.path.dirname(os.path.realpath(sys.argv[0])), 'botocore', 'data')
            )
            os.environ['REQUESTS_CA_BUNDLE'] = os.path.join(
                os.path.dirname(os.path.realpath(sys.argv[0])), 'botocore', 'cacert.pem'
            )
        s._credentials = session_credentials
        return Session(botocore_session=s)

    # Defines which files and under which names should be downloaded
    def __fetch_files_to_download(self, storage, source, destination_dir, destination_file_name):

        def __get_file_name_suffix(omics_file_name, omics_resource_type):
            if omics_resource_type == "FASTQ":
                return "_R1" if omics_file_name == "source1" else "_R2"
            else:
                return ""

        def __get_file_ext(omics_file_name, omics_resource_type):
            if omics_resource_type == "FASTQ":
                return "fastq.gz"
            elif omics_resource_type == "BAM" or omics_resource_type == "UBAM":
                if omics_file_name == "index":
                    return "bam.bai"
                else:
                    return "bam"
            elif omics_resource_type == "CRAM":
                if omics_file_name == "index":
                    return "cram.crai"
                else:
                    return "cram"
            elif omics_resource_type == "REFERENCE":
                if omics_file_name == "index":
                    return "fa.fai"
                else:
                    return "fa"
            else:
                raise ValueError(
                    "Wrong resouce type: {}, supported in FASTQ, BAM, UBAM, CRAM, REFERENCE".format(omics_resource_type)
                )
        try:
            omics_file = OmicsUriParser(source).parse()
        except ValueError as e:
            raise PipeOmicsException("Invalid AWS Omics URL format: '{}'".format(source))

        file_metadata = self.get_file_metadata(storage, omics_file.resource_id)

        if file_metadata.status == UPLOAD_FAILED:
            raise PipeOmicsException(
                "Omics file status: '{}', message: {}".format(UPLOAD_FAILED, file_metadata.status_message)
            )

        # If original url have source[1|2]|index - will download only specific file, else - the whole set
        if re.search(".*/(index|source|source1|source2)", source):
            omics_file_name = omics_file.file_name.lower()
            if omics_file_name not in file_metadata.files:
                raise ValueError(
                    "Can't find '{}' file in AWS Omics {} with id: {} object".format(
                        omics_file_name, omics_file.resource_type, omics_file.resource_id
                    )
                )
            local_file_name = destination_file_name
            if not local_file_name:
                local_file_name = "{}{}.{}".format(
                    file_metadata.name,
                    __get_file_name_suffix(omics_file_name, file_metadata.type),
                    __get_file_ext(omics_file_name, file_metadata.type)
                )

            return [
                AWSOmicsFileDownloadRequest(
                    storage.cloud_store_id,
                    file_metadata.id,
                    omics_file_name,
                    destination_dir,
                    local_file_name,
                    file_metadata.sizes[omics_file_name]
                )
            ]
        else:
            result = []
            for omics_file_name, file in file_metadata.files.items():
                local_file_name = "{}{}.{}".format(
                    file_metadata.name,
                    __get_file_name_suffix(omics_file_name, file_metadata.type),
                    __get_file_ext(omics_file_name, file_metadata.type)
                )
                result.append(
                    AWSOmicsFileDownloadRequest(
                        storage.cloud_store_id,
                        file_metadata.id,
                        omics_file_name,
                        destination_dir,
                        local_file_name,
                        file_metadata.sizes[omics_file_name]
                    )
                )
            return result

