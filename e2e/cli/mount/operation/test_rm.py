import os

import pytest

from pyfs import rm
from pyio import write_dirs, read_dirs

operation_config = [
    {
        'name': 'rm file',
        'target': 'file',
        'before': {
            'file': ''
        },
        'after': {}
    },
    {
        'name': 'rm file in folder',
        'target': 'folder/file',
        'before': {
            'folder': {
                'file': ''
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm file in subfolder',
        'target': 'folder/subfolder/file',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {
                'subfolder': {}
            }
        }
    },
    {
        'name': 'rm file with space in name',
        'target': 'file name',
        'before': {
            'file name': ''
        },
        'after': {}
    },
    {
        'name': 'rm file with uppercase letters in name',
        'target': 'FILE',
        'before': {
            'FILE': ''
        },
        'after': {}
    },
    {
        'name': 'rm folder',
        'target': 'folder',
        'before': {
            'folder': {}
        },
        'after': {}
    },
    {
        'name': 'rm subfolder',
        'target': 'folder/subfolder',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm folder with subfolder',
        'target': 'folder',
        'before': {
            'folder': {
                'subfolder': {}
            }
        },
        'after': {}
    },
    {
        'name': 'rm folder with file',
        'target': 'folder',
        'before': {
            'folder': {
                'file': ''
            }
        },
        'after': {}
    },
    {
        'name': 'rm subfolder with file',
        'target': 'folder/subfolder',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {
            'folder': {}
        }
    },
    {
        'name': 'rm folder with subfolder with file',
        'target': 'folder',
        'before': {
            'folder': {
                'subfolder': {
                    'file': ''
                }
            }
        },
        'after': {}
    }
]


@pytest.fixture(scope='function', autouse=True)
def teardown_function(mount_path):
    yield
    rm(mount_path, under=True, recursive=True, force=True)


@pytest.mark.parametrize('config', operation_config, ids=lambda config: config['name'])
def test_rm(mount_path, config):
    write_dirs(mount_path, config['before'])
    target_path = os.path.join(mount_path, config['target'])
    rm(target_path, recursive=os.path.isdir(target_path))
    assert read_dirs(mount_path) == config['after']
