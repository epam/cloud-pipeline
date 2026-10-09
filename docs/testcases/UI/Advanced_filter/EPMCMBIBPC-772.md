# [MANUAL] Advanced_filtering by node.image

Test verifies filtering the advanced search results by `node.image` using `=`, `!=`, and (partial) `*` matches.

**Prerequisites**:
- Several different completed pipelines launched using different AMIs

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `node.image = {full node image} (ami-791fa316)` into the search field and press **Enter** |  |
| 3 | Click on a pipeline |  |
| 4 | Expand the **Instance** element | For all found pipelines, the **Node image** field value equals the value specified at step 2 |
| 5 | Return to the search page |  |
| 6 | Enter `node.image != {full node image} (ami-791fa316)` into the search field and press **Enter** |  |
| 7 | Click on a pipeline |  |
| 8 | Expand the **Instance** element | For all found pipelines, the **Node image** field value is not equal to the value specified at step 6 |
| 9 | Return to the search page |  |
| 10 | Enter `node.image = "*{part of node image}*" ("*-791f*")` into the search field and press **Enter** |  |
| 11 | Click on a pipeline |  |
| 12 | Expand the **Instance** element | For all found pipelines, the **Node image** field value contains, as a substring, the value specified at step 10 |
| 13 | Return to the search page |  |
| 14 | Enter `node.image != "*{part of node image}*" ("*-791f*")` into the search field and press **Enter** |  |
| 15 | Click on a pipeline |  |
| 16 | Expand the **Instance** element | For all found pipelines, the **Node image** field value does not contain, as a substring, the value specified at step 14 |
| 17 | Return to the search page |  |
