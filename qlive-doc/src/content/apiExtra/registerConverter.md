What QLive registers for itself, and what an application overrides by
registering the same type again:

| Type | On the wire | In the application |
|---|---|---|
| `Timestamp` | ISO-8601 string | `Temporal.Instant` |
| `Date` | ISO-8601 string | `Temporal` value |
| `FooDocument` and friends | plain JSON object | `QueryDocument` instance |

Do it from `startup()`'s `init` hook -- see
[Register a converter](/qlive-framework/how-to/register-a-converter/).
