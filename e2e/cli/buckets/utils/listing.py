# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import json

import datetime

import pytz
from tzlocal import get_localzone
from dateutil.parser import parse
from common_utils.pipe_cli import *


class FileModel(object):
    FILE_TYPE = 'File'
    DELETED_FILE_TYPE = '-File'
    ADDED_FILE_TYPE = '+File'
    FOLDER_TYPE = 'Folder'
    DELIMITER = '/'

    def __init__(self, name, size, last_modified, type, version_id=None, is_version_latest=False):
        self.name = name
        self.size = size
        self.last_modified = last_modified
        self.type = type
        self.version_id = version_id
        self.is_version_latest = is_version_latest

    @classmethod
    def parse_from_pipe_short(cls, name):
        if name.endswith(cls.DELIMITER):
            type = cls.FOLDER_TYPE
        else:
            type = cls.FILE_TYPE
        return FileModel(name.strip(), 0, None, type)

    @classmethod
    def parse_from_pipe_line(cls, line):
        parts = line.split()
        type = parts[0]
        if type == cls.FILE_TYPE:
            filename = parts[5].strip()
            size = int(parts[4])
            last_modified = parse(parts[2] + ' ' + parts[3])
            return FileModel(filename, size, last_modified, type)
        elif type == cls.DELETED_FILE_TYPE:
            if len(parts) > 4:
                filename = parts[3].strip()
                size = None
                last_modified = parse(parts[1] + ' ' + parts[2])
                version_id = parts[4].strip()
                is_latest = len(parts) > 4 and '(latest)' in parts[5]
                return FileModel(filename, size, last_modified, type, version_id, is_latest)
            else:
                filename = parts[3].strip()
                last_modified = parse(parts[1] + ' ' + parts[2])
                return FileModel(filename, None, last_modified, type)
        elif type == cls.ADDED_FILE_TYPE:
            filename = parts[5].strip()
            size = int(parts[4])
            last_modified = parse(parts[2] + ' ' + parts[3])
            version_id = parts[6].strip()
            is_latest = len(parts) > 7 and '(latest)' in parts[7]
            return FileModel(filename, size, last_modified, type, version_id, is_latest)
        else:
            filename = parts[1].strip()
            return FileModel(filename, 0, None, type)

    @classmethod
    def parse_from_aws_line(cls, line):
        parts = line.split()
        if len(parts) == 2 and parts[0] == 'PRE':
            type = cls.FOLDER_TYPE
            name = parts[1]
            return FileModel(name, 0, None, type)
        else:
            type = cls.FILE_TYPE
            filename = parts[3].strip()
            size = int(parts[2])
            last_modified = parse(parts[0] + ' ' + parts[1])
            return FileModel(filename, size, last_modified, type)

    @classmethod
    def parse_aws_object_versions(cls, lines):
        result = list()
        data = json.loads(" ".join(lines))
        delete_markers = list()
        if 'DeleteMarkers' in data:
            delete_markers = data['DeleteMarkers']
        versions = list()
        if 'Versions' in data:
            versions = data['Versions']
        for deleted_object in delete_markers:
            result.append(FileModel(deleted_object['Key'], None, cls.parse_aws_date_time(deleted_object['LastModified']),
                                    cls.DELETED_FILE_TYPE, deleted_object['VersionId'], deleted_object['IsLatest']))
        for version in versions:
            result.append(FileModel(version['Key'], int(version['Size']),
                                    cls.parse_aws_date_time(version['LastModified']),
                                    cls.ADDED_FILE_TYPE, version['VersionId'], version['IsLatest']))
        return result

    @classmethod
    def parse_aws_date_time(cls, aws_date_time):
        date_time_from_string = datetime.datetime.strptime(aws_date_time, "%Y-%m-%dT%H:%M:%S.%fZ")
        localized_date_time = pytz.utc.localize(date_time_from_string)
        return parse(localized_date_time.astimezone(get_localzone()).replace(tzinfo=None)
                     .strftime("%Y-%m-%d %H:%M:%S"))

    def equals(self, other, check_last_modified=False, check_version=True):
        if self.type != other.type:
            return False
        if self.name != other.name:
            return False
        # for folders only names are compared
        if self.type == self.FOLDER_TYPE:
            return True
        if check_last_modified and self.last_modified != other.last_modified:
            return False
        if check_version and self.version_id != other.version_id:
            return False
        return self.size == other.size and self.is_version_latest == other.is_version_latest

    def __str__(self):
        if self.type == self.FOLDER_TYPE:
            return "{}\t{}".format(self.type, self.name)
        else:
            return "{}\t{}\t{}\t".format(self.type, self.name, str(self.size), str(self.last_modified),
                                         str(self.version_id), str(self.is_version_latest))

    def __repr__(self):
        return self.__str__()


