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

def assert_first_argument_contained(mocked_function, substring, assert_not=False):
    valid = False
    for call in mocked_function.call_args_list:
        args, kwargs = call
        first_arg = args[0]
        if substring in first_arg:
            valid = True
    assert assert_not ^ valid, '%scall contained %s as a first argument substring' \
                               % ('' if assert_not else 'No ',substring)


def assert_first_argument_not_contained(mocked_function, substring):
    assert_first_argument_contained(mocked_function, substring, assert_not=True)