# Pipe common

## Development

The proper approach is to use python virtual environment. It can be installed and activated using the following command.
Also it should be set as a IDE python interpreter.
 
 ```bash
python2 -m pip install virtualenv
python2 -m virtualenv .env
source .env/bin/activate
```

Once a virtual environment is set you can use `python` to access `python2` whatever python distribution is set 
as default on your system.

## Testing

To run python tests you should add `workflows/pipe-common` to your `PYTHOPATH`.

```bash
export PYTHONPATH=$PYTHONPATH:/path/to/cloud-pipeline/workflows/pipe-common
cd /path/to/cloud-pipeline/workflows/pipe-common
python -m pip install mock
python -m pytest
```
