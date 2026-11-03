# [MANUAL] Validation of "OR" operand

Test verifies that an `or`-combined query on `pipeline.name` returns pipelines matching either value.

**Prerequisites**:
- Several completed pipelines with different values of the pipeline field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter into the search field: `pipeline.name="{existing pipeline name}" or pipeline.name="{existing pipeline name}"` (e.g. `pipeline.name="gromacs" or pipeline.name="python_test"`) and press **Enter** | The table shows only pipelines with the pipeline-field values entered at step 2 |
