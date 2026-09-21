package io.github.qlivedev.model.bootstrap;

import io.github.qlivedev.runtime.auth.AppAuthentication;
import io.github.qlivedev.graphql.util.JSONHolder;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

/// Encapsulates the data for a /api/bootstrap call or to be embedded in the HTML document.
///
///  * QLive config - {@link QLiveConfig} wrapped in a JSONHolder
///  * injected data - Prepared data for the starting view based on static analysis of TypeScript sources.
///  * who is asking - {@link AppAuthentication}, which is per user and so cannot live in the config, that
///    being rendered once per module and handed to everyone.
///
public class QLiveBoostrap
{
    private JSONHolder config;

    private ClientCsrfToken csrfToken;

    private Map<String, Injection> data;

    private AppAuthentication authentication;


    /// QLive system config.
    ///
    /// We keep it wrapped in a JSONHolder to generate the JSON only once. 
    public JSONHolder getConfig()
    {
        return config;
    }


    public void setConfig(JSONHolder config)
    {
        this.config = config;
    }


    /// Returns CSRF Token meta information.
    public ClientCsrfToken getCsrfToken()
    {
        return csrfToken;
    }


    public void setCsrfToken(ClientCsrfToken csrfToken)
    {
        this.csrfToken = csrfToken;
    }


    /// Injection data map
    public Map<String, Injection> getData()
    {
        return data;
    }


    public void setData(Map<String, Injection> data)
    {
        this.data = data;
    }


    /// Who the page is being served to: login, roles and id, the last of which is what lets a client tell
    /// somebody else's write from its own without asking.
    ///
    /// Anonymous is an answer and not an absence, so this is never null.
    public AppAuthentication getAuthentication()
    {
        return authentication;
    }


    public void setAuthentication(AppAuthentication authentication)
    {
        this.authentication = authentication;
    }

}
