package com.navya.domain;

public final class Member extends User {
    @Override public Role role() { return Role.MEMBER; }
    @Override public String badge() { return ""; }
}
