package com.composum.sling.cpnl;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;

/**
 * a tag to build form elements with mapped action URLs
 */
public class FormTag extends UrlTag {

    private String method;
    private String enctype;
    private String charset;

    protected String getDefaultTagName() {
        return "form";
    }

    protected String getDefaultUrlAttr() {
        return "action";
    }

    public void setAction(String action) {
        setUrl(action);
    }

    protected void clear() {
        super.clear();
        enctype = null;
        method = null;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setEnctype(String enctype) {
        this.enctype = enctype;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    protected void writeAttributes (JspWriter writer) throws IOException {
        super.writeAttributes(writer);
        if (StringUtils.isNotBlank(charset)) {
            writer.write(" accept-charset=\"");
            writer.write(charset);
            writer.write("\"");
        }
        if (StringUtils.isNotBlank(enctype)) {
            writer.write(" enctype=\"");
            writer.write(enctype);
            writer.write("\"");
        }
        if (StringUtils.isNotBlank(method)) {
            writer.write(" method=\"");
            writer.write(method.toUpperCase());
            writer.write("\"");
        }
    }
}
