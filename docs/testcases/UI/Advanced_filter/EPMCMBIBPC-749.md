# [MANUAL] Advanced_filter by non equal pipeline.name validation

Test verifies filtering the advanced search results using `pipeline.name`.

**Prerequisites**:
- Several completed pipelines with different values of the pipeline field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `pipeline.name!="{full name of existing pipeline}"` into the search field and press **Enter** | The table shows all pipelines except those with the name specified at step 2 |
