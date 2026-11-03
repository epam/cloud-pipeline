# [MANUAL] Advanced_filter by non equal run.status validation

Test verifies filtering the advanced search results by `run.status` not equal to each of the run statuses `stopped`, `failure`, `success`.

**Prerequisites**:
- Several pipelines with different statuses

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `run.status!=stopped` into the search field and press **Enter** | All pipelines except those with status `stopped` are displayed |
| 3 | Clear the search field |  |
| 4 | Enter `run.status!=failure` into the search field and press **Enter** | All pipelines except those with status `failure` are displayed |
| 5 | Clear the search field |  |
| 6 | Enter `run.status!=success` into the search field and press **Enter** | All pipelines except those with status `success` are displayed |
