# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipeline.api import PipelineAPI
import argparse
from slm.src.application_mode import ApplicationModeRunner
from slm.src.storage_lifecycle_manager import StorageLifecycleManager


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", default="single", choices=['single', 'demon'])
    parser.add_argument("--api")
    parser.add_argument("--log-dir", default="/var/log/")

    args = parser.parse_args()
    api = PipelineAPI(args.api, args.log_dir)
    ApplicationModeRunner.get_application_runner(StorageLifecycleManager(api), args.mode).run()


if __name__ == '__main__':
    main()
