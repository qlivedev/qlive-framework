package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.graphql.config.SourceField;
import io.github.qlivedev.qlive.graphql.config.TargetField;
import io.github.qlivedev.qlive.graphql.logicimpl.ConfigureNonDBByNameLogic;
import io.github.qlivedev.qlive.graphql.testdomain.Public;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.Bar;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.BarOrg;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.BarOwner;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.Foo;
import org.junit.jupiter.api.Test;
import org.svenson.JSON;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.github.qlivedev.qlive.graphql.testdomain.Tables.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class QLiveDomainNamingTest
{
}
