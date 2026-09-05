import {config} from "@quinscape/qlive-ts";

/**
 * Login form for spring security's form login.
 *
 * A plain HTML form, deliberately: the POST is what authenticates the session, and letting the browser
 * submit it means the response -- a redirect to the requested view, or back here with ?error -- is handled
 * by the browser as well. The CSRF token spring security demands for that POST arrives with the QLive
 * bootstrap, which is why this page is served through IndexPageRenderer like any other view.
 */
export default function Login() {

    const {csrfToken} = config()

    const failed = new URLSearchParams(location.search).has("error")

    return (
        // Posting to the path without the query string keeps ?error out of the next round-trip.
        <form className="login" method="post" action={location.pathname}>
            <h1>QLive Test</h1>
            {
                failed && <p className="error">Invalid user name or password</p>
            }
            <label htmlFor="username">User</label>
            <input id="username" name="username" type="text" autoComplete="username" autoFocus required/>

            <label htmlFor="password">Password</label>
            <input id="password" name="password" type="password" autoComplete="current-password" required/>

            <input type="hidden" name={csrfToken!.param} value={csrfToken!.value}/>

            <button type="submit">Log in</button>
        </form>
    )
}
