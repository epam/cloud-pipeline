# Edit bucket STS duration

Test verifies editing the bucket's Short-Term Storage duration together with the LTS duration.

**Prerequisites**:
- Perform [EPMCMBIBPC-470](EPMCMBIBPC-470.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Specify a natural number into the **STS duration** field |  |
| 2 | Specify a natural number into the **LTS duration** field |  |
| 3 | Click **Save** button | <li> the value specified at step 1, with the ending `days`, is displayed in the **Short-Term Storage duration** field in the upper left corner of the storage page <li> the value specified at step 2, with the ending `days`, is displayed in the **Long-Term Storage duration** field in the upper left corner of the storage page |
