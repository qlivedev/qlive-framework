/// What application code touches: the annotations a logic bean is declared with, the
/// generic domain object model, and the fetcher contract. Split out below the GraphQL
/// engine so that annotation scanning does not have to sit above the code it scans for.
///
/// This package and everything below it, apart from `json`, is derived from DomainQL,
/// Copyright 2018 Quinscape GmbH, used under the Apache License, Version 2.0. It was
/// vendored from commit cea405177c684d16e68a3081f6efbd1dfbbd5204 and repackaged. See
/// the NOTICE file at the root of this repository for the full statement of changes.
package io.github.qlivedev.qlive.api;
