package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** Consulta de auditoria para el Administrador del sistema. */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping
    public Map<String, Object> listar(@RequestParam(required = false) String usuario,
                                      @RequestParam(required = false) String tipo,
                                      @RequestParam(required = false) String entidad,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "25") int tamano) {
        return auditoria.buscar(usuario, tipo, entidad, desde, hasta, pagina, tamano);
    }
}
