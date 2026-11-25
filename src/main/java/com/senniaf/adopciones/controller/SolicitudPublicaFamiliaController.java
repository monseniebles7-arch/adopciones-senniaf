package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.service.FamiliaService;
import com.senniaf.adopciones.service.TokenFormularioSolicitud;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Receives self-submitted family applications; it exposes no family data. */
@RestController
@RequestMapping("/api/solicitudes/familias")
public class SolicitudPublicaFamiliaController {

    private final FamiliaService servicio;
    private final TokenFormularioSolicitud tokenFormulario;

    public SolicitudPublicaFamiliaController(FamiliaService servicio,
                                             TokenFormularioSolicitud tokenFormulario) {
        this.servicio = servicio;
        this.tokenFormulario = tokenFormulario;
    }

    @GetMapping("/token")
    public Map<String, String> token() {
        return Map.of("token", tokenFormulario.emitir());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos) {
        if (!texto(datos.get("website")).isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solicitud no válida."));
        }
        if (!tokenFormulario.validarYConsumir(texto(datos.get("form_token")))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recarga el formulario e inténtalo nuevamente."));
        }

        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        String documento = texto(datos.get("numero_documento"));
        String documentoSegundo = texto(datos.get("segundo_numero_documento"));
        if (servicio.documentosYaRegistrados(documento, documentoSegundo)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe una solicitud registrada con uno de esos documentos. Contacta al equipo técnico si necesitas actualizarla.",
                                 "campos", List.of("numero_documento", "segundo_numero_documento")));
        }

        FamiliaSolicitante creada = servicio.crear(datos, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "numeroSolicitud", creada.getNumeroSolicitud(),
                "asignacionPendiente", creada.getTecnicoAsignado() == null));
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString().trim();
    }
}
