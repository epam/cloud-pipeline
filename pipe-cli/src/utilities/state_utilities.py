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

import click

OK_COLOR = 'green'
FAIL_COLOR = 'red'
WARN_COLOR = 'yellow'
PROGRESS_COLOR = 'blue'

states = {
    'running': PROGRESS_COLOR,
    'scheduled': PROGRESS_COLOR,
    'in progress': PROGRESS_COLOR,
    'success': OK_COLOR,
    'completed': OK_COLOR,
    'complete': OK_COLOR,
    'succeeded': OK_COLOR,
    'done': OK_COLOR,
    'finished': OK_COLOR,
    'pending': WARN_COLOR,
    'waiting': WARN_COLOR,
    'stopped': WARN_COLOR,
    'failure': FAIL_COLOR,
    'failed': FAIL_COLOR,
    'timeout': FAIL_COLOR,
    'terminated': FAIL_COLOR,
    'terminating': FAIL_COLOR
}

def color_state(state):
    state_lower = state.lower()
    try:
        color = states[state_lower]
        return click.style(state, fg=color)
    except:
        return state
