package com.navya.domain;

import com.navya.exception.InvalidInputException;

public enum Condition {
    NEW_WITH_TAGS("New with tags"), LIKE_NEW("Like new"), GOOD("Good"), FAIR("Fair");

    public final String label;

    Condition(String label) { this.label = label; }

    public static Condition parse(String s) {
        for (Condition c : values()) if (c.label.equalsIgnoreCase(s == null ? "" : s.trim())) return c;
        throw new InvalidInputException("Condition must be one of New with tags, Like new, Good, Fair");
    }
}
