# [MANUAL] Advanced_filter by equal parent.id validation

Test verifies filtering the advanced search results using `parent.id`.

**Prerequisites**:
- Several completed pipelines with a non-empty "Parent run" field

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `parent.id={existing pipeline id}` into the search field and press **Enter** | Only pipelines whose **Parent run** field equals the value entered at step 2 are displayed |
