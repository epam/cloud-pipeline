# [MANUAL] Advanced_filter by non equal run.id validation

Test verifies filtering the advanced search results using `run.id`.

**Prerequisites**:
- Several completed pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `run.id!={existing pipeline id}` into the search field and press **Enter** | The table shows pipelines except the one whose id equals the value specified at step 2 |
