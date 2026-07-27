package com.ailoganalyzer.exception;

/**
 * Kayıt sırasında kullanıcı adı zaten alınmışsa fırlatılır → 409 Conflict.
 */
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String username) {
        super("Bu kullanıcı adı zaten alınmış: " + username);
    }
}
