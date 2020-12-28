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

import datetime
import uuid
from abc import ABCMeta, abstractmethod
from copy import copy
import xml.etree.cElementTree as xml
from treelib import Tree

from src.ontology_type import OntologyType
from src.record import QualifierRecord, DescriptorRecord


def get_parser(ontology_type):
    if ontology_type == OntologyType.DESC:
        return DescriptorParser()
    if ontology_type == OntologyType.QUAL:
        return QualifierParser()
    raise RuntimeError("Unsupported ontology type '%s'" % ontology_type)


class MeshParser(object):
    __metaclass__ = ABCMeta

    def __init__(self):
        self.tree = Tree()
        self.root_id = str(uuid.uuid4()).replace("-", "")
        self.root = self.tree.create_node(self.root_id, self.root_id, data=None)
        self.record_tag = self.get_record_tag()
        self.ui_tag = self.get_ui_tag()
        self.name_tag = self.get_name_tag()

    @abstractmethod
    def get_record_tag(self):
        pass

    @abstractmethod
    def get_ui_tag(self):
        pass

    @abstractmethod
    def get_name_tag(self):
        pass

    @abstractmethod
    def get_ontology_type(self):
        pass

    @abstractmethod
    def fill_record(self, record):
        pass

    def parse(self, path):
        content = xml.parse(path)
        for record in content.getroot().iter(self.record_tag):
            result_record = self.fill_record(record)
            tree_number_list = record.find('TreeNumberList')
            if tree_number_list is None:
                # in case if ontology is not specified in tree structure - put it into root
                node_id = str(uuid.uuid4()).replace("-", "")
                self.tree.create_node(node_id, node_id, parent=self.root_id, data=result_record)
                continue
            for tree_number in tree_number_list:
                result_record.tree_number = tree_number.text
                self.add_record(copy(result_record))
        return self.tree, self.root_id

    def add_record(self, record):
        tree_coordinate = record.tree_number
        parsed_coordinate = str(tree_coordinate).split(".")
        if len(parsed_coordinate) == 1:
            if self.tree.get_node(tree_coordinate) is None:
                self.tree.create_node(tree_coordinate, tree_coordinate, parent=self.root_id, data=record)
            else:
                self.tree[tree_coordinate].data = record
        else:
            self.create_parent_tree(parsed_coordinate[:-1])
            if self.tree.get_node(tree_coordinate) is None:
                self.tree.create_node(tree_coordinate, tree_coordinate, parent=".".join(parsed_coordinate[:-1]),
                                      data=record)
            else:
                self.tree[tree_coordinate].data = record

    def create_parent_tree(self, parsed_coordinate):
        coordinate = ".".join(parsed_coordinate)
        if self.tree.get_node(coordinate) is not None:
            return
        if len(parsed_coordinate) == 1:
            self.tree.create_node(coordinate, coordinate, parent=self.root_id, data=None)
            return
        self.create_parent_tree(parsed_coordinate[:-1])
        self.tree.create_node(coordinate, coordinate, parent=".".join(parsed_coordinate[:-1]), data=None)

    @staticmethod
    def find(record, tag):
        result = record.find(tag)
        return None if result is None else result.text.strip()

    @staticmethod
    def find_level_2(record, tag_1, tag_2):
        result = record.find(tag_1)
        if result is None:
            return None
        result = result.find(tag_2)
        return None if result is None else result.text.strip()

    @staticmethod
    def find_date(record, tag):
        record = record.find(tag)
        if record is None:
            return None
        year = record.find('Year').text
        month = record.find('Month').text
        day = record.find('Day').text
        if year is None or month is None or day is None:
            return None
        return datetime.date(int(year), int(month), int(day)).strftime("%Y/%m/%d")


class QualifierParser(MeshParser):

    def __init__(self):
        super(MeshParser, self).__init__()
        self.tree = Tree()
        self.root_id = str(uuid.uuid4()).replace("-", "")
        self.root = self.tree.create_node(self.root_id, self.root_id, data=None)
        self.record_tag = self.get_record_tag()
        self.ui_tag = self.get_ui_tag()
        self.name_tag = self.get_name_tag()

    def get_record_tag(self):
        return 'QualifierRecord'

    def get_ui_tag(self):
        return 'QualifierUI'

    def get_name_tag(self):
        return 'QualifierName'

    def get_ontology_type(self):
        return OntologyType.QUAL

    def fill_record(self, record):
        result = QualifierRecord()
        result.ui = self.find(record, self.ui_tag)
        result.name = self.find_level_2(record, self.name_tag, 'String')
        result.annotation = self.find(record, 'Annotation')
        result.history_note = self.find(record, 'HistoryNote')
        result.online_note = self.find(record, 'OnlineNote')
        result.created_date = self.find_date(record, 'DateCreated')
        result.established_date = self.find_date(record, 'DateEstablished')
        result.revised_date = self.find_date(record, 'DateRevised')
        concept_list = record.find('ConceptList')
        if concept_list is None:
            return result
        for concept in concept_list.iter('Concept'):
            concept_name = self.find_level_2(concept, 'ConceptName', 'String')
            term_list = concept.find('TermList')
            if term_list is None:
                continue
            if concept_name == result.name:
                for term in term_list.iter('Term'):
                    result.abbreveation = self.find(term, 'Abbreviation')
                    result.entry_version = self.find(term, 'EntryVersion')
            else:
                for term in term_list.iter('Term'):
                    result.entry_terms.add(self.find(term, 'String'))
        return result


