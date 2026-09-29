# [MANUAL] Advanced_filtering by docker.image

Test verifies filtering the advanced search results by `docker.image` using `=`, `!=`, and (partial) `*` matches.

**Prerequisites**:
- Several different completed pipelines

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `docker.image = {full docker image} ("dockerregistry.opensource.epam.com:5000/shell")` into the search field and press **Enter** |  |
| 3 | Click on a pipeline |  |
| 4 | Expand the **Instance** element | For all found pipelines, the **Docker image** field value equals the value specified at step 2 |
| 5 | Return to the search page |  |
| 6 | Enter `docker.image != {full docker image} ("dockerregistry.opensource.epam.com:5000/shell")` into the search field and press **Enter** |  |
| 7 | Click on a pipeline |  |
| 8 | Expand the **Instance** element | For all found pipelines, the **Docker image** field value is not equal to the value specified at step 6 |
| 9 | Return to the search page |  |
| 10 | Enter `docker.image = "*{part of docker image}*" ("*shel*")` into the search field and press **Enter** |  |
| 11 | Click on a pipeline |  |
| 12 | Expand the **Instance** element | For all found pipelines, the **Docker image** field value contains, as a substring, the value specified at step 10 |
| 13 | Return to the search page |  |
| 14 | Enter `docker.image != "*{part of docker image}*" ("*.31.38.143:5000/*")` into the search field and press **Enter** |  |
| 15 | Click on a pipeline |  |
| 16 | Expand the **Instance** element | For all found pipelines, the **Docker image** field value does not contain, as a substring, the value specified at step 14 |
| 17 | Return to the search page |  |
