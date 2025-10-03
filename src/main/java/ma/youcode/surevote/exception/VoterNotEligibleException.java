package ma.youcode.surevote.exception;

public class VoterNotEligibleException extends RuntimeException {
    public VoterNotEligibleException(String message) {
        super(message);
    }
}
