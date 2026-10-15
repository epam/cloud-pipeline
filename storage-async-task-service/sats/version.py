# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

# A module-level name is the Python way of declaring a constant: there is no `final`, the name is
# simply never reassigned. The leading and trailing double underscores mark it as a name the language
# itself knows about - `pip` and `setup.py` read `__version__` from here.
#
# The gradle build rewrites this line on every assembly, exactly as it does for the lifecycle service,
# so do not expect the committed value to match a built artefact.
__version__ = '0.1.0'
