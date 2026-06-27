package com.paperagent.dto;

public record AutonomousWritingPhaseEvent(
        String phase,
        String status,
        Object data
) {
    public static AutonomousWritingPhaseEvent inProgress(String phase) {
        return new AutonomousWritingPhaseEvent(phase, "in_progress", null);
    }
    public static AutonomousWritingPhaseEvent inProgress(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "in_progress", data);
    }
    public static AutonomousWritingPhaseEvent completed(String phase, Object data) {
        return new AutonomousWritingPhaseEvent(phase, "completed", data);
    }
    public static AutonomousWritingPhaseEvent error(String phase, String message) {
        return new AutonomousWritingPhaseEvent(phase, "error", new PhaseError(message));
    }
    public record PhaseError(String message) {}
}
