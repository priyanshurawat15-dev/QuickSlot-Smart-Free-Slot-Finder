package in.quickslot.shared;

public record AuthenticatedUser(
    String id, String fullName, String email, String role, String campusId, String department) {}
