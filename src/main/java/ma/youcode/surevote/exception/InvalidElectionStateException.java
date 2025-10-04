package ma.youcode.surevote.exception;

public class InvalidElectionStateException extends RuntimeException {
    public InvalidElectionStateException(String message) {
        super(message);
    }
}
