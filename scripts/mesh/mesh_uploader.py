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

import argparse

from src.mesh_file_manager import MeshStructureFileManager
from src.mesh_parser import get_parser
from src.mesh_tree_uploader import get_uploader
from src.ontology_type import OntologyType


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", type=str, required=True)
    parser.add_argument("--type", type=str, required=True)
    parser.add_argument("--tmp_path", type=str, required=False)

    args = parser.parse_args()
    url_path = args.url
    tmp_path = args.tmp_path
    ontology_type = args.type

    if ontology_type not in OntologyType.get_allowed():
        raise RuntimeError("Unsupported ontology type '%s'. Allowed types: %s" %
                           (ontology_type, ", ".join(OntologyType.get_allowed())))

    file_manager = MeshStructureFileManager(tmp_path, ontology_type)
    try:
        path = file_manager.download(url_path)
        print("Mesh structure successfully downloaded to path '%s'" % path)
        tree, root_id = get_parser(ontology_type).parse(path)
        print("Mesh structure successfully parsed. Found '%d' records" % len(tree.nodes))
        get_uploader(ontology_type, tree, root_id).upload_tree()
        print("Mesh structure successfully uploaded!")
    except Exception as e:
        file_manager.delete()
        raise e
    file_manager.delete()


if __name__ == "__main__":
    main()
