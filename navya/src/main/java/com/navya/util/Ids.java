package com.navya.util;

import java.util.UUID;
import java.util.regex.Pattern;

public final class Ids {
    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Ids() {}

    public static String newId() { return UUID.randomUUID().toString(); }

    /** Ids from the URL are checked before they reach SQL, so a malformed id is a 404, not a database error. */
    public static boolean valid(String id) { return id != null && UUID_RE.matcher(id).matches(); }
}
