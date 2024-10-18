# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import json

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--image", type=str, required=True,
                        help="Docker image name for which new name should be generated")
    parser.add_argument("-e", "--ecr", type=str, required=True,
                        help="Private ECR registry where image will be pushed.")
    parser.add_argument("-c", "--images-config", type=str, required=True, help="")

    args = parser.parse_args()

    ecr = args.ecr
    public_image = args.image
    images_config = args.images_config
    with open(images_config, "r") as images_config_file:
        image_config_json = json.load(images_config_file)

    default_image_settings = image_config_json.pop("")
    if not default_image_settings:
        default_image_settings = {
            "namespace": "dockerhub"
        }

    effective_image_name = "{}/{}".format(default_image_settings["namespace"], public_image)
    for registry, settings in image_config_json.items():
        if public_image.startswith(registry):
            effective_image_name = public_image.replace(registry, settings["namespace"])
            break

    print("{}/{}".format(ecr, effective_image_name))
