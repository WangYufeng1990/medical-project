package com.example.medical.module.chat.service;

/**
 * One side of a conversation: {@code STAFF} is a sys_user id, {@code PATIENT} a
 * patient id. The two id spaces overlap (Post-Round 44 review R2-1), so a party is
 * always the pair, never a bare number.
 * <p>
 * Ordering is defined so that the two sides of a thread have exactly one canonical
 * arrangement — {@code (type, id)} — which is what makes a conversation addressable
 * by its pair (Round 54.2).
 */
public record Party(String type, Long id) implements Comparable<Party> {

    public static Party staff(Long staffId) {
        return new Party(ChatService.STAFF, staffId);
    }

    public static Party patient(Long patientId) {
        return new Party(ChatService.PATIENT, patientId);
    }

    @Override
    public int compareTo(Party other) {
        int byType = type.compareTo(other.type);
        return byType != 0 ? byType : Long.compare(id, other.id);
    }
}
