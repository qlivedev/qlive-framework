package com.dataciders.qlive.runtime.util;

import com.dataciders.qlive.model.ts.RsPackManifest;
import de.quinscape.spring.jsview.AssetProvider;
import de.quinscape.spring.jsview.JsViewException;
import de.quinscape.spring.jsview.loader.JSONResourceConverter;
import de.quinscape.spring.jsview.loader.ResourceHandle;
import de.quinscape.spring.jsview.loader.ResourceLoader;
import de.quinscape.spring.jsview.util.Util;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.List;

import static de.quinscape.spring.jsview.webpack.WebpackAssetProvider.*;

/**
 * Provides assets on the basis of the manifest.json provided by RPack and the rspack-manifest-plugin.
 */
public class RsPackAssetProvider
    implements AssetProvider
{


    public static final String JS_EXTENSION = ".js";
    public static final String MAP_EXTENSION = ".map";

    private static final String VENDOR_PREFIX = "vendor";

    private final ResourceHandle<RsPackManifest> manifestHandle;

    private final String assetPath;


    public RsPackAssetProvider(ResourceLoader resourceLoader, String manifestPath, String assetPath)
    {
        this.assetPath = assetPath;
        manifestHandle = resourceLoader.getResourceHandle(
            manifestPath,
            new JSONResourceConverter<>(
            RsPackManifest.class)
        );
    }


    @Override
    public String renderAssets(HttpServletRequest request, String entryPointName)
    {
        try
        {
            final RsPackManifest manifest = manifestHandle.getContent();

            final StringBuilder buff = new StringBuilder();
            final String contextPath = request.getContextPath();

            if (entryPointName.startsWith(RESOURCES_PREFIX))
            {
                final List<String> assets = Util.split(entryPointName.substring(RESOURCES_PREFIX.length()), ";");

                for (String asset : assets)
                {
                    renderAsset(
                        buff,
                        contextPath + asset
                    );
                }
            }
            else
            {
                for (String name : manifest.propertyNames())
                {
                    final Object property = manifest.getProperty(name);
                    if (property instanceof String value)
                    {
                        if (!name.endsWith(MAP_EXTENSION) && name.startsWith(entryPointName) || name.startsWith(VENDOR_PREFIX))
                        {
                            renderAsset(
                                buff,
                                contextPath + assetPath + value
                            );

                        }
                    }
                }
            }
            return buff.toString();
        }
        catch (IOException e)
        {
            throw new JsViewException(e);
        }
    }

    @Override
    public boolean hasEntryPoint(String entryPointName)
    {
        try
        {
            final RsPackManifest manifest = manifestHandle.getContent();
            return manifest.hasProperty(entryPointName + JS_EXTENSION);
        }
        catch (IOException e)
        {
            throw new JsViewException(e);
        }

    }
}
