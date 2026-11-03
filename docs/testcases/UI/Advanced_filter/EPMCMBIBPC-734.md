# [MANUAL] Advanced_filter by partly equal pipeline.version validation

Test verifies filtering the advanced search results using `pipeline.version`.

**Prerequisites**:
- Several completed pipelines with different values of the version field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `pipeline.version="{first part of name of existing pipeline version}*" (e.g. `pipeline.version="draft-42*"`)` into the search field and press **Enter** | The table shows all pipelines whose version starts with the substring specified at step 2 (e.g. shows all pipelines whose version starts with `draft-42`) |
