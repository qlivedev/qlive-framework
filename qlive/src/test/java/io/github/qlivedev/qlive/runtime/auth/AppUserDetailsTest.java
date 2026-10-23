package io.github.qlivedev.qlive.runtime.auth;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AppUserDetailsTest
{
    @Test
    void aDisabledUserIsNotEnabled()
    {
        assertThat(details(true).isEnabled(), is(false));
        assertThat(details(false).isEnabled(), is(true));
        assertThat(details(null).isEnabled(), is(true));
    }


    @Test
    void aUserTableWithoutTheColumnHasNoDisabledUsers()
    {
        final AppUserDetails details = new AppUserDetails(
            Map.of("id", "u-1", "login", "kim", "roles", "ROLE_USER", "password", "irrelevant")
        );

        assertThat(details.isEnabled(), is(true));
    }


    private static AppUserDetails details(Boolean disabled)
    {
        final Map<String, Object> user = new HashMap<>(
            Map.of("id", "u-1", "login", "kim", "roles", "ROLE_USER", "password", "irrelevant")
        );
        user.put("disabled", disabled);
        return new AppUserDetails(user);
    }
}
