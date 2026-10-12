# Validation of exception handling

Test verifies that invalid JSON in `config.json` is reported as an error when launching the pipeline.

**Prerequisites**:
- Perform the [EPMCMBIBPC-380](EPMCMBIBPC-380.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline from the [EPMCMBIBPC-380](EPMCMBIBPC-380.md) case, select version |  |
| 2 | On the pipeline page select **CODE** tab |  |
| 3 | Click on `config.json` file and edit it: add some invalid JSON code, save the edited file |  |
| 4 | Click **RUN** button | A message about errors in the `config.json` file appears |
