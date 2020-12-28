import os

import pytest

from pyfs import mkdir, rm, touch, mv


mv_test_data = [
    {
        'name': 'file',
        'from': 'file-old',
        'to': 'file-new',
        'before': {
            'file-old': ''
        },
        'after': {
            'file-new': ''
        }
    },
    {
        'name': 'file from folder to root',
        'from': 'folder/file-old',
        'to': 'file-new',
        'before': {
            'folder': {
                'file-old': ''
            }
        },
        'after': {
            'folder': {},
            'file-new': ''
        }
    },
    {
        'name': 'file from root to folder',
        'from': 'file-old',
        'to': 'folder/file-new',
        'before': {
            'file-old': '',
            'folder': {}
        },
        'after': {
            'folder': {
                'file-new': ''
            }
        }
    },
    {
        'name': 'file from folder to folder',
        'from': 'folder-old/file-old',
        'to': 'folder-new/file-new',
        'before': {
            'folder-old': {
                'file-old': ''
            },
            'folder-new': {}
        },
        'after': {
            'folder-old': {},
            'folder-new': {
                'file-new': ''
            }
        }
    },
    {
        'name': 'file from folder to subfolder',
        'from': 'folder/file-old',
        'to': 'folder/subfolder/file-new',
        'before': {
            'folder': {
                'subfolder': {},
                'file-old': ''
            }
        },
        'after': {
            'folder': {
                'subfolder': {
                    'file-new': ''
                }
            }
        }
    },
    {
        'name': 'file from subfolder to folder',
        'from': 'folder/subfolder/file-old',
        'to': 'folder/file-new',
        'before': {
            'folder': {
                'subfolder': {
                    'file-old': ''
                }
            }
        },
        'after': {
            'folder': {
                'subfolder': {},
                'file-new': ''
            }
        }
    },
    {
        'name': 'subfolder with files from folder to root',
        'from': 'folder/subfolder-old',
        'to': 'subfolder-new',
        'before': {
            'folder': {
                'subfolder-old': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {},
            'subfolder-new': {
                'file': ''
            }
        }
    }, {
        'name': 'subfolder with files from root to folder',
        'from': 'subfolder-old',
        'to': 'folder/subfolder-new',
        'before': {
            'subfolder-old': {
                'file': ''
            },
            'folder': {}
        },
        'after': {
            'folder': {
                'subfolder-new': {
                    'file': ''
                }
            },
        }
    }, {
        'name': 'subfolder with files from folder to folder',
        'from': 'folder-old/subfolder-old',
        'to': 'folder-new/subfolder-new',
        'before': {
            'folder-old': {
                'subfolder-old': {
                    'file': ''
                }
            },
            'folder-new': {}
        },
        'after': {
            'folder-old': {},
            'folder-new': {
                'subfolder-new': {
                    'file': ''
                }
            }
        }
    }
]


@pytest.mark.parametrize('config', mv_test_data, ids=lambda a: a['name'])
def test_mv(mount_path, config):
    create_dirs(mount_path, config['before'])
    move(mount_path, config['from'], config['to'])
    assert collect_dirs(mount_path) == config['after']
    clean_dirs(mount_path, config['after'])


def create_dirs(path, dirs):
    for k, v in dirs.items():
        curr_path = os.path.join(path, k)
        if isinstance(v, str):
            touch(curr_path)
        else:
            mkdir(curr_path)
            create_dirs(curr_path, v)


def move(mount_path, source, destination):
    mv(os.path.join(mount_path, source), os.path.join(mount_path, destination))


def collect_dirs(path):
    dirs = {}
    for item in os.listdir(path):
        item_path = os.path.join(path, item)
        if os.path.isfile(item_path):
            dirs[item] = ''
        if os.path.isdir(item_path):
            dirs[item] = collect_dirs(item_path)
    return dirs


def clean_dirs(path, dirs):
    for k, v in dirs.items():
        rm(os.path.join(path, k), recursive=True)
