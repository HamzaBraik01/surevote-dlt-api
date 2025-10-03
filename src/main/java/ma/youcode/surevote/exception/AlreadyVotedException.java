package ma.youcode.surevote.exception;

public class AlreadyVotedException extends RuntimeException {
    public AlreadyVotedException(Long electionId) {
        super("Vous avez déjà voté dans l'élection avec l'identifiant: " + electionId);
    }
}