def parse_pipe_listing(lines, show_details=True):
    if show_details:
        return map(FileModel.parse_from_pipe_line, lines)
    else:
        result = []
        for line in lines:
            result.extend(map(FileModel.parse_from_pipe_short, line.split()))
        return result


def get_pipe_listing(path, show_details=True, recursive=False, token=None, versioning=False, paging=None):
    cmd_output = pipe_storage_ls(path, show_details=show_details, recursive=recursive, token=token,
                                 versioning=versioning, paging=paging)[0]
    if show_details and len(cmd_output) > 0:
        cmd_output = cmd_output[1:]
    return parse_pipe_listing(cmd_output, show_details=show_details)


def compare_listing(actual_listing, expected_listing, expected_length, show_details=True, check_last_modified=False,
                    check_version=False, sort=True):
    assert len(actual_listing) == expected_length, \
        "Length of listing [{}] doesn't match expected value [{}]".format(len(actual_listing), expected_length)
    assert len(actual_listing) == len(expected_listing), \
        "Length of listing results differ between pipe ls [{}] and cloud ls [{}]" \
                .format(len(actual_listing), len(expected_listing))
    if sort:
        actual_listing = sorted(actual_listing, key=lambda tup: (tup.type, tup.name, tup.size, tup.last_modified))
        expected_listing = sorted(expected_listing, key=lambda tup: (tup.type, tup.name, tup.size, tup.last_modified))
    for actual_item, expected_item in zip(actual_listing, expected_listing):
        if show_details:
            assert actual_item.equals(expected_item, check_last_modified=check_last_modified,
                                      check_version=check_version), \
                "Mismatch between files in listing:\n{}\n{}".format(str(actual_item), str(expected_item))
        else:
            assert actual_item.name == expected_item.name, "Mismatch between files in listing: {} vs {}" \
                    .format(actual_item.name, expected_item.name)


def assert_and_filter_first_versioned_listing_line(pipe_output, result_not_empty=True):
    first_line_file = None
    latest_version_file = None
    result = list()
    for line in pipe_output:
        if line.is_version_latest:
            latest_version_file = line
        if not line.version_id:
            first_line_file = line
            continue
        result.append(line)
    if result_not_empty:
        assert first_line_file.name == latest_version_file.name
        assert first_line_file.size == latest_version_file.size
        assert first_line_file.last_modified == latest_version_file.last_modified
    else:
        assert len(result) == 0
    return result


def get_non_latest_version(pipe_output):
    for line in pipe_output:
        if line.type == FileModel.ADDED_FILE_TYPE and not line.is_version_latest:
            return line.version_id
    return None


def get_latest_version(pipe_output):
    for line in pipe_output:
        if line.is_version_latest:
            return line.version_id
    return None


def filter_versioned_lines(lines):
    result = lines
    for line in lines:
        if line.type == FileModel.DELETED_FILE_TYPE or line.type == FileModel.ADDED_FILE_TYPE:
            result.remove(line)
    return result


def f(name, size=None, deleted=False, added=False, latest=False):
    """ Returns a storage file item with the specified parameters. """
    file_type = FileModel.FILE_TYPE
    if added:
        file_type = FileModel.ADDED_FILE_TYPE
    if deleted:
        file_type = FileModel.DELETED_FILE_TYPE
    return FileModel(name, size, None, file_type, is_version_latest=latest)


def d(name):
    """ Returns a storage folder item with the specified parameters. """
    return FileModel(name, None, None, FileModel.FOLDER_TYPE)
