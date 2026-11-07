package com.navya.domain;

import com.navya.exception.InvalidInputException;

public enum Category {
    CLOTHES("Clothes"), SHOES("Shoes"), BAGS("Bags"), ACCESSORIES("Accessories");

    public final String label;

    Category(String label) { this.label = label; }

    public static Category parse(String s) {
        for (Category c : values()) if (c.label.equalsIgnoreCase(s == null ? "" : s.trim())) return c;
        throw new InvalidInputException("Category must be one of Clothes, Shoes, Bags, Accessories");
    }
}
