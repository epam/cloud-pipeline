# pipefuse

Totally complete description.

## Testing

```
cd tests
export PYTHONPATH=$PWD:$PYTHONPATH

# To run containerized tests
pytest -s -v docker

# To run tests locally
pytest -s -v local

# To run containerized tests in parallel
pip install pytest-xdist
pytest -s -v -n 2 docker
```
