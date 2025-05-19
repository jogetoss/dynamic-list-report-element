<div class="list_reportelement_${dataList.id!}">
    <table class="border-table" cellspacing="0" style="width:100%;border-collapse: collapse; -fs-table-paginate: paginate; border-spacing: 0;">
        <thead style="${element.headerStyle!}">
            <tr>
            <#list dataList.columns as column>
                <#if element.isVisible(column) >
                    <#assign style = "">
                    <#if column.properties.headerAlignment! == "dataListAlignRight">
                        <#assign style = style + "text-align:right;">
                    <#elseif column.properties.headerAlignment! == "dataListAlignCenter">
                        <#assign style = style + "text-align:center;">
                    <#else>
                        <#assign style = style + "text-align:left;">
                    </#if>    
                    <th class="column_${column.name!}" style="${style!}${column.style!}">${column.label!?html}</th>
                </#if>
            </#list>
            </tr>
        </thead>
        <tbody>
            <#list rows as row>
                <tr>
                    <#list dataList.columns as column>
                        <#assign style = "">
                        <#if column.properties.alignment! == "dataListAlignRight">
                            <#assign style = style + "text-align:right;">
                        <#elseif column.properties.alignment! == "dataListAlignCenter">
                            <#assign style = style + "text-align:center;">
                        <#else>
                            <#assign style = style + "text-align:left;">
                        </#if>  
                        <#if row[column.getPropertyString("id")]??>
                            <td class="column_${column.name!}" style="${style!}${column.style!}">${row[column.getPropertyString("id")]!}</td>
                        <#else>
                            <td class="column_${column.name!}" style="${style!}${column.style!}">${row[column.name]!}</td>                   
                        </#if>
                    </#list>
                </tr>
                ${element.getRowExtra(row)}
            </#list>
        </tbody>
        <#if element.hasFooter() >
            <tfoot style="font-weight:bold; <#if repeatFooterRowEveryPage! != "true">display:table-row-group;</#if>">
                <tr>
                    <#assign colspan = 0>
                    <#list dataList.columns as column>
                        <#if colspan != -1 && column.properties.footer! == "">
                            <#assign colspan = colspan + 1>
                        <#else>
                            <#if colspan gt 0>
                                <td class="footerRowlabel" style="text-align:right;" colspan="${colspan}">${element.properties.footerRowlabel!}</td>
                                <#assign colspan = -1>
                            </#if>
                            <#assign style = "">
                            <#if column.properties.alignment! == "dataListAlignRight">
                                <#assign style = style + "text-align:right;">
                            <#elseif column.properties.alignment! == "dataListAlignCenter">
                                <#assign style = style + "text-align:center;">
                            <#else>
                                <#assign style = style + "text-align:left;">
                            </#if>
                            <td class="column_${column.name!}" style="${style!}${column.style!}">${element.getFooter(column)}</td>
                        </#if>
                    </#list>
                </tr>
            </tfoot>    
        </#if>  
    </table>
</div>