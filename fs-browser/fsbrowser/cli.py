# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# The console script of the fsbrowser package. Its command line is in fsbrowser-cli.py, the script of the bundle that
# build.sh builds, but "fsbrowser-cli" is no module name that an entry point can import
import importlib


def main():
    importlib.import_module('fsbrowser.fsbrowser-cli').main()
