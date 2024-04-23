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
    default_image_settings = {
        "namespace": "dockerhub"
    }
    if "" in image_config_json:
        default_image_settings = image_config_json[""]

    effective_image_name = "{}/{}".format(default_image_settings["namespace"], public_image)
    for registry, settings in image_config_json.items():
        if public_image.startswith(registry):
            effective_image_name = public_image.replace(registry, settings["namespace"])
            break

    print("{}/{}".format(ecr, effective_image_name))
