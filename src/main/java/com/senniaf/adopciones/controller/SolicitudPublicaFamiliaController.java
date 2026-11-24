package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.service.FamiliaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    public SolicitudPublicaFamiliaController(FamiliaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> datos) {
        List<String> malos = servicio.validar(datos);
        if (!malos.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Datos invalidos", "campos", malos));
        }

        String documento = String.valueOf(datos.getOrDefault("numero_documento", "")).trim();
        if (servicio.documentoYaRegistrado(documento)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe una solicitud registrada con ese documento",
                                 "campos", List.of("numero_documento")));
        }

        FamiliaSolicitante creada = servicio.crear(datos, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "numeroSolicitud", creada.getNumeroSolicitud(),
                "estado", creada.getEstado().name()));
    }
}
