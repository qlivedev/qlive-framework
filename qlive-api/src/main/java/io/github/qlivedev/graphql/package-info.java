/// The part of the GraphQL engine that everything else may depend on: the annotations
/// a logic bean is declared with, the generic domain object model, and the fetcher
/// contract. Split out below the engine so that annotation scanning does not have to
/// sit above the code it scans for.
///
/// This package and everything below it is derived from DomainQL, Copyright 2018
/// Quinscape GmbH, used under the Apache License, Version 2.0. It was vendored from
/// commit cea405177c684d16e68a3081f6efbd1dfbbd5204 and repackaged. See the NOTICE
/// file at the root of this repository for the full statement of changes.
package io.github.qlivedev.graphql;
