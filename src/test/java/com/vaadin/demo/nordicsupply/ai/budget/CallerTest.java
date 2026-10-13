package com.vaadin.demo.nordicsupply.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.vaadin.demo.nordicsupply.ui.TestUi;

/** A caller is the current session, as the log names it, and the current request's address, counted under its key. */
class CallerTest {

    @AfterEach
    void noUi() {
        UI.setCurrent(null);
    }

    @Test
    void theCurrentCallerIsTheSessionsIdAndTheRequestsAddress() {
        var ui = TestUi.withSession();

        var caller = Caller.current();

        assertThat(caller.session()).isNotBlank().isEqualTo(ui.getSession().getPushId());
        // no request in a test: the address is unknown, and counted as such
        assertThat(caller.address()).isNull();
        assertThat(caller.addressKey()).isEqualTo(Caller.UNKNOWN);
    }

    @Test
    void withoutASessionTheCallerIsUnknown() {
        var caller = Caller.current();

        assertThat(caller.session()).isNull();
        assertThat(caller.logged()).isEqualTo(Caller.UNKNOWN);
    }

    @Test
    void anIPv4AddressIsItsOwnKeyAndAnIPv6AddressItsPrefix() {
        assertThat(new Caller("s", "203.0.113.7").addressKey()).isEqualTo("203.0.113.7");
        assertThat(new Caller("s", "2001:db8:1:2:aaaa:bbbb:cccc:dddd").addressKey())
                .isEqualTo("2001:db8:1:2::/64");
        assertThat(new Caller("s", "fe80::1%eth0").addressKey()).isEqualTo("fe80:0:0:0::/64");
        // an IPv4 address written the IPv6 way is an IPv4 address
        assertThat(new Caller("s", "::ffff:203.0.113.7").addressKey()).isEqualTo("::ffff:203.0.113.7");
        assertThat(new Caller("s", "not:an:address").addressKey()).isEqualTo("not:an:address");
    }
}
