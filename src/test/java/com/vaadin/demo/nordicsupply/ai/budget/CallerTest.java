package com.vaadin.demo.nordicsupply.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A caller is the visit, as the logs name it, and the current request's address, counted under its key. */
class CallerTest {

    private static final UUID VISIT = UUID.randomUUID();

    @AfterEach
    void noRequest() {
        CurrentInstance.clearAll();
    }

    @Test
    void theCurrentCallerIsTheVisitAndTheRequestsAddress() {
        var request = mock(VaadinRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        CurrentInstance.set(VaadinRequest.class, request);

        var caller = Caller.current(VISIT);

        assertThat(caller).isEqualTo(new Caller(VISIT, "203.0.113.7"));
    }

    @Test
    void withoutARequestTheAddressIsUnknown() {
        var caller = Caller.current(VISIT);

        assertThat(caller.visit()).isEqualTo(VISIT);
        assertThat(caller.address()).isNull();
        assertThat(caller.addressKey()).isEqualTo(Caller.UNKNOWN);
    }

    @Test
    void anIPv4AddressIsItsOwnKeyAndAnIPv6AddressItsPrefix() {
        assertThat(new Caller(VISIT, "203.0.113.7").addressKey()).isEqualTo("203.0.113.7");
        assertThat(new Caller(VISIT, "2001:db8:1:2:aaaa:bbbb:cccc:dddd").addressKey())
                .isEqualTo("2001:db8:1:2::/64");
        assertThat(new Caller(VISIT, "fe80::1%eth0").addressKey()).isEqualTo("fe80:0:0:0::/64");
        // an IPv4 address written the IPv6 way is an IPv4 address
        assertThat(new Caller(VISIT, "::ffff:203.0.113.7").addressKey()).isEqualTo("::ffff:203.0.113.7");
        assertThat(new Caller(VISIT, "not:an:address").addressKey()).isEqualTo("not:an:address");
    }
}
