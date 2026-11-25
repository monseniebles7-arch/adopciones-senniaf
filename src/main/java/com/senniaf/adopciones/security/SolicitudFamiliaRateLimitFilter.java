package com.senniaf.adopciones.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Limits public family submissions per source IP without persisting IP addresses. */
@Component
public class SolicitudFamiliaRateLimitFilter extends OncePerRequestFilter {

    private static final String RUTA = "/api/solicitudes/familias";
    private static final int MAX_BUCKETS = 10_000;

    private final int maximo;
    private final Duration ventana;
    private final ConcurrentHashMap<String, Cubeta> cubetas = new ConcurrentHashMap<>();
    private final AtomicInteger solicitudes = new AtomicInteger();

    public SolicitudFamiliaRateLimitFilter(
            @Value("${app.solicitudes.limite-envios:10}") int maximo,
            @Value("${app.solicitudes.ventana-segundos:900}") long ventanaSegundos) {
        this.maximo = Math.max(1, maximo);
        this.ventana = Duration.ofSeconds(Math.max(1, ventanaSegundos));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !RUTA.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Instant ahora = Instant.now();
        String ip = request.getRemoteAddr();
        String clave = ip == null ? "unknown" : ip;
        if (!cubetas.containsKey(clave) && cubetas.size() >= MAX_BUCKETS) clave = "__overflow__";
        Cubeta cubeta = cubetas.computeIfAbsent(clave, k -> new Cubeta());
        long espera;
        synchronized (cubeta) {
            Instant limite = ahora.minus(ventana);
            while (!cubeta.intentos.isEmpty() && !cubeta.intentos.peekFirst().isAfter(limite)) {
                cubeta.intentos.removeFirst();
            }
            if (cubeta.intentos.size() >= maximo) {
                espera = Math.max(1, Duration.between(ahora,
                        cubeta.intentos.peekFirst().plus(ventana)).toSeconds());
            } else {
                cubeta.intentos.addLast(ahora);
                cubeta.ultimoUso = ahora;
                espera = 0;
            }
        }

        if (solicitudes.incrementAndGet() % 256 == 0 || cubetas.size() > MAX_BUCKETS) {
            Instant vencidas = ahora.minus(ventana);
            cubetas.entrySet().removeIf(e -> e.getValue().ultimoUso.isBefore(vencidas));
        }

        if (espera > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(espera));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Se alcanzó el límite temporal de envíos. Intenta nuevamente más tarde.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Cubeta {
        private final Deque<Instant> intentos = new ArrayDeque<>();
        private volatile Instant ultimoUso = Instant.now();
    }
}
