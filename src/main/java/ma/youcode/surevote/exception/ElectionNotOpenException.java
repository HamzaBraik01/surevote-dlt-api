package ma.youcode.surevote.exception;

import ma.youcode.surevote.domain.enums.StatutElection;

public class ElectionNotOpenException extends RuntimeException {
    public ElectionNotOpenException(Long electionId, StatutElection statut) {
        super("L'élection " + electionId + " n'est pas ouverte. Statut actuel: " + statut);
    }
}
