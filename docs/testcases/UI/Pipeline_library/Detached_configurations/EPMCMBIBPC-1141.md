# Check pipeline config after changes in detached config

Test verifies that editing a detached configuration doesn't affect the base pipeline configuration it was created from.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case |  |
| 2 | Open the pipeline's configuration used at step 7 of the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case | All field values aren't changed and equal the ones at the prerequisites of the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case (the parameter value fields are empty) |
