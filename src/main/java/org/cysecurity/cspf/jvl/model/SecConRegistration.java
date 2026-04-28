package org.cysecurity.cspf.jvl.model;

import javax.servlet.http.HttpSession;

/**
 * Session attribute keys and helpers for the Apiiro SecCon 2026 registration wizard.
 */
public final class SecConRegistration {

    public static final String ATTR_NAME = "seccon_name";
    public static final String ATTR_ADDRESS = "seccon_address";
    public static final String ATTR_PHONE = "seccon_phone";
    public static final String ATTR_EMAIL = "seccon_email";
    /** Values: {@link #TIER_BASIC} or {@link #TIER_PREFERRED}. */
    public static final String ATTR_TIER = "seccon_tier";
    public static final String ATTR_AMOUNT_CENTS = "seccon_amount_cents";

    public static final String SESSION_CSRF = "secconCsrfToken";

    public static final String CONFERENCE_DISPLAY = "Apiiro SecCon 2026";

    public static final String TIER_BASIC = "basic";
    public static final String TIER_PREFERRED = "preferred";

    public static final int AMOUNT_BASIC_CENTS = 2500;
    public static final int AMOUNT_PREFERRED_CENTS = 5000;

    private SecConRegistration() {}

    public static boolean hasContact(HttpSession session) {
        if (session == null) {
            return false;
        }
        return nonEmpty(session.getAttribute(ATTR_NAME))
                && nonEmpty(session.getAttribute(ATTR_ADDRESS))
                && nonEmpty(session.getAttribute(ATTR_PHONE))
                && nonEmpty(session.getAttribute(ATTR_EMAIL));
    }

    public static boolean hasTier(HttpSession session) {
        if (session == null || !hasContact(session)) {
            return false;
        }
        final Object tier = session.getAttribute(ATTR_TIER);
        final Object amt = session.getAttribute(ATTR_AMOUNT_CENTS);
        if (!(tier instanceof String)) {
            return false;
        }
        final String t = ((String) tier).trim().toLowerCase();
        if (!TIER_BASIC.equals(t) && !TIER_PREFERRED.equals(t)) {
            return false;
        }
        return amt instanceof Long && ((Long) amt).longValue() > 0;
    }

    public static int amountForTier(String tierToken) {
        if (tierToken == null) {
            return -1;
        }
        final String t = tierToken.trim().toLowerCase();
        if (TIER_BASIC.equals(t)) {
            return AMOUNT_BASIC_CENTS;
        }
        if (TIER_PREFERRED.equals(t)) {
            return AMOUNT_PREFERRED_CENTS;
        }
        return -1;
    }

    public static String tierLabel(String tierToken) {
        if (tierToken == null) {
            return "";
        }
        final String t = tierToken.trim().toLowerCase();
        if (TIER_BASIC.equals(t)) {
            return "Basic";
        }
        if (TIER_PREFERRED.equals(t)) {
            return "Preferred";
        }
        return tierToken;
    }

    private static boolean nonEmpty(Object v) {
        if (v == null) {
            return false;
        }
        return !String.valueOf(v).trim().isEmpty();
    }
}
