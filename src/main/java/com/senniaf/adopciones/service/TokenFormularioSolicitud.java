package com.senniaf.adopciones.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Signed, short-lived, one-use form token used to reject instant or replayed bot posts. */
@Service
public class TokenFormularioSolicitud {

    private final byte[] secreto;
    private final long esperaMinimaMillis;
    private final long vigenciaMillis;
    private final ConcurrentHashMap<String, Long> consumidos = new ConcurrentHashMap<>();

    public TokenFormularioSolicitud(@Value("${app.jwt.secret}") String secreto,
                                    @Value("${app.solicitudes.tiempo-minimo-segundos:3}") long esperaMinimaSegundos,
                                    @Value("${app.solicitudes.token-vigencia-segundos:3600}") long vigenciaSegundos) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        this.esperaMinimaMillis = Math.max(1, esperaMinimaSegundos) * 1000;
        this.vigenciaMillis = Math.max(60, vigenciaSegundos) * 1000;
    }

    public String emitir() {
        String payload = Instant.now().toEpochMilli() + ":" + UUID.randomUUID();
        String cuerpo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return cuerpo + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(firmar(cuerpo));
    }

    public boolean validarYConsumir(String token) {
        if (token == null || token.length() > 512) return false;
        String[] partes = token.split("\\.", -1);
        if (partes.length != 2) return false;

        byte[] firmaRecibida;
        try {
            firmaRecibida = Base64.getUrlDecoder().decode(partes[1]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!MessageDigest.isEqual(firmar(partes[0]), firmaRecibida)) return false;

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String[] datos = payload.split(":", 2);
        if (datos.length != 2) return false;

        long emitido;
        try {
            emitido = Long.parseLong(datos[0]);
            UUID.fromString(datos[1]);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        long ahora = System.currentTimeMillis();
        long edad = ahora - emitido;
        if (edad < esperaMinimaMillis || edad > vigenciaMillis) return false;

        consumidos.entrySet().removeIf(e -> e.getValue() < ahora);
        return consumidos.putIfAbsent(datos[1], emitido + vigenciaMillis) == null;
    }

    private byte[] firmar(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo validar el token del formulario.", ex);
        }
    }
}
