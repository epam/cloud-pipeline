# [MANUAL] Advanced_filter by equal run.owner validation

Test verifies filtering the advanced search results using `run.owner`.

**Prerequisites**:
- Several completed pipelines launched by different users

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `run.owner={existing user name}` into the search field and press **Enter** | All pipelines launched by the user specified at step 2 are displayed |
