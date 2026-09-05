package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

/// Bootrap service makes sure that the client code has the necessary information about the server state / configuration
/// of the server
///
/// In production the data in embedded in the HTML document. In dev this data embedding does not happen but instead
/// /api/bootstrap provides the same data.
///
/// The boostrap service also provides the injection data for new views.
public interface BootstrapService
{
    /// Provides the bootstrap data 
    QLiveBoostrap provideConfig(CsrfToken csrfToken, String path);

    /// Provides just the injection data subset for dynamic path updates
    Map<String, Injection> provideInjectionData(String path);
}
