package com.navya.service;

import com.navya.domain.Role;
import com.navya.exception.ForbiddenException;

/** Who is calling. */
public record Principal(String userId, Role role) {

    public void requireAdmin() {
        if (role != Role.ADMIN) throw new ForbiddenException("Only the Navya team can do this");
    }

    public boolean isAdmin() { return role == Role.ADMIN; }
}
