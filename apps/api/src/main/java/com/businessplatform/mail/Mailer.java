package com.businessplatform.mail;

/** Delivers one email. Implementations may block; {@link MailService} calls them off the request thread. */
public interface Mailer {

    void send(MailMessage message) throws Exception;

    /** Whether messages actually leave the server (false for the logging stand-in). */
    default boolean delivers() {
        return true;
    }
}
