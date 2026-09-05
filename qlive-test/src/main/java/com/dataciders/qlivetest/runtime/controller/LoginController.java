package com.dataciders.qlivetest.runtime.controller;

import com.dataciders.qlive.runtime.view.VitePageRenderer;
import com.dataciders.qlivetest.runtime.config.SecurityConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * <p>
 *     Serves the login page {@link SecurityConfiguration} points spring security's form login at.
 * </p>
 * <p>
 *     The login page is a Vite entry point of its own -- {@code frontend/login.html} loading
 *     {@code src/login.tsx} -- rendered by the same {@link VitePageRenderer} the application itself is
 *     served with. It therefore boots the same way, with the bootstrap data embedded here in production and
 *     fetched from {@code /api/bootstrap} in {@code vite dev}. That is what gets the CSRF token into the
 *     login form without this controller knowing anything about the form.
 * </p>
 * <p>
 *     Deliberately an application controller rather than part of QLive: the login page is the one page every
 *     application wants to look like its own, so it stays where it can simply be rewritten. Its route lives
 *     outside Vite's base, so it is neither {@code /app/**} nor one of the views below {@code src/app}.
 * </p>
 */
@Controller
public class LoginController
{
    /**
     * Entry point of the login page, as declared in the frontend's {@code build.rollupOptions.input}.
     */
    private final static String LOGIN_ENTRY_POINT = "login.html";

    private final VitePageRenderer vitePageRenderer;


    public LoginController(VitePageRenderer vitePageRenderer)
    {
        this.vitePageRenderer = vitePageRenderer;
    }


    @GetMapping(SecurityConfiguration.LOGIN_URI)
    public ResponseEntity<String> login(CsrfToken csrfToken)
    {
        return vitePageRenderer.render(LOGIN_ENTRY_POINT, SecurityConfiguration.LOGIN_URI, csrfToken);
    }
}
