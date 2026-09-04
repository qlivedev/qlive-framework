package com.dataciders.qlive.model.bootstrap;

import de.quinscape.domainql.util.JSONHolder;

import java.util.Map;

/// Encapsulates the data for a /api/bootstrap call
///
///  * QLive config - this part is constant over the lifetime of the server and is kept as a JSONHolder
///  * injected data
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


    public Map<String, Injection> getData()
    {
        return data;
    }


    public void setData(Map<String, Injection> data)
    {
        this.data = data;
    }
}
