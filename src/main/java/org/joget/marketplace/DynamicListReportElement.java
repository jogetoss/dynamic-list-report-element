package org.joget.marketplace;


import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormat;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.rbuilder.ReportBuilder;
import org.joget.rbuilder.api.ReportElement;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.mozilla.javascript.Scriptable;
import org.joget.rbuilder.lib.ListReportElement;

public class DynamicListReportElement extends ExtDefaultPlugin implements PropertyEditable, ReportElement, PluginWebSupport {

    private DataList cachedDataList = null;
    private DataListCollection cachedDataListData = null;
    private int colSize = 0;
    private boolean hasFormulaColumns = false;
    private boolean hasAutomatedNumber = false;
    private boolean hasFooter = false;
    private boolean hasFormatter = false;
    private Collection<DataListColumnFormat> nestedListFormatter = new ArrayList<DataListColumnFormat>();
    private Map<String, Object> footers = new HashMap<String, Object>();
    private final static String MESSAGE_PATH = "message/DynamicListReportElement";
    

    @Override
    public String getName() {
        return "DynamicListReportElement";
    }

    @Override
    public String getVersion() {
        return Activator.VERSION;
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage(getName() + ".desc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage(getName() + ".label", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/DynamicListReportElement.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-table\"></i>";
    }

    @Override
    public String render() {
        if (getPropertyString("datalistId").isEmpty() || getDataList() == null) {
return "<table style=\"width:100%; border-collapse:collapse; border:1px solid #ccc;\"><thead><tr><th style=\"border:1px solid #ccc; padding:10px;\">Column 1</th><th style=\"border:1px solid #ccc; padding:10px;\">Column 2</th></tr></thead><tbody><tr><td class=\"notice\" colspan=\"2\" style=\"border:1px solid #ccc; padding:10px; font-style:italic; color:#666; text-align:center;\">This is a sample table. It does not reflect the accurate table.</td></tr></tbody></table>";
        }

        Map model = new HashMap();
        model.put("element", this);
        model.put("dataList", getDataList());
        model.put("rows", getDataListData());
        model.put("repeatFooterRowEveryPage", getPropertyString("repeatFooterRowEveryPage"));

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/DynamicListReportElement.ftl", MESSAGE_PATH);
        return "<div id=\"e_" + getPropertyString("id")+"\" class=\"" + getPropertyString("selectorClass")+"\">" + content + "</div>";
    }

    @Override
    public boolean supportReportContainer() {
        return true;
    }

    @Override
    public String preview() {
        setProperty("elementPreview", "true");
        return render();
    }

    @Override
    public String getCSS() {
        return "";
    }

    public boolean hasFooter() {
        return hasFooter;
    }

    protected DataList getDataList() {
        if (cachedDataList == null) {

            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            DataListService dataListService = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
            DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
            DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(getPropertyString("datalistId"), appDef);
            if (datalistDefinition != null) {
                List<DataListColumn> columns = new ArrayList<DataListColumn>();
                
                if ("true".equals(getPropertyString("automatedNum"))) {
                    hasAutomatedNumber = true;
                    Map<String, Object> props = new HashMap<String, Object>();
                    props.put("headerAlignment", "dataListAlignLeft");
                    props.put("alignment", "dataListAlignLeft");

                    DataListColumn NumberingColumn = new DataListColumn("numberingColumn", "No", false);
                    NumberingColumn.setProperties(props);
                    NumberingColumn.setWidth("25px");

                    columns.add(NumberingColumn);
                    colSize++;
                }
                
                cachedDataList = dataListService.fromJson(datalistDefinition.getJson());
                if (cachedDataList.getColumns().length > 0) {
                    for (DataListColumn c : cachedDataList.getColumns()) {
                        if (isVisible(c)) {
                            columns.add(c);
                        }
                    }
                }
                cachedDataList.setColumns(columns.toArray(new DataListColumn[columns.size()]));
                
                Object footers = getProperty("footer");
                Map<String, Map> footerMap = new HashMap<String, Map>();
                if (footers != null && footers instanceof Object[] && ((Object[]) footers).length > 0) {
                    Map fMap;
                    for (Object footer : ((Object[]) footers)) {
                        fMap = (Map) footer;
                        footerMap.put((String) fMap.get("fieldid"), fMap);
                        
                    }
                }

                for (DataListColumn c : cachedDataList.getColumns()) {
                    if (isVisible(c)) {
                        if (footerMap.containsKey(c.getName())) {
                            hasFooter = true;
                            c.setProperty("footer", footerMap.get(c.getName()).get("operator").toString());
                            c.setProperty("numberFormat", footerMap.get(c.getName()).get("numberFormat").toString());
                            c.setProperty("footerlabel", footerMap.get(c.getName()).get("footerlabel").toString());
                        }
                        if (c.getFormats() != null && !c.getFormats().isEmpty()) {
                            DataListColumnFormat format = c.getFormats().iterator().next();
                            if (format != null) {
                                hasFormatter = true;
                                if ("org.joget.plugin.enterprise.NestedDatalistFormatter".equalsIgnoreCase(format.getClassName())) {
                                    if (format.getPropertyString("exportOptions").contains("pdf")) {
                                        nestedListFormatter.add(format);
                                    }
                                }
                            }
                        }
                        colSize++;
                    }
                }

                Object formulaColumns = getProperty("formulaColumns");
                if (formulaColumns != null && formulaColumns instanceof Object[] && ((Object[]) formulaColumns).length > 0) {
                    hasFormulaColumns = true;
                    List<DataListColumn> Fcolumns = new ArrayList<DataListColumn>();
                    Fcolumns.addAll(Arrays.asList(cachedDataList.getColumns()));

                    Map fcMap;
                    int c = 0;
                    for (Object fc : ((Object[]) formulaColumns)) {
                        fcMap = ((Map) fc);

                        DataListColumn formulaColumn = new DataListColumn("formulaColumn-" + c, fcMap.get("label").toString(), false);
                        formulaColumn.setProperties(fcMap);
                        formulaColumn.setWidth(fcMap.get("width").toString());

                        if (!fcMap.get("footer").toString().isEmpty()) {
                            hasFooter = true;
                        }

                        Fcolumns.add(formulaColumn);
                        c++;
                        colSize++;
                    }
                    cachedDataList.setColumns(Fcolumns.toArray(new DataListColumn[0]));
                }
            }
        }
        return cachedDataList;
    }

    protected DataListCollection getDataListData() {
        if (cachedDataListData == null) {
            boolean isPreview = false;
            if ("true".equals(getPropertyString("elementPreview"))) {
                cachedDataListData = getDataList().getRows(3, null);
                isPreview = true;
            } else {
                Object filterParamsProperty = getProperty("filterParams");
                if (filterParamsProperty != null && filterParamsProperty instanceof Object[] && ((Object[]) filterParamsProperty).length > 0) {
                    Map<String, String[]> params = getDataList().getRequestParamMap();
                    if (params == null) {
                        params = new HashMap<String, String[]>();
                    }
                    Map paramMap;
                    for (Object param : ((Object[]) filterParamsProperty)) {
                        paramMap = ((Map) param);
                        String key = paramMap.get("param").toString();
                        
                        if (params.containsKey(key)) { //to set multiple values to single parameter
                            List<String> temp = new ArrayList<String>(Arrays.asList(params.get(key)));
                            temp.add(paramMap.get("value").toString());
                            params.put(key, temp.toArray(new String[]{}));
                        } else {
                            params.put(key, new String[]{paramMap.get("value").toString()});
                        }
                    }
                    getDataList().setRequestParamMap(params);
                }

                cachedDataListData = getDataList().getRows(getDataList().getTotal(), 0);
            }
            cachedDataListData = preparedataListCollection(getDataList(), cachedDataListData, isPreview);
        }
        return cachedDataListData;
    }

    public DataListCollection preparedataListCollection(DataList dataList, DataListCollection data, boolean isPreview) {
        if (dataList != null && !data.isEmpty()) {
            if (hasFormatter || hasFormulaColumns || hasFooter || hasAutomatedNumber) {
                DataListCollection<Map<String, Object>> newData = new DataListCollection<Map<String, Object>>();
                Object value = null;
                Map<String, Object> newRow = null;
                int rowCount = 0;
                for (Object row : data) {
                    newRow = new HashMap<String, Object>();
                    for (DataListColumn c : dataList.getColumns()) {
                        if (isVisible(c)) {
                            value = DataListService.evaluateColumnValueFromRow(row, c.getName());
                            if (c.getFormats() != null && !c.getFormats().isEmpty()) {
                                for (DataListColumnFormat f : c.getFormats()) {
                                    if (f != null) {
                                        value = f.format(dataList, c, row, value);
                                        Pattern patternInput = Pattern.compile("<span class=\"nesteddl_trigger\"[^>]*>(.*?)<\\/span>");
                                        Matcher matcherInput = patternInput.matcher(value.toString());
                                        if (matcherInput.find()) {
                                            value = matcherInput.group(1);
                                        }
                                    }
                                }
                            } else if (c.getName().equalsIgnoreCase("numberingColumn")) {
                                value = rowCount+=1;
                            } else {
                                //if formula column
                                value = getFormulaValue(value, c, row);
                            }
                            calculateFooter(c, value);

                            if (value != null && value instanceof String) {
                                value = StringUtil.stripHtmlRelaxed(value.toString());
                            }
                            newRow.put(c.getName(), value);
                            newRow.put(c.getPropertyString("id"), value);
                        }
                    }
                    newData.add(newRow);
                }
                data = newData;
            }

        } else if (dataList != null && data.isEmpty() && isPreview) {
            DataListCollection<Map<String, Object>> newData = new DataListCollection<Map<String, Object>>();
            Map<String, Object> newRow = new HashMap<String, Object>();
            for (DataListColumn c : dataList.getColumns()) {
                if (isVisible(c)) {
                    newRow.put(c.getName(), "");
                }
            }
            newData.add(newRow);
            newData.add(newRow);
            newData.add(newRow);
            data = newData;
        }
        return data;
    }

    public static boolean isVisible(DataListColumn c) {
        return (c.isHidden() && "true".equalsIgnoreCase(c.getPropertyString("include_export"))) || (!c.isHidden() && !"true".equalsIgnoreCase(c.getPropertyString("exclude_export")));
    }

    public String getHeaderStyle() {
        String css = "";
        if (!getPropertyString("headerColor").isEmpty()) {
            css += "background:" + getPropertyString("headerColor") + ";";
        }
        if (!getPropertyString("headerTextColor").isEmpty()) {
            css += "color:" + getPropertyString("headerTextColor") + ";";
        }
        return css;
    }

    public String getRowExtra(Object row) {
        String html = "";
        if (!nestedListFormatter.isEmpty()) {
            for (DataListColumnFormat f : nestedListFormatter) {
                ListReportElement nl = new ListReportElement();
                nl.setProperty("datalistId", f.getPropertyString("listId"));
                if ("true".equalsIgnoreCase(f.getPropertyString("customHeaderColor"))) {
                    nl.setProperty("headerColor", f.getPropertyString("headerColor"));
                    nl.setProperty("headerTextColor", f.getPropertyString("headerFontColor"));
                }
                Object requestParamsProperty = f.getProperty("requestParams");
                if (requestParamsProperty != null && requestParamsProperty instanceof Object[]) {
                    List<Map> filters = new ArrayList<Map>();
                    for (Object param : ((Object[]) requestParamsProperty)) {
                        Map paramMap = ((Map) param);
                        Object tempValue = DataListService.evaluateColumnValueFromRow(row, paramMap.get("hrefColumn").toString());
                        paramMap.put("value", (tempValue != null) ? tempValue.toString() : paramMap.get("defaultValue").toString());
                        filters.add(paramMap);
                    }
                    nl.setProperty("filterParams", filters.toArray(new Object[0]));
                }
                nl.setProperty(ReportBuilder.IS_PREVIEW, false);
                html += "<tr style=\"padding:2px 0px 5px;\"><td colspan=\"" + colSize + "\">" + nl.render() + "</td></tr>";
            }
        }
        return html;
    }

    public String getFooter(DataListColumn c) {
        Object value = null;
        String footerlabel = null;
        String type = c.getPropertyString("footer");
        if (!type.isEmpty()) {
            switch (type) {
                case "DistinctCount":
                    Set<Object> distinct = (HashSet<Object>) footers.get(c.getName() + ":distinct");
                    if (distinct != null) {
                        value = distinct.size();
                    } else {
                        value = 0;
                    }
                    break;
                case "First":
                    value = footers.get(c.getName() + ":first");
                    break;
                case "Highest":
                    value = footers.get(c.getName() + ":max");
                    break;
                case "Lowest":
                    value = footers.get(c.getName() + ":min");
                    break;
                case "Sum":
                    value = footers.get(c.getName() + ":sum");
                    break;
                case "Average":
                    double count = (double) footers.get(c.getName() + ":count");
                    if (count > 0) {
                        value = (double) footers.get(c.getName() + ":sum") / count;
                    } else {
                        value = 0;
                    }
                    break;
                case "Count":
                    value = footers.get(c.getName() + ":count");
                    break;
                case "Variance":
                case "StandardDeviation":
                    double sum = (double) footers.get(c.getName() + ":sum");
                    double count2 = (double) footers.get(c.getName() + ":count");
                    double squares = (double) footers.get(c.getName() + ":squares");
                    if (count2 > 1) {
                        value = (squares - (double) sum * sum / count2) / (count2 - 1);
                        if ("StandardDeviation".equals(type)) {
                            value = Math.sqrt((double) value);
                        }
                    } else {
                        value = 0;
                    }
                    break;
            }
        }
        if (value != null && !c.getPropertyString("numberFormat").isEmpty()) {
            value = formatNumber(value.toString(), c.getPropertyString("numberFormat"));
        }
        if (value == null) {
            value = "";
        }
        
        //getPrefix and add back to the footer Value
        Object prefix = footers.get(c.getName() + ":prefix");
        if (prefix != null && !prefix.toString().isEmpty()) {
            String negativeBeforePrefix = (String) footers.get(c.getName() + ":negativeBeforePrefix");
            String strValue = value.toString();
            if (negativeBeforePrefix != null && strValue.startsWith("-")) {
                value = strValue.substring(0, 1) + prefix.toString() + strValue.substring(1);
            } else {
                value = prefix.toString() + strValue;
            }
        }
        //getPostfix and add back to the footer Value
        Object postfix = footers.get(c.getName() + ":postfix");
        if (postfix != null && !postfix.toString().isEmpty()) {
            value += postfix.toString();
        }
        
        if(!c.getPropertyString("footerlabel").isEmpty()){
            footerlabel = c.getPropertyString("footerlabel")+":";
        }else{
            footerlabel = "";
        }
        return StringUtil.escapeString(footerlabel + value.toString(), StringUtil.TYPE_HTML, null);
    }

    protected void calculateFooter(DataListColumn c, Object value) {
        String type = c.getPropertyString("footer");
        if (!type.isEmpty()) {
            //remove prefix & postfix
            value = removePrefixPostfix(c, value.toString());
            
            //remove thousand separator
            value = removeThousandSeparator(c, value.toString());
            
            double num = 0;
            if (!(value instanceof Number)) {
                try {
                    num = Double.parseDouble(value.toString());
                } catch (Exception e) {
                } //ignore
            } else {
                num = (Double) value;
            }

            switch (type) {
                case "DistinctCount":
                    Set<Object> distinct = null;
                    if (!footers.containsKey(c.getName() + ":distinct")) {
                        distinct = new HashSet<Object>();
                    } else {
                        distinct = (HashSet<Object>) footers.get(c.getName() + ":distinct");
                    }
                    distinct.add(value);
                    footers.put(c.getName() + ":distinct", distinct);
                    break;
                case "First":
                    if (!footers.containsKey(c.getName() + ":first")) {
                        footers.put(c.getName() + ":first", value);
                    }
                    break;
                case "Highest":
                    double max = Double.MIN_VALUE;
                    if (footers.containsKey(c.getName() + ":max")) {
                        max = (Double) footers.get(c.getName() + ":max");
                    }
                    if (num > max) {
                        footers.put(c.getName() + ":max", num);
                    }
                    break;
                case "Lowest":
                    double min = Double.MAX_VALUE;
                    if (footers.containsKey(c.getName() + ":min")) {
                        min = (Double) footers.get(c.getName() + ":min");
                    }
                    if (num < min) {
                        footers.put(c.getName() + ":min", num);
                    }
                    break;
                case "Sum":
                case "Average":
                case "Count":
                case "StandardDeviation":
                case "Variance":
                    double sum = 0;
                    double count = 0;
                    double squares = 0;
                    if (footers.containsKey(c.getName() + ":sum")) {
                        sum = (Double) footers.get(c.getName() + ":sum");
                        count = (Double) footers.get(c.getName() + ":count");
                        squares = (Double) footers.get(c.getName() + ":squares");
                    }
                    sum += num;
                    count++;
                    squares += (num * num);
                    footers.put(c.getName() + ":sum", sum);
                    footers.put(c.getName() + ":count", count);
                    footers.put(c.getName() + ":squares", squares);
                default:
                    break;
            }
        }
    }

    protected Object getFormulaValue(Object value, DataListColumn c, Object row) {
        if (!c.getPropertyString("formula").isEmpty()) {
            value = formatValue(c.getPropertyString("formula"), row);

            if (!c.getPropertyString("numberFormat").isEmpty()) {
                value = formatNumber(value.toString(), c.getPropertyString("numberFormat"));
            }
        }
        return value;
    }

     //method to set the format of the thousand separator
    protected String formatNumber(String value, String format) {
        if (value == null) {
            value = "0";
        }

        try {
            DecimalFormat decimalFormat;
            if (format.matches("([0#]\\.[0#]{2,})*0(,0+)$")) {
                // If the format matches the given regular expression, get the symbols for the current locale
                DecimalFormatSymbols symbols = new DecimalFormatSymbols();

                symbols.setDecimalSeparator(',');
                symbols.setGroupingSeparator('.');

                // Count the number of decimal places in the format
                int decimalPlaces = format.length() - format.indexOf(',') - 1;

                // Use the custom format "#,##0.00" or "#,##0.000" based on the number of decimal places
                String customFormat = "#,##0." + "0".repeat(decimalPlaces);
                decimalFormat = new DecimalFormat(customFormat, symbols);
            } else {
                // Otherwise, use the given format
                decimalFormat = new DecimalFormat(format);
            }
            return decimalFormat.format(Double.parseDouble(value));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }

        return value;
    }

    protected String formatValue(String format, Object row) {
        Map variables = new HashMap();
        format = prepareExpression(format, row, variables);
        Object result = evaluateExpression(format, variables);

        if (result != null) {
            return result.toString();
        }
        return "";
    }

    protected String prepareExpression(String expr, Object row, Map variables) {
        Pattern pattern = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");
        Matcher matcher = pattern.matcher(expr);

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = DataListService.evaluateColumnValueFromRow(row, key);
            if (value != null) {
                String newKey = key.replaceAll(StringUtil.escapeRegex("."), "__");
                if (value instanceof String) {
                    try {
                        value = Double.parseDouble(value.toString());
                    } catch (Exception e) {
                    } //ignore
                }
                variables.put(newKey, value);
                expr = expr.replaceAll(StringUtil.escapeRegex("{" + key + "}"), newKey);
            }
        }

        return expr;
    }

