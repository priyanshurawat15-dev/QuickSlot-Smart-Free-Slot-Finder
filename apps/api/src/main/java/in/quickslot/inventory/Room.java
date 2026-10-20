package in.quickslot.inventory;

public record Room(
    String id,
    String code,
    String kind,
    String building,
    Integer capacity,
    String campusId,
    String department,
    boolean active,
    String sourceLabel,
    String floor) {}
