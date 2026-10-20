package vn.tuikhon.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class GatewayFilter extends OncePerRequestFilter {
  private final byte[] expected;

  public GatewayFilter(@Value("${GATEWAY_SECRET}") String secret) {
    if (secret.length() < 32) throw new IllegalStateException("Configure a strong gateway secret");
    expected = secret.getBytes(StandardCharsets.UTF_8);
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getRequestURI().startsWith("/api/")) {
      String supplied = req.getHeader("X-Gateway-Key");
      if (supplied == null
          || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
        res.setStatus(403);
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"Gateway required\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
