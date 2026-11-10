package by.bsuir.labqueue.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Считает неудачные попытки входа (REQ-SEC-002). */
@Component
public class LoginAttemptListener {

    private final AccountService accounts;

    public LoginAttemptListener(AccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        accounts.registerFailedLogin(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        accounts.registerSuccessfulLogin(event.getAuthentication().getName());
    }
}
