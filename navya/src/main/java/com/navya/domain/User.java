package com.navya.domain;

import java.time.Instant;

/** An account. Members buy and sell, admins moderate reports. */
public abstract class User {
    public String id;
    public String email;
    public String name;
    public String city;
    public String passwordHash;
    public Instant createdAt;
    /** When the member accepted the Terms and Privacy Notice (null for accounts made without the sign-up form), and which version. */
    public Instant consentedAt;
    public String termsVersion;

    public abstract Role role();

    /** Short text shown next to the name in the app. */
    public abstract String badge();
}
