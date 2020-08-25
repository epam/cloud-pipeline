# pipefuse

Totally complete description.

## Testing

```
cd tests
export PYTHONPATH=$PWD:$PYTHONPATH

# To run tests locally
pipe storage mount -b storage mount
pytest -s -v operation

# To run tests locally
#  with different mount directory
pipe storage mount -b storage MOUNT
pytest -s -v operation --mount MOUNT

# To run single test locally
pipe storage mount -b storage mount
pytest -s -v operation/test_mkdir.py

# To run containerized tests
export API=...
export API_TOKEN=...
pytest -s -v container

# To run containerized tests
#  with temporary storages created in specific folder
export API=...
export API_TOKEN=...
pytest -s -v container --folder 6450

# To run containerized tests in parallel
export API=...
export API_TOKEN=...
pip install pytest-xdist
pytest -s -v -n 2 container
```
