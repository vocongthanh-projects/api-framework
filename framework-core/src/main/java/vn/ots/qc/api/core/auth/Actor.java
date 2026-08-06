package vn.ots.qc.api.core.auth;

public record Actor(String alias, String authProfile) {
    public Actor {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Actor alias is required");
        authProfile = authProfile == null ? "none" : authProfile;
    }

    public static Actor anonymous() { return new Actor("anonymous", "none"); }
    public static Actor of(String alias, String authProfile) { return new Actor(alias, authProfile); }
}
