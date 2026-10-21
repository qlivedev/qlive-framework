import { config, i18n } from "@qlivedev/qlive-ts";

/**
 * Texts in the locale the page is served in.
 *
 * The server picks it from the locales `qlive.i18n.locales` offers, by the browser's language settings, and sends
 * the translations from `app_translation` along with the page. Switch the browser to German to see them change:
 * qlive-test seeds German for these texts, for Foo's fields and for some of QLive's own.
 *
 * A text without translation shows as its tag in brackets, followed by its arguments.
 */
export default function I18n()
{
    return (
        <div>
            <h1>Translation</h1>
            <dl>
                <dt>Locale</dt>
                <dd>{ config().locale }</dd>
                <dt>Translated, with an argument</dt>
                <dd>{ i18n("Welcome {0}", "userA") }</dd>
                <dt>A field name</dt>
                <dd>{ i18n("Foo.num") }</dd>
                <dt>Not translated</dt>
                <dd>{ i18n("Nobody translated {0} yet", "this") }</dd>
            </dl>
        </div>
    );
}
