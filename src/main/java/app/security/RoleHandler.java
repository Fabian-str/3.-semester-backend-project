package app.security;

import app.entities.Role;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;

public class RoleHandler {

    public void requireRole(Context ctx, Role requiredRole) {

        String role = ctx.attribute("role");

        if (role == null || !role.equals(requiredRole.name())) {
            throw new ForbiddenResponse("Forbidden");
        }
    }
}