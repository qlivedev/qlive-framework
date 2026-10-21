---
title: Translate an application
description: The translation table, the locales an application offers, and which texts can be translated.
sidebar:
  order: 16
---

Every text QLive renders goes through [`i18n()`](/qlive-framework/api/startup-and-config/#i18n),
and so does every text of yours that you pass through it. Translating an
application means storing what those texts read as in each language it
offers. Nothing else has to be registered: which texts there are is
something the server already knows.

## The translation table

The translations live in a table of the framework's, by a fixed name:

```sql
CREATE TABLE public.app_translation
(
    id     character varying(36) NOT NULL,
    tag    text                  NOT NULL,
    locale character varying(35) NOT NULL,
    value  text                  NOT NULL,
    CONSTRAINT pk_app_translation PRIMARY KEY (id),
    CONSTRAINT uc_app_translation_tag_locale UNIQUE (tag, locale)
);
```

Every application has it, whether it offers one language or several: a
page is not served without reading it. Like any table it becomes a domain
type, `AppTranslation`, so
[regenerate](/qlive-framework/how-to/regenerate-from-the-schema/) after
adding it.

A row translates one tag into one locale:

```sql
INSERT INTO app_translation (id, tag, locale, value)
VALUES (gen_random_uuid()::text, 'Filter {0}', 'de', '{0} filtern');
```

## The locales an application offers

```properties
qlive.i18n.locales=en,de
```

Language tags, comma-separated. A page is served in the one the browser's
`Accept-Language` matches best, falling back from `de-AT` to `de`, and in
the first one where it matches none. Without the setting the application
offers `en`. The page's locale is `config().locale`, for whatever else
depends on it.

`locale` in the table holds the same language tag, exactly: a row for
`de` does not translate a page served in `de-CH`.

## What there is to translate

A tag is one of these:

- **The static text of an `i18n()` call**, its first argument:
  `i18n("Filter {0}", label)` has the tag `Filter {0}`. QLive's own texts
  are among them -- "Next page", "Stale rows" -- and come in with the
  Vite plugin.
- **A domain type with a table**, and each of its fields: `Foo`,
  `Foo.name`. Grid headings and form labels are these.
- **A value of an enum** such a field holds: `Size.MEDIUM`.

The analysis records a tag only where it is written out. A call whose
first argument is a variable is not recorded, and its tag has no
translation, whatever the table says. Write the calls out, even where
that means a function per text rather than a lookup table of tags.

A translation holds the same placeholders as its tag, `{0}` for the first
argument after it and so on, in whatever order the language needs:
`"{1} of {0}"` may become `"{0}: {1}"`. The server logs a warning for a
translation whose placeholders differ from its tag's.

A tag is short for its text rather than the text itself: QLive's
`Stale rows` reads "Rows changed elsewhere" in English. Tags in your own
calls can be either; a short one keeps its translations when the wording
changes.

A row for a tag no code uses is not sent, so translations of removed texts
do no harm and can be cleaned up whenever.

## QLive's own texts

QLive brings the English of its own tags, so a page served in `en`, or
`en-GB`, shows them without a row in the table. A row for the same tag
and locale takes the place of QLive's English, and every other language
needs rows of its own. The tags and their English are in qlive-ts's
`dist/i18n-en.json`, a starting point for translating them.

## A text without translation

Renders as its tag in brackets, followed by the arguments where there are
any: `[Filter {0}: Name]`. That reads well enough to work with and shows
plainly what is missing.

## When a translation shows

In dev, with the next page load: the translations are read for every
page. In production they are read once per locale and kept until the
application restarts.

An application that wants to change or add to them on the client does so
in `startup()`'s init hook:

```ts
await startup({
    views: import.meta.glob("./app/**/*.tsx"),
    init: async config => {
        config.translations = {...config.translations, "Filter {0}": "{0} filtern"}
    }
})
```
