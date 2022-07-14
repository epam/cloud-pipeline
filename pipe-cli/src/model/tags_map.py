import json
import os.path
import re


class TagsMap:

    def __init__(self, masks_to_tags):
        self.masks_to_tags = masks_to_tags
        self.mask_to_pattern = {mask: re.compile(mask) for mask in masks_to_tags.keys()}

    def find_tags(self, file_item):
        return [
            tag
            for mask, tags in self.masks_to_tags.items() if self.mask_to_pattern[mask].match(file_item[1])
            for tag in tags
        ]

    @classmethod
    def read_tags_map(cls, storage_tags_schema_file_path, storage_tags_schema_preference_value=None):

        def load_storage_tags_schema_content():
            if storage_tags_schema_file_path and os.path.exists(storage_tags_schema_file_path):
                with open(storage_tags_schema_file_path) as tf:
                    return json.load(tf)
            elif storage_tags_schema_preference_value:
                return json.loads(storage_tags_schema_preference_value)
            return {}

        # TODO fix [0] - should check that dict of the mask-tag entry valid
        data = {
            file_mask: cls.__parse_tags_from_list_of_string(tags)
            for file_mask, tags in load_storage_tags_schema_content().items()
        }
        tags_map = TagsMap(data)
        return tags_map

    @classmethod
    def __parse_tags_from_list_of_string(cls, _tags):
        if not isinstance(_tags, list):
            _tags = [_tags]

        for _tag in _tags:
            if "=" not in _tag:
                raise RuntimeError("Wrong tag format, should be 'key=value', but was: " + _tag)

        return _tags
