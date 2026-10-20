package vn.tuikhon.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    MDC.put("requestId", id);
    res.setHeader("X-Request-ID", id);
    res.setHeader("Cache-Control", "no-store");
    long start = System.nanoTime();
    try {
      chain.doFilter(req, res);
    } finally {
      log.info(
          "request={} method={} path={} status={} durationMs={}",
          id,
          req.getMethod(),
          req.getRequestURI(),
          res.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.clear();
    }
  }
}
