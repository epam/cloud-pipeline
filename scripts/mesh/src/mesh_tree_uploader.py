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

import sys
from abc import ABCMeta, abstractmethod

from src.api import API
from src.descriptors_roots_creator import create_roots
from src.ontology_type import OntologyType


def get_uploader(ontology_type, tree, root_id):
    if ontology_type == OntologyType.DESC:
        return DescriptorsTreeUploader(tree, root_id)
    if ontology_type == OntologyType.QUAL:
        return QualifiersTreeUploader(tree, root_id)
    raise RuntimeError("Unsupported ontology type '%s'" % ontology_type)


class MeshTreeUploader(object):
    __metaclass__ = ABCMeta

    def __init__(self, tree, root_id, ontology_type):
        self.api = API()
        self.tree = tree
        self.root_id = root_id
        self.ontology_type = ontology_type

    @abstractmethod
    def upload_tree(self):
        pass

    @abstractmethod
    def convert_to_ontology(self, ontology, parent_id):
        pass

    def save_children(self, children, parent_id):
        if children is None or len(children) == 0:
            return
        for child in children:
            ontology = self.convert_to_ontology(child.data, parent_id)
            created_id = self.create_or_update_ontology(ontology, parent_id)
            self.save_children(self.tree.children(child.data.tree_number), created_id)

    def create_or_update_ontology(self, ontology, parent_id):
        external_id = ontology['externalId']
        try:
            loaded = self.api.get_by_external_id(external_id, parent_id)
            if not loaded:
                return self.api.create_ontology(ontology)['id']
            else:
                self.api.update(ontology, loaded['id'])
                return loaded['id']
        except KeyboardInterrupt:
            print("Program stopped at ontology with external id '%s' and parent '%s'" % (external_id, parent_id))
            sys.exit(1)
        except Exception as e:
            print("Failed to update/create ontology by external id '%s' and parent '%s'" % (external_id, parent_id))
            raise e


class QualifiersTreeUploader(MeshTreeUploader):

    def __init__(self, tree, root_id):
        super(QualifiersTreeUploader, self).__init__(tree, root_id, OntologyType.QUAL)
        self.api = API()
        self.tree = tree
        self.root_id = root_id
        self.ontology_type = OntologyType.QUAL

    def convert_to_ontology(self, qualifier, parent_id):
        attributes = {}
        if qualifier.annotation is not None:
            attributes["Annotation"] = qualifier.annotation
        if qualifier.online_note is not None:
            attributes["Online Note"] = qualifier.online_note
        if qualifier.history_note is not None:
            attributes["History Note"] = qualifier.history_note
        if qualifier.established_date is not None:
            attributes["Date Established"] = qualifier.established_date
        if qualifier.created_date is not None:
            attributes["Date of Entry"] = qualifier.created_date
        if qualifier.revised_date is not None:
            attributes["Revision Date"] = qualifier.revised_date
        if qualifier.entry_version is not None:
            attributes["Entry Version"] = qualifier.entry_version
        if qualifier.abbreveation is not None:
            attributes["Abbreviation"] = qualifier.abbreveation
        if len(qualifier.entry_terms) != 0:
            attributes["Entry Term(s)"] = ",".join(qualifier.entry_terms)
        ontology = {
            "name": qualifier.name,
            "externalId": qualifier.ui,
            "type": "QUAL",
            "attributes": attributes
        }
        if parent_id:
            ontology['parent'] = {
                "id": parent_id
            }
        return ontology

    def upload_tree(self):
        for root_record in self.tree.children(self.root_id):
            ontology = self.convert_to_ontology(root_record.data, None)
            created_id = self.create_or_update_ontology(ontology, None)
            if root_record.data.tree_number is not None:
                self.save_children(self.tree.children(root_record.data.tree_number), created_id)


class DescriptorsTreeUploader(MeshTreeUploader):

    def __init__(self, tree, root_id):
        super(DescriptorsTreeUploader, self).__init__(tree, root_id, OntologyType.DESC)
        self.api = API()
        self.tree = tree
        self.root_id = root_id
        self.ontology_type = OntologyType.DESC

    def convert_to_ontology(self, descriptor, parent_id):
        attributes = {}
        if descriptor.public_mesh_note is not None:
            attributes["Public MeSH Note"] = descriptor.public_mesh_note
        if descriptor.history_note is not None:
            attributes["History Note"] = descriptor.history_note
        if descriptor.established_date is not None:
            attributes["Date Established"] = descriptor.established_date
        if descriptor.created_date is not None:
            attributes["Date of Entry"] = descriptor.created_date
        if descriptor.revised_date is not None:
            attributes["Revision Date"] = descriptor.revised_date
        if len(descriptor.previous_indexing) != 0:
            attributes["Previous Indexing"] = ",".join(descriptor.previous_indexing)
        if descriptor.scope_note is not None:
            attributes["Scope Note"] = descriptor.scope_note
        if len(descriptor.entry_terms) != 0:
            attributes["Entry Term(s)"] = ",".join(descriptor.entry_terms)
        if len(descriptor.qualifiers) != 0:
            attributes["Qualifiers"] = ",".join(descriptor.qualifiers)
        if descriptor.annotation is not None:
            attributes["Annotation"] = descriptor.annotation
        if descriptor.registry_number is not None:
            attributes["Registry Number"] = descriptor.registry_number
        if len(descriptor.related_numbers) != 0:
            attributes["Related Numbers"] = ",".join(descriptor.related_numbers)
        if descriptor.cas_n1_name is not None:
            attributes["CAS Type 1 Name"] = descriptor.cas_n1_name
        if len(descriptor.pharm_action) != 0:
            attributes["Pharm Action"] = ",".join(descriptor.pharm_action)
        if len(descriptor.see_also) is not None:
            attributes["See Also"] = ",".join(descriptor.see_also)
        ontology = {
            "name": descriptor.name,
            "externalId": descriptor.ui,
            "type": "DESC",
            "attributes": attributes
        }
        if parent_id:
            ontology['parent'] = {
                "id": parent_id
            }
        return ontology

    def upload_tree(self):
        roots = self.get_roots()
        for root_record in self.tree.children(self.root_id):
            coordinate = root_record.data.tree_number
            parent_id = self.get_parent_root(coordinate, roots)
            ontology = self.convert_to_ontology(root_record.data, parent_id)
            created_id = self.create_or_update_ontology(ontology, parent_id)
            if coordinate is not None:
                self.save_children(self.tree.children(coordinate), created_id)

    def get_roots(self):
        roots = list()
        for stored_root in self.api.get_roots("desc"):
            group = stored_root['externalId']
            if group is not None and len(group) == 1:
                roots.append(stored_root)
        if len(roots) == 0:
            roots = create_roots()
        return roots

    @staticmethod
    def get_parent_root(coordinate, roots):
        if not coordinate:
            return None
        for stored_root in roots:
            group = stored_root['externalId']
            if coordinate.startswith(group):
                return stored_root['id']
        return None
