package com.senniaf.adopciones.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolicitudFamiliaRateLimitFilterTest {

    @Test
    @DisplayName("Bloquea el siguiente envío desde la misma IP y devuelve 429 con Retry-After")
    void limitaEnviosPorIp() throws Exception {
        var filtro = new SolicitudFamiliaRateLimitFilter(2, 60);
        var ejecutados = new AtomicInteger();
        FilterChain cadena = (request, response) -> ejecutados.incrementAndGet();

        for (int i = 0; i < 2; i++) {
            var respuesta = new MockHttpServletResponse();
            filtro.doFilter(peticion("192.0.2.1"), respuesta, cadena);
            assertEquals(200, respuesta.getStatus());
        }

        var bloqueada = new MockHttpServletResponse();
        filtro.doFilter(peticion("192.0.2.1"), bloqueada, cadena);

        assertEquals(429, bloqueada.getStatus());
        assertTrue(Long.parseLong(bloqueada.getHeader("Retry-After")) > 0);
        assertEquals(2, ejecutados.get());
    }

    @Test
    @DisplayName("Aplica el límite por separado a cada IP")
    void cadaIpTieneSuPropiaVentana() throws Exception {
        var filtro = new SolicitudFamiliaRateLimitFilter(1, 60);
        var ejecutados = new AtomicInteger();
        FilterChain cadena = (request, response) -> ejecutados.incrementAndGet();

        filtro.doFilter(peticion("192.0.2.1"), new MockHttpServletResponse(), cadena);
        filtro.doFilter(peticion("192.0.2.2"), new MockHttpServletResponse(), cadena);

        assertEquals(2, ejecutados.get());
    }

    @Test
    @DisplayName("No limita otros métodos ni otras rutas")
    void soloLimitaPostPublicoDeFamilias() throws Exception {
        var filtro = new SolicitudFamiliaRateLimitFilter(1, 60);
        var ejecutados = new AtomicInteger();
        FilterChain cadena = (request, response) -> ejecutados.incrementAndGet();

        var get = peticion("192.0.2.1");
        get.setMethod("GET");
        filtro.doFilter(get, new MockHttpServletResponse(), cadena);
        var otraRuta = peticion("192.0.2.1");
        otraRuta.setRequestURI("/api/otra-ruta");
        filtro.doFilter(otraRuta, new MockHttpServletResponse(), cadena);

        assertEquals(2, ejecutados.get());
    }

    private static MockHttpServletRequest peticion(String ip) {
        var request = new MockHttpServletRequest("POST", "/api/solicitudes/familias");
        request.setRemoteAddr(ip);
        return request;
    }
}
