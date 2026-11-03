# [MANUAL] Advanced_filtering by node.disk

Test verifies filtering the advanced search results by `node.disk` using `=`, `!=`, and (partial) `*` matches.

**Prerequisites**:
- Several pipelines launched with different disk size values

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `node.disk = {int}` into the search field and press **Enter** |  |
| 3 | Click on a pipeline |  |
| 4 | Expand the **Instance** element | For all found pipelines, the **Disk** field value equals the value specified at step 2 |
| 5 | Return to the search page |  |
| 6 | Enter `node.disk != {int}` into the search field and press **Enter** |  |
| 7 | Click on a pipeline |  |
| 8 | Expand the **Instance** element | For all found pipelines, the **Disk** field value is not equal to the value specified at step 6 |
| 9 | Return to the search page |  |
| 10 | Enter `node.disk >= {int}` into the search field and press **Enter** |  |
| 11 | Click on a pipeline |  |
| 12 | Expand the **Instance** element | For all found pipelines, the **Disk** field value is greater than or equal to the value specified at step 10 |
| 13 | Return to the search page |  |
| 14 | Enter `node.disk <= {int}` into the search field and press **Enter** |  |
| 15 | Click on a pipeline |  |
| 16 | Expand the **Instance** element | For all found pipelines, the **Disk** field value is less than or equal to the value specified at step 14 |
| 17 | Return to the search page |  |
| 18 | Enter `node.disk > {int}` into the search field and press **Enter** |  |
| 19 | Click on a pipeline |  |
| 20 | Expand the **Instance** element | For all found pipelines, the **Disk** field value is greater than the value specified at step 18 |
| 21 | Return to the search page |  |
| 22 | Enter `node.disk < {int}` into the search field and press **Enter** |  |
| 23 | Click on a pipeline |  |
| 24 | Expand the **Instance** element | For all found pipelines, the **Disk** field value is less than the value specified at step 22 |
| 25 | Return to the search page |  |
