package com.navya.domain;

public final class Admin extends User {
    @Override public Role role() { return Role.ADMIN; }
    @Override public String badge() { return "Navya team"; }
}