class DescriptorParser(MeshParser):

    def __init__(self):
        super(MeshParser, self).__init__()
        self.tree = Tree()
        self.root_id = str(uuid.uuid4()).replace("-", "")
        self.root = self.tree.create_node(self.root_id, self.root_id, data=None)
        self.record_tag = self.get_record_tag()
        self.ui_tag = self.get_ui_tag()
        self.name_tag = self.get_name_tag()

    def get_record_tag(self):
        return 'DescriptorRecord'

    def get_ui_tag(self):
        return 'DescriptorUI'

    def get_name_tag(self):
        return 'DescriptorName'

    def get_ontology_type(self):
        return OntologyType.DESC

    def fill_record(self, record):
        result = DescriptorRecord()
        result.ui = self.find(record, self.ui_tag)
        result.name = self.find_level_2(record, self.name_tag, 'String')
        result.history_note = self.find(record, 'HistoryNote')
        result.public_mesh_note = self.find(record, 'PublicMeSHNote')
        result.annotation = self.find(record, 'Annotation')
        result.created_date = self.find_date(record, 'DateCreated')
        result.established_date = self.find_date(record, 'DateEstablished')
        result.revised_date = self.find_date(record, 'DateRevised')
        result.previous_indexing = self.fill_previous_indexing(record)
        result.pharm_action = self.fill_pharm_actions(record)
        result.see_also = self.fill_see_also(record)
        concept_list = record.find('ConceptList')
        if concept_list is not None:
            for concept in concept_list.iter('Concept'):
                related_number_list = concept.find('RelatedRegistryNumberList')
                if related_number_list is not None:
                    for related_number in related_number_list.iter('RelatedRegistryNumber'):
                        result.related_numbers.add(related_number.text)

                concept_name = self.find_level_2(concept, 'ConceptName', 'String')
                if concept_name == result.name:
                    result.scope_note = self.find(concept, 'ScopeNote')
                    result.registry_number = self.find(concept, 'RegistryNumber')
                    result.cas_n1_name = self.find(concept, 'CASN1Name')
                term_list = concept.find('TermList')
                if term_list is None:
                    continue
                for term in term_list.iter('Term'):
                    term_name = self.find(term, 'String')
                    if term_name != result.name:
                        result.entry_terms.add(term_name)
        result.qualifiers = self.fill_qualifiers(record)
        return result

    def fill_qualifiers(self, record):
        result = set()
        qualifiers_list = record.find('AllowableQualifiersList')
        if qualifiers_list is None:
            return result
        for allowable_qualifier in qualifiers_list.iter('AllowableQualifier'):
            qualifier = allowable_qualifier.find('QualifierReferredTo')
            if qualifier is None:
                continue
            result.add(self.find(qualifier, 'QualifierUI'))
        return result

    def fill_previous_indexing(self, record):
        result = set()
        previous_indexing_list = record.find('PreviousIndexingList')
        if previous_indexing_list is None:
            return result
        for previous_index in previous_indexing_list.iter('PreviousIndexing'):
            result.add(previous_index.text)
        return result

    def fill_pharm_actions(self, record):
        result = set()
        pharm_action_list = record.find('PharmacologicalActionList')
        if pharm_action_list is None:
            return result
        for pharm_action in pharm_action_list.iter('PharmacologicalAction'):
            result.add(self.find_level_2(pharm_action, 'DescriptorReferredTo', 'DescriptorUI'))
        return result

    def fill_see_also(self, record):
        result = set()
        see_related_list = record.find('SeeRelatedList')
        if see_related_list is None:
            return result
        for related_descriptor in see_related_list.iter('SeeRelatedDescriptor'):
            descriptor = related_descriptor.find('DescriptorReferredTo')
            if descriptor is None:
                continue
            result.add(self.find(descriptor, 'DescriptorUI'))
        return result

