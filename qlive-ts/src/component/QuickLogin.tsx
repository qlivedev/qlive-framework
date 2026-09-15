import config from "../config";

/**
 * A quick login account
 */
export type QuickLoginUser = {
    /**
     * login
     */
    login: string
    /**
     * Clear text password
     */
    password: string
}

/**
 * PropTypes for the QuickLogin component
 */
export type QuickLoginProps = {
    /**
     * Accounts offered as one-click switches. The framework has no accounts of its own to suggest -- an
     * application names them, which is also what keeps a real one from shipping this with anything in it.
     */
    users: QuickLoginUser[]

    /**
     * Where the switch posts to. Left at Spring Security's default login-processing URL, "/login", which is
     * what formLogin() answers to whether or not an application customizes loginPage().
     */
    url?: string
}

/**
 * Shows who a page is being served to and offers to become one of a fixed list of other users in a single
 * click, without going through the login page.
 *
 * Built for a development home page, not a production one: it round-trips a plaintext password with every
 * click, which is only ever fine for accounts nobody needs to keep secret. Posted with fetch() rather than
 * a native form submit, and on success the page reloads itself instead of following Spring Security's
 * configured success URL -- the point of a one-click switch is staying where you were and seeing it take
 * effect, not being carried off to wherever a real login lands.
 */
export default function QuickLogin({ users, url = "/login" }: QuickLoginProps)
{
    const { csrfToken, authentication } = config()

    return (
        <div className="quick-login">
            <p>
                Logged in as <strong>{ authentication!.login }</strong>
            </p>
            {
                users.map(({ login, password }) => (
                    <button
                        key={ login }
                        type="button"
                        className="btn"
                        disabled={ authentication!.login === login }
                        onClick={ async () => {
                            const body = new FormData()
                            body.set(csrfToken!.param, csrfToken!.value)
                            body.set("username", login)
                            body.set("password", password)

                            await fetch(url, { method: "POST", body, credentials: "same-origin" })
                            location.reload()
                        } }
                    >
                        { login }
                    </button>
                ))
            }
        </div>
    )
}
