package com.ailoganalyzer.exception;

/**
 * Giriş sırasında kullanıcı adı bulunamaz veya şifre yanlışsa fırlatılır → 401 Unauthorized.
 * Kullanıcı adının var olup olmadığını sızdırmamak için mesaj bilinçli olarak geneldir.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Kullanıcı adı veya şifre hatalı.");
    }
}
