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

class DescriptorRecord(object):

    def __init__(self):
        self.id = None
        self.ui = None
        self.tree_number = None
        self.name = None
        self.created_date = None
        self.revised_date = None
        self.established_date = None
        self.history_note = None
        self.public_mesh_note = None
        self.previous_indexing = set()
        self.qualifiers = set()
        self.scope_note = None
        self.annotation = None
        self.registry_number = None
        self.entry_terms = set()
        self.related_numbers = set()
        self.cas_n1_name = None
        self.pharm_action = set()
        self.see_also = set()


class QualifierRecord(object):

    def __init__(self):
        self.id = None
        self.ui = None
        self.tree_number = None
        self.name = None
        self.created_date = None
        self.revised_date = None
        self.established_date = None
        self.history_note = None
        self.online_note = None
        self.annotation = None
        self.entry_version = None
        self.abbreveation = None
        self.entry_terms = set()
