# [MANUAL] Advanced_filtering by run.end date validation

Test verifies filtering the advanced search results by `run.end` using `>`, `>=`, `=`, `!=`, `<`, `<=`.

**Prerequisites**:
- Several completed pipelines started on different dates

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `run.end > {yesterday's date, format yyyy-mm-dd}` into the search field and press **Enter** | All pipelines completed after the specified date, not including that date, are displayed |
| 3 | Clear the input field |  |
| 4 | Enter `run.end >= {yesterday's date}` into the search field and press **Enter** | All pipelines completed after the specified date, including that date, are displayed |
| 5 | Clear the input field |  |
| 6 | Enter `run.end = {yesterday's date}` into the search field and press **Enter** | Only pipelines completed on that exact date are displayed |
| 7 | Clear the input field |  |
| 8 | Enter `run.end != {yesterday's date}` into the search field and press **Enter** | All pipelines completed at any time except that date are displayed |
| 9 | Clear the input field |  |
| 10 | Enter `run.end < {2017-07-01}` into the search field and press **Enter** | All pipelines completed before the specified date, not including that date, are displayed |
| 11 | Clear the input field |  |
| 12 | Enter `run.end <= {2017-07-01}` into the search field and press **Enter** | All pipelines completed before the specified date, including that date, are displayed |
