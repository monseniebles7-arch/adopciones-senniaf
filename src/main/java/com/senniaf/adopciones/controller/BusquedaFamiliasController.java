package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.service.BusquedaFamiliasService;
import com.senniaf.adopciones.service.BusquedaFamiliasService.BusquedaRechazada;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HU-08.1 (Sprint 9): familias compatibles por edad con un menor.
 *
 * Bajo /api/tecnico/**: solo el Equipo Tecnico. El Juez y el Administrador
 * reciben 403 (SecurityConfig).
 */
@RestController
public class BusquedaFamiliasController {

    private final BusquedaFamiliasService busqueda;

    public BusquedaFamiliasController(BusquedaFamiliasService busqueda) {
        this.busqueda = busqueda;
    }

    /**
     *   200  {numeroExpediente, menor, edad, criterio, familiasEnEspera,
     *         sinRangoDeEdad, total, familias: [...]}
     *   404  no existe el expediente
     *   409  el menor no esta Declarado adoptable o el expediente esta cerrado
     */
    @GetMapping("/api/tecnico/expedientes/{numero}/familias-compatibles")
    public ResponseEntity<Map<String, Object>> porEdad(@PathVariable String numero) {
        try {
            return ResponseEntity.ok(busqueda.porEdad(numero));
        } catch (BusquedaRechazada ex) {
            HttpStatus codigo = ex.getMotivo() == BusquedaFamiliasService.Motivo.NO_ENCONTRADO
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            return ResponseEntity.status(codigo).body(Map.of("error", ex.getMessage()));
        }
    }
}
