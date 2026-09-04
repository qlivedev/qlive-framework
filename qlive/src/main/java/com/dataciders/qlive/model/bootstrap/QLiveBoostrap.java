package com.dataciders.qlive.model.bootstrap;

import de.quinscape.domainql.util.JSONHolder;

import java.util.Map;

/// Encapsulates the data for a /api/bootstrap call or to be embedded in the HTML document.
///
///  * QLive config - {@link QLiveConfig} wrapped in a JSONHolder
///  * injected data - Prepared data for the starting view based on static analysis of TypeScript sources.
///
public class QLiveBoostrap
{
    private JSONHolder config;

    private Map<String, Injection> data;


    /**
     * QLive system config.
     */
    public JSONHolder getConfig()
    {
        return config;
    }


    public void setConfig(JSONHolder config)
    {
        this.config = config;
    }


    /**
     * Injection data map
     */
    public Map<String, Injection> getData()
    {
        return data;
    }


    public void setData(Map<String, Injection> data)
    {
        this.data = data;
    }
}
