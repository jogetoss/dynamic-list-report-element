package org.joget.marketplace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.ArrayUtils;
import org.joget.apps.app.dao.BuilderDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.BuilderDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.CustomBuilderUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.rbuilder.ReportBuilder;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.util.WorkflowUtil;

public class DynamicListDownloadReportDatalistAction extends DataListActionDefault {

    protected ReportBuilder builder = null;

      private final static String MESSAGE_PATH = "message/DynamicListDownloadReportDatalistAction";

    @Override
    public String getName() {
        return "DynamicListDownloadReportDatalistAction";
    }

    @Override
    public String getVersion() {
         return Activator.VERSION;
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
        return AppUtil.readPluginResource(getClass().getName(), "/properties/DynamicListDownloadReportDatalistAction.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("DynamicListDownloadReportDatalistAction.desc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getLinkLabel() {
        return getPropertyString("label");
    }

    @Override
    public String getHref() {
        return getPropertyString("href"); //Let system to handle to post to the same page
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn"); //Let system to set the primary key column of the binder
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation"); //get confirmation from configured properties options
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();

        // check for submited rows
        try {
            // get the HTTP Response
            HttpServletResponse response = WorkflowUtil.getHttpServletResponse();

            if (rowKeys != null && rowKeys.length > 0) {
                if (rowKeys.length == 1) {
                    // generate a pdf for download
                    singlePdf(request, response, rowKeys[0], dataList);
                } else {
                    // generate a zip of all pdfs
                    multiplePdfs(request, response, rowKeys, dataList);
                }
            } else {
                singlePdf(request, response, "", dataList);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Fail to generate PDF for " + ArrayUtils.toString(rowKeys));
        }

        // return null to do nothing
        return null;
    }

    protected void singlePdf(HttpServletRequest request, HttpServletResponse response, String rowKey, DataList dataList) throws IOException, ServletException {
        if(!rowKey.equals("")){
            String fileName = getPropertyString("renameFile").equalsIgnoreCase("true") ? getPropertyString("filename") + ".pdf" : rowKey + ".pdf";
            byte[] pdf = generetaPDF(rowKey, dataList);
            writeResponse(request, response, pdf, fileName, "application/pdf");
        } else {
            String fileName = getPropertyString("renameFile").equalsIgnoreCase("true") ? getPropertyString("filename") + ".pdf" : "report.pdf";
            byte[] pdf = generetaPDF("", dataList);
            writeResponse(request, response, pdf, fileName, "application/pdf");
        }
    }

    protected void multiplePdfs(HttpServletRequest request, HttpServletResponse response, String[] rowKeys, DataList dataList) throws IOException, ServletException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(baos);

        try {
            //create pdf and put in zip
            for (String id : rowKeys) {
                byte[] pdf = generetaPDF(id, dataList);
                zip.putNextEntry(new ZipEntry(id + ".pdf"));
                zip.write(pdf);
                zip.closeEntry();
            }

            zip.finish();
            writeResponse(request, response, baos.toByteArray(), getLinkLabel() + ".zip", "application/zip");
        } finally {
            baos.close();
            zip.flush();
        }
    }

    protected byte[] generetaPDF(String id, DataList dataList) {
        
        String reportId = getPropertyString("reportId");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        BuilderDefinitionDao builderDefinitionDao = (BuilderDefinitionDao) AppUtil.getApplicationContext().getBean("builderDefinitionDao");
        BuilderDefinition builderDefinition = builderDefinitionDao.loadById(reportId, appDef);
        
        WorkflowAssignment ass = new WorkflowAssignment();
        ass.setProcessId(id);
        
        String json = "";
        Map<String, String> rparams = new HashMap<String, String>();
        rparams.put("id", id);
        rparams.put("datalistId", dataList.getId());
        rparams.put("datalistName", dataList.getName());

        if (builderDefinition != null) {
            json = builderDefinition.getJson();
            json = AppUtil.processHashVariable(json, ass, StringUtil.TYPE_JSON, null);
            DataListCollection rows = dataList.getRows();
            Object finalValue = null;
            Object reportParams = getProperty("reportParams");
            if (reportParams != null && reportParams instanceof Object[] && ((Object[]) reportParams).length > 0) {
                Map paramMap;
                for (Object param : ((Object[]) reportParams)) {
                    paramMap = ((Map) param);
                    String value = paramMap.get("value").toString();
                    Iterator i = rows.iterator();
                    while (i.hasNext()) {
                        Object r = i.next();
                        Object idValue= DataListService.evaluateColumnValueFromRow(r, "id");
                        if (idValue.toString().equals(id)){
                            finalValue = DataListService.evaluateColumnValueFromRow(r, value);
                        }    
                    }
                    rparams.put(paramMap.get("param").toString(), finalValue.toString());
                }
            }
        }
        Map<String, Object> config = new HashMap<String, Object>();
        config.put(ReportBuilder.REPORT_PARAMS, rparams);

        return (byte[]) getBuilder().getBuilderResult(json, config);
    }

    protected void writeResponse(HttpServletRequest request, HttpServletResponse response, byte[] bytes, String filename, String contentType) throws IOException, ServletException {
        OutputStream out = response.getOutputStream();
        try {
            response.setHeader("Content-Type", contentType);
            response.setHeader("Content-Disposition", "attachment; filename=" + filename + "; filename*=UTF-8''" + filename);
            response.getOutputStream().write(bytes);

            if (bytes.length > 0) {
                response.setContentLength(bytes.length);
                out.write(bytes);
            }
        } catch (IOException ex) {
            LogUtil.error(getClassName(), ex, null);
        } finally {
            out.flush();
            out.close();

            //simply foward to a 
            // request.getRequestDispatcher(filename).forward(request, response);
        }
    }

    protected ReportBuilder getBuilder() {
        if (builder == null) {
            builder = (ReportBuilder) CustomBuilderUtil.getBuilder("report");
        }
        return builder;
    }
}
