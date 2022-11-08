<!DOCTYPE html>
<html>
    <body>
        <table style="width:50%">
            <thead>
                <tr class="header">
                    <th align="left">Name</th>
                    <th align="left">Size</th>
                    <th align="left">Date Modified</th>
                </tr>
            </thead>
            <tbody>
                <#list items as item>
                    <tr>
                        <td align="left"><a href="${item.path}">${item.name}</a></td>
                        <td align="left">${item.size}</td>
                        <td align="left">${item.changed}</td>
                    </tr>
                </#list>
            </tbody>
        </table>
    </body>
</html>
