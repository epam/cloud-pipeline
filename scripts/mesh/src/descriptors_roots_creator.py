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

from src.api import API
from src.ontology_type import OntologyType


def prepare_to_query(name, group):
    return {
        "name": name,
        "externalId": group,
        "type": str(OntologyType.DESC).upper()
    }


def get_roots():
    roots = list()
    roots.append(prepare_to_query("Anatomy", "A"))
    roots.append(prepare_to_query("Organisms", "B"))
    roots.append(prepare_to_query("Diseases", "C"))
    roots.append(prepare_to_query("Chemicals and Drugs", "D"))
    roots.append(prepare_to_query("Analytical, Diagnostic and Therapeutic Techniques, and Equipment", "E"))
    roots.append(prepare_to_query("Psychiatry and Psychology", "F"))
    roots.append(prepare_to_query("Phenomena and Processes", "G"))
    roots.append(prepare_to_query("Disciplines and Occupations", "H"))
    roots.append(prepare_to_query("Anthropology, Education, Sociology, and Social Phenomena", "I"))
    roots.append(prepare_to_query("Technology, Industry, and Agriculture", "J"))
    roots.append(prepare_to_query("Humanities", "K"))
    roots.append(prepare_to_query("Information Science", "L"))
    roots.append(prepare_to_query("Named Groups", "M"))
    roots.append(prepare_to_query("Health Care", "N"))
    roots.append(prepare_to_query("Publication Characteristics", "V"))
    roots.append(prepare_to_query("Geographicals", "Z"))
    return roots


def create_roots():
    api = API()
    roots = list()
    for root in get_roots():
        roots.append(api.create_ontology(root))
    return roots


def main():
    create_roots()


if __name__ == "__main__":
    main()
