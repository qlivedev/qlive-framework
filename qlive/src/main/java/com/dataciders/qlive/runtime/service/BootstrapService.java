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
    ///
    /// How much of it the given path needs is the service's decision, not the caller's: whether the module
    /// serving that path declares noSchema() is something only the frontend's static analysis knows, and this
    /// is where that data is read. The same lookup is what resolves the path's injections.
    ///
    /// @param csrfToken    CSRF token of the current session
    /// @param path         path within the application
    ///
    /// @return the bootstrap data, or `null` while the server cannot yet say what this path needs -- in dev,
    ///         before the Vite dev server has pushed its first static analysis snapshot. Callers turn that
    ///         into a 503, which the frontend's bootstrap fetch retries.
    QLiveBoostrap provideConfig(CsrfToken csrfToken, String path);

    /// Provides just the injection data subset for dynamic path updates.
    ///
    /// The injections are the result of actually running the queries the path's view declares with
    /// useInjection(), which is why this can be asked for again as the application navigates: the data is
    /// current as of the call, not as of the page load.
    ///
    /// @param path         path within the application
    ///
    /// @return injections by injection id, or `null` while the server cannot yet say what this path needs --
    ///         the same "not ready" {@link #provideConfig(CsrfToken, String)} answers with, for the same
    ///         reason
    Map<String, Injection> provideInjectionData(String path);
}
