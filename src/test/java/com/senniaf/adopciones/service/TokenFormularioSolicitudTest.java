package com.senniaf.adopciones.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenFormularioSolicitudTest {

    @Test
    @DisplayName("Acepta un token auténtico tras el tiempo mínimo y lo consume una sola vez")
    void tokenValidoEsDeUnSoloUso() throws InterruptedException {
        var servicio = new TokenFormularioSolicitud("secreto-de-prueba-de-al-menos-32-caracteres", 1, 60);
        String token = servicio.emitir();
        assertNotNull(token);

        Thread.sleep(1100);

        assertTrue(servicio.validarYConsumir(token));
        assertFalse(servicio.validarYConsumir(token));
    }

    @Test
    @DisplayName("Rechaza tokens falsificados o alterados")
    void rechazaTokenAlterado() {
        var servicio = new TokenFormularioSolicitud("secreto-de-prueba-de-al-menos-32-caracteres", 1, 60);
        String token = servicio.emitir();

        assertFalse(servicio.validarYConsumir("falso.token"));
        assertFalse(servicio.validarYConsumir(token + "x"));
    }

    @Test
    @DisplayName("Rechaza un formulario enviado antes del tiempo mínimo")
    void rechazaEnvioDemasiadoRapido() {
        var servicio = new TokenFormularioSolicitud("secreto-de-prueba-de-al-menos-32-caracteres", 3, 60);

        assertFalse(servicio.validarYConsumir(servicio.emitir()));
    }
}
