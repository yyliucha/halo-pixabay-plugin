package com.yyliucha.pixabay;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Synthetic authenticated identity for background runs that have no user
 * request thread (scheduled runs, article events). Halo's attachment upload
 * API requires an authenticated SecurityContext on the executing thread.
 */
final class SystemIdentity {

    private SystemIdentity() {
    }

    static SecurityContext securityContext() {
        return new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken("pixabay-downloader", "", List.of()));
    }
}
