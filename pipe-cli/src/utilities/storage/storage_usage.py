# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
from treelib import Tree


class StorageUsageAccumulator(object):

    def __init__(self, bucket_name, relative_path, delimiter, max_depth):
        self.delimiter = delimiter
        self.relative_path = relative_path
        self.bucket_name = bucket_name
        self.max_depth = max_depth

        self.result = Tree()
        self.relative_path_len = 0
        self.range_start = 1
        if relative_path:
            root_path = delimiter.join([bucket_name, relative_path])
            self.root = self.result.create_node(root_path, root_path, data=StorageUsageItem())
            self.relative_path_len = len(relative_path.split(delimiter))
            self.range_start = self.relative_path_len + 1
        else:
            self.root = self.result.create_node(bucket_name, bucket_name, data=StorageUsageItem())

    def add_path(self, name, size):
        tokens = name.split(self.delimiter)
        self.root.data.add_item(size)

        if len(tokens) - self.relative_path_len > 1:
            if self.relative_path:
                first_token = self.delimiter.join([self.bucket_name] + tokens[:self.relative_path_len + 1])
            else:
                first_token = self.delimiter.join([self.bucket_name, tokens[0]])
            if not self.result.contains(first_token):
                self.result.create_node(tokens[0], first_token, data=StorageUsageItem(), parent=self.root)
            self.result[first_token].data.add_item(size)
            for i in range(self.range_start, min(self.max_depth, len(tokens) - 1)):
                token = self.delimiter.join([self.bucket_name] + tokens[:i + 1])
                parent_token = self.delimiter.join([self.bucket_name] + tokens[:i])
                if not self.result.contains(token):
                    self.result.create_node(tokens[i], token, parent=parent_token, data=StorageUsageItem())
                self.result[token].data.add_item(size)

    def get_tree(self):
        return self.result


class StorageUsageItem(object):

    def __init__(self):
        self.size = 0
        self.count = 0

    def add_item(self, size):
        self.size += size
        self.count += 1

    def get_size(self):
        return self.size

    def get_count(self):
        return self.count
