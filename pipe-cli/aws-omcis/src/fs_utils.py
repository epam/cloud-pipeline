import os.path


def parse_local_path(path):
    parent_dir = os.path.dirname(path)
    basename = os.path.basename(path)
    if os.path.exists(path):
        if os.path.isdir(path):
            return path, None
        else:
            raise ValueError("File with path {} already exists!".format(path))
    elif os.path.isdir(parent_dir):
        return parent_dir, basename
    raise ValueError("Path {} doesn't exists!".format(parent_dir))
