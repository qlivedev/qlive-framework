A field path is dotted and follows relations, and what it means to cross a
to-many relation depends on where the condition is evaluated. As a database
query condition, `bazLinks.baz.name` becomes a correlated `EXISTS` rather than
a join. Against an object graph in memory it is a walk and needs an index:
`bazLinks.0.baz.name` names the baz behind one concrete link. A path that only
crosses to-one relations, `owner.login`, says the same thing in both worlds.

See [semantic differences per
technology](/qlive-framework/reference/filter-dsl/#semantic-differences-per-technology).
