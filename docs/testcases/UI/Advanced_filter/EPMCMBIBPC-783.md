# [MANUAL] Advanced_filtering by commit.status

Test verifies filtering the advanced search results by `commit.status` equal/not-equal to `FAILURE` and `Success`.

**Prerequisites**:
- Several pipelines with different commit statuses to the docker repository (FAILURE, SUCCESS)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `commit.status = FAILURE` into the search field and press **Enter** |  |
| 3 | Click on a pipeline | All pipelines with commit status `FAILURE` are displayed |
| 4 | Return to the search page |  |
| 5 | Enter `commit.status != FAILURE` into the search field and press **Enter** |  |
| 6 | Click on a pipeline | All pipelines with commit status `Success`, and pipelines that never committed to the docker repository, are displayed |
| 7 | Return to the search page |  |
| 8 | Enter `commit.status = Success` into the search field and press **Enter** |  |
| 9 | Click on a pipeline | All pipelines with commit status `Success` are displayed |
| 10 | Return to the search page |  |
| 11 | Enter `commit.status != Success` into the search field and press **Enter** |  |
| 12 | Click on a pipeline | All pipelines with commit status `FAILURE`, and pipelines that never committed to the docker repository, are displayed |
