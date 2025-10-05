package ma.youcode.surevote.exception;

public class ResultsNotAvailableException extends RuntimeException {
    public ResultsNotAvailableException(Long electionId) {
        super("Les résultats de l'élection " + electionId + " ne sont pas encore disponibles.");
    }
}
