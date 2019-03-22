class JsonParser:

    def __init__(self):
        pass

    @classmethod
    def get_required_field(cls, data, field):
        if field not in data:
            raise RuntimeError('Malformed storage json. Missing required field "{}".'.format(field))
        return data[field]

    @classmethod
    def get_optional_field(cls, data, field, default=None):
        return data[field] if field in data else default
