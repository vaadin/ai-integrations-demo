package com.vaadin.demo.nordicsupply.ai.budget;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;

/**
 * Who asks for a turn: the session and the address the question came from, both as the budget counts them. The
 * demo has no login, and the employee the session is signed in as can be switched by anyone, so neither names the
 * visitor. Read on the request thread: a turn runs off it, and there is no request to read by then.
 *
 * @param session the session's id, or {@code null} when there is no session
 * @param address the address the request came from, or {@code null} when there is no request
 */
public record Caller(String session, String address) {

    /** The key of a caller whose session or address is unknown; such callers are counted together. */
    static final String UNKNOWN = "-";

    /** The characters of an IP literal, IPv4-mapped IPv6 included; anything else is counted as it was written. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

    private static final int IPV6_BYTES = 16;

    /** The bytes of an IPv6 /64 prefix: the half of the address a household or a server is given whole. */
    private static final int PREFIX_BYTES = 8;

    /**
     * The caller of the current request: its session's own id and the address the request came from. The session is
     * the current one, or the current UI's where only the UI is current, as it is where a test made it so.
     */
    public static Caller current() {
        var session = VaadinSession.getCurrent();
        if (session == null && UI.getCurrent() != null) {
            session = UI.getCurrent().getSession();
        }
        var request = VaadinRequest.getCurrent();
        return new Caller(
                session == null ? null : session.getPushId(), request == null ? null : request.getRemoteAddr());
    }

    /** The key the session is counted under. */
    String sessionKey() {
        return session == null ? UNKNOWN : session;
    }

    /** The session as the log names it: a short hash of its id, enough to tell sessions apart, and not the id. */
    String logged() {
        return session == null ? UNKNOWN : Integer.toHexString(session.hashCode());
    }

    /** The key the address is counted under: an IPv4 address as it is, an IPv6 address by its /64 prefix. */
    String addressKey() {
        if (address == null) {
            return UNKNOWN;
        }
        if (address.indexOf(':') < 0) {
            return address;
        }
        // an interface's zone, as in fe80::1%eth0, is not part of the address
        var zoneless = address.substring(0, address.indexOf('%') < 0 ? address.length() : address.indexOf('%'));
        if (!IP_LITERAL.matcher(zoneless).matches()) {
            return address;
        }
        try {
            // a literal is parsed, never looked up
            var bytes = InetAddress.getByName(zoneless).getAddress();
            if (bytes.length == IPV6_BYTES) {
                return prefix(bytes);
            }
        } catch (UnknownHostException e) {
            // not an address after all; counted as it was written
        }
        return address;
    }

    private static String prefix(byte[] bytes) {
        var groups = new StringBuilder();
        for (int i = 0; i < PREFIX_BYTES; i += 2) {
            if (!groups.isEmpty()) {
                groups.append(':');
            }
            groups.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff)));
        }
        return groups + "::/64";
    }
}
