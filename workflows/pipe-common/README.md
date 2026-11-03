# Pipe common

## Development

The proper approach is to use python virtual environment. It can be installed and activated using the following command.
Also it should be set as a IDE python interpreter.
 
pipe-common requires Python 3.10 or later; job containers run it with Python 3.12.

 ```bash
python3.12 -m venv .env
source .env/bin/activate
```

Once a virtual environment is set you can use `python` to access its Python 3, whatever python distribution is set
as default on your system.

## Testing

To run python tests you should add `workflows/pipe-common` to your `PYTHOPATH`.

```bash
cd /path/to/cloud-pipeline/workflows/pipe-common
export PYTHONPATH=$PYTHONPATH:$PWD
python -m pip install mock
python -m pytest
```
