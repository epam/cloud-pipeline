# [MANUAL] Advanced_filter by partly non equal pipeline.name validation

Test verifies filtering the advanced search results using `pipeline.name`.

**Prerequisites**:
- Several completed pipelines with different values of the pipeline field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `pipeline.name!="{first part of existing pipeline name}*" (e.g. `pipeline.name!="bash*"`)` into the search field and press **Enter** | The table shows all pipelines except those whose name starts with the substring specified at step 2 (e.g. except those starting with `bash`) |
