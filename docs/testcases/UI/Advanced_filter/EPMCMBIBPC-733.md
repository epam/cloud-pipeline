# [MANUAL] Advanced_filter by non equal pipeline.version validation

Test verifies filtering the advanced search results using `pipeline.version`.

**Prerequisites**:
- Several completed pipelines with different values of the version field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `pipeline.version!="{full name of existing pipeline version}"` into the search field and press **Enter** | The table shows all pipelines except those with the version specified at step 2 |
