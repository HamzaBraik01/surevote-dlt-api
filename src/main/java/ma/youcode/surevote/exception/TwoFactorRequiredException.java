package ma.youcode.surevote.exception;

public class TwoFactorRequiredException extends RuntimeException {
    public TwoFactorRequiredException() {
        super("L'authentification à deux facteurs est requise avant d'accéder au bureau de vote.");
    }
}
