package bf.anptic.geoportail.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Filtre unique qui gere toute l'authentification decideur :
// 1) Lit le JWT depuis Authorization: Bearer <token>
// 2) Valide et alimente AccessScopeHolder (role + ministere + userId)
// 3) Rejette les requetes sans JWT valide sur /api/v1 (sauf /auth)
@Component
public class SiteAccessTokenFilter extends OncePerRequestFilter {

    private final DecideurJwtService jwtService;

    public SiteAccessTokenFilter(DecideurJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Endpoints publics : login, healthcheck (monitoring, sans JWT),
        // cle VAPID publique (necessaire avant meme l'inscription push,
        // donc avant tout JWT utilisable) + tout ce qui n'est pas /api/v1
        if (!uri.startsWith("/api/v1") || uri.startsWith("/api/v1/auth")
                || uri.equals("/api/v1/health") || uri.equals("/api/v1/push/public-key")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (jwtService.estValide(token)) {
                    Claims claims = jwtService.validerToken(token);
                    String role = (String) claims.get("role");
                    String ministere = (String) claims.get("ministere");
                    Long userId = claims.get("userId", Long.class);

                    AccessScopeHolder.setUserId(userId);

                    if ("LAMBDA".equals(role)) {
                        AccessScopeHolder.setMinistere(null);
                        AccessScopeHolder.setRole("LAMBDA");
                    } else {
                        AccessScopeHolder.setMinistere(ministere);
                        AccessScopeHolder.setRole("DECIDEUR");
                    }

                    chain.doFilter(request, response);
                    return;
                }
            }

            // Pas de JWT valide
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Accès refusé. Veuillez vous connecter.\"}");

        } finally {
            AccessScopeHolder.clear();
        }
    }
}