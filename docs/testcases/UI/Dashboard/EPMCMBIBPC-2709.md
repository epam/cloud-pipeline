# [MANUAL] DATA widget

Test verifies the DATA widget's records for an object storage, an aliased object storage, and an FS mount, and its search field.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v**, hover over **Storages**, click **Create new object storage** |  |
| 3 | Specify a valid storage path, click **Create** |  |
| 4 | Repeat step 2 |  |
| 5 | Specify a valid storage path and alias, click **Create** |  |
| 6 | Hover over **+ Create v**, hover over **Storages**, click **Create new FS mount** |  |
| 7 | Specify a valid storage path, click **Create** |  |
| 8 | Open the **Home** page |  |
| 9 | Click **Configure** in the upper-right corner |  |
| 10 | In the pop-up that appears, set the checkbox near **Data** and unset the others |  |
| 11 | Click **OK** | The **DATA** widget is displayed with at least 3 records: <li>for the storage created at step 3, containing: a "storage type" icon in front of the storage name; a storage name equal to the storage path specified at step 3; a "region" icon next to the storage name; a storage path equal to the storage path specified at step 3 <li>for the storage created at step 5, containing: a "storage type" icon in front of the storage name; a storage name equal to the alias specified at step 5; a "region" icon next to the storage name; a storage path equal to the storage path specified at step 5 <li>for the storage created at step 7, containing: a "storage type" icon in front of the storage name; a storage name equal to the storage path specified at step 7; a storage path equal to the storage path specified at step 7 |
| 12 | Click on the record with the storage created at step 3 | The storage created at step 3 opens |
| 13 | Repeat step 8 |  |
| 14 | Click on the record with the storage created at step 7 | The storage created at step 7 opens |
| 15 | Enter into the **Search storages** field the storage path from step 3 | Only the record for the storage created at step 3 is displayed |
| 16 | Clear the **Search storages** field, enter into it the alias from step 5 | Only the record for the storage created at step 5 is displayed |
| 17 | Clear the **Search storages** field |  |
