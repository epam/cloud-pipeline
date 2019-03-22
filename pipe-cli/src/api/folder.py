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

from .base import API
from ..model.folder_model import FolderModel


class Folder(API):
    def __init__(self):
        super(Folder, self).__init__()

    @classmethod
    def load_tree(cls):
        api = cls.instance()
        response_data = api.call('/folder/loadTree', None)
        return FolderModel.load(response_data['payload'])

    @classmethod
    def load(cls, identifier):
        api = cls.instance()
        response_data = api.call('/folder/find?id={}'.format(identifier), None)
        return FolderModel.load(response_data['payload'])

    @classmethod
    def load_by_name(cls, dir_name):
        folder = cls.load_tree()
        directory = cls.search_tree(folder, dir_name)
        if directory is not None:
            api = cls.instance()
            response_data = api.call('/folder/{}/load'.format(directory.id), None)
            return FolderModel.load(response_data['payload'])

    @classmethod
    def search_tree(cls, folder, dir_name):
        dirs = cls.collect_dirs(None, folder)
        for directory in dirs:
            if directory.name == dir_name:
                return directory

    @classmethod
    def collect_dirs(cls, dirs, folder):
        if dirs is None:
            dirs = []

        for child in folder.child_folders:
            dirs.append(child)
            cls.collect_dirs(dirs, child)
        return dirs