    protected Object evaluateExpression(String expr, Map variables) {
        org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
        Scriptable scope = cx.initStandardObjects(null);

        java.lang.Object eval;
        try {
            prepareContext(scope, variables);
            eval = cx.evaluateString(scope, expr, "", 1, null);
            return eval;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, expr);
        } finally {
            org.mozilla.javascript.Context.exit();
        }
        return null;
    }

    protected void prepareContext(Scriptable scope, Map variables)
            throws Exception {
        Iterator iter = variables.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry me = (Map.Entry) iter.next();
            String key = me.getKey().toString();
            java.lang.Object value = me.getValue();
            scope.put(key, scope, value);
        }
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String action = request.getParameter("action");
        
        if ("events".equals(action)) {
            JSONArray jsonArray = new JSONArray();
            String datalistId = request.getParameter("datalistId");

            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
            DatalistDefinition datalist = datalistDefinitionDao.loadById(datalistId, appDef);

            if (datalist != null) {
                DataListService dataListService = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
                DataList dataList = dataListService.fromJson(AppUtil.processHashVariable(datalist.getJson(), null, null, null));
                DataListFilter[] tempfilterList = dataList.getFilters();

                if (tempfilterList != null) {
                    for (DataListFilter filterList : tempfilterList) {
                        String value = filterList.getName();
                        String filterParam = dataList.getDataListEncodedParamName(DataList.PARAMETER_FILTER_PREFIX+value);

                        Map<String, String> option = new HashMap<String, String>();
                        option.put("value", filterParam);
                        option.put("label", filterParam);
                        jsonArray.put(option);
                    }
                }
            }
            
            try {
                jsonArray.write(response.getWriter());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, action);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

    }
    
    public String removePrefixPostfix(DataListColumn column, String value) {
        String prefix = (String) footers.get(column.getName() + ":prefix");
        if (prefix == null) {
            //check for prefix
            int prefixStart = 0;
            int prefixEnd = 0;
            boolean hasPrefix = false;

            //find the prefix by looping the input value until a digit number is found
            while (prefixEnd < value.length() && !Character.isDigit(value.charAt(prefixEnd))) { 
                char c = value.charAt(prefixEnd);
                
                //if negative put before prefix
                if (c == '-' && prefixEnd == 0 && prefixEnd == 0) {
                    prefixStart++;
                    prefixEnd = prefixStart;
                    continue;
                } else if (c == '-') { //stop when reach negative symbol
                    break;
                }
                
                hasPrefix = true;
                prefixEnd++;
            }
            
            if (hasPrefix && prefixEnd > 0) {
                prefix = value.substring(prefixStart, prefixEnd);
                
                if (prefixStart > 0) {
                    footers.put(column.getName() + ":negativeBeforePrefix", "true");
                }
            } else {
                prefix = "";
            }
            footers.put(column.getName() + ":prefix", prefix);
        }
        
        String negativeBeforePrefix = (String) footers.get(column.getName() + ":negativeBeforePrefix");
        if (negativeBeforePrefix == null && value.startsWith("-")) {
            footers.put(column.getName() + ":negativeBeforePrefix", "true");
        }
        
        if (!prefix.isEmpty()) {
            value = value.replaceAll(StringUtil.escapeRegex(prefix), "");
        }
        
        String postfix = (String) footers.get(column.getName() + ":postfix");
        if (postfix == null) {
            //check for postfix
            int postfixStart = value.length()-1;
            boolean hasPostfix = false;

            //find the postfix by looping the input value from last char until a digit number is found
            while (postfixStart > 0 && !Character.isDigit(value.charAt(postfixStart))) {
                hasPostfix = true;
                postfixStart--;
            }
            
            if (hasPostfix) {
                postfix = value.substring(postfixStart + 1);
            } else {
                postfix = "";
            }
            footers.put(column.getName() + ":postfix", postfix);
        }
        if (!postfix.isEmpty()) {
            value = value.replaceAll(StringUtil.escapeRegex(postfix), "");
        }
        
        return value;
    }
 
    public String removeThousandSeparator(DataListColumn column, String value) {
        // Use a regular expression to check if the string uses a comma(US) or a period(EU) as a decimal separator
        String usPattern = ".*,(?=[\\d,]*\\.\\d{2}\\b).*";
        String euPattern = ".*\\.(?=[\\d.]*\\,\\d{2}\\b).*";
        String format = null;
        //use regex to check format
        if (value.matches(usPattern)) {
            format = "US";
        } else if (value.matches(euPattern)) {
            format = "EURO";
        }
        
        if (format != null) {
            String decimalSeperator = ".";
            String thousandSeparator = ",";
            if ("EURO".equalsIgnoreCase(format)) {
                decimalSeperator = ",";
                thousandSeparator = ".";
            }
            value = value.replaceAll(StringUtil.escapeRegex(thousandSeparator), "");
            value = value.replaceAll(StringUtil.escapeRegex(decimalSeperator), ".");
        }
        return value;
    }
}
