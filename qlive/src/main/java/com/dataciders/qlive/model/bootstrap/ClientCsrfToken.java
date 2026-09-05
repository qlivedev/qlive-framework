package com.dataciders.qlive.model.bootstrap;

import org.springframework.security.web.csrf.CsrfToken;

///  Encapsulates CSRF token meta information
public class ClientCsrfToken
{
    private final String param;
    private final String header;
    private final String value;

    public ClientCsrfToken(CsrfToken token)
    {
        param = token.getParameterName();
        header = token.getHeaderName();
        value = token.getToken();
    }


    ///  HTTP Parameter name to send the CSRF token as
    public String getParam()
    {
        return param;
    }


    ///  Header name to send the CSRF token as
    public String getHeader()
    {
        return header;
    }


    ///  CSRF token value
    public String getValue()
    {
        return value;
    }
}
