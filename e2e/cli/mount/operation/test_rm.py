import os

import pytest

from pyfs import rm
from pyio import write_dirs, read_dirs

operation_config = [
    {
        'case': 'TC-PIPE-FUSE-16',
        'name': 'rm file',
        'target': 'file',
        'before': {
            'file': ''
        },
        'after': {}
    },
    {
        'case': 'TC-PIPE-FUSE-17',
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
        'case': 'TC-PIPE-FUSE-18',
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
        'case': 'TC-PIPE-FUSE-19',
        'name': 'rm file with space in name',
        'target': 'file name',
        'before': {
            'file name': ''
        },
        'after': {}
    },
    {
        'case': 'TC-PIPE-FUSE-20',
        'name': 'rm file with uppercase letters in name',
        'target': 'FILE',
        'before': {
            'FILE': ''
        },
        'after': {}
    },
    {
        'case': 'TC-PIPE-FUSE-21',
        'name': 'rm folder',
        'target': 'folder',
        'before': {
            'folder': {}
        },
        'after': {}
    },
    {
        'case': 'TC-PIPE-FUSE-22',
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
        'case': 'TC-PIPE-FUSE-23',
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
        'case': 'TC-PIPE-FUSE-24',
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
        'case': 'TC-PIPE-FUSE-25',
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
        'case': 'TC-PIPE-FUSE-26',
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


@pytest.mark.parametrize('config,test_case', zip(operation_config, [config['case'] for config in operation_config]),
                         ids=[config['name'] for config in operation_config])
def test_rm(mount_path, config, test_case):
    write_dirs(mount_path, config['before'])
    target_path = os.path.join(mount_path, config['target'])
    rm(target_path, recursive=os.path.isdir(target_path))
    assert read_dirs(mount_path) == config['after']
