package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.ResultadoIdoneidad;
import com.senniaf.adopciones.repository.DictamenIdoneidadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-08.1 · Busqueda de familias compatibles por edad (Sprint 9).
 *
 * "Como Tecnico, quiero buscar familias segun el rango de edad que aceptan,
 * para encontrar candidatas compatibles con un menor declarado adoptable."
 *
 * Reglas:
 *   1. Solo para un menor Declarado adoptable: antes de eso no hay
 *      resolucion judicial que permita buscarle familia (HU-07.3).
 *   2. Con el expediente Cerrado no se busca: primero se reabre.
 *   3. Solo aparecen familias de la lista de espera, es decir, con
 *      dictamen vigente Idonea y solicitud no cerrada (HU-06.3).
 *   4. La edad del menor, en anos cumplidos, debe estar dentro del rango
 *      que la familia acepta (edad minima y maxima, ambas incluidas).
 *   5. Una familia que no declaro su rango de edad no se puede comparar:
 *      no aparece, y la respuesta dice cuantas quedaron fuera por eso.
 *   6. El orden es el de la lista de espera (fecha de idoneidad). El orden
 *      por compatibilidad y la razon de cada coincidencia son HU-08.3.
 *
 * La ruta /api/tecnico/** limita la busqueda al Equipo Tecnico, que cubre
 * las funciones del Comite de Adopciones (decision de la PO).
 */
@Service
public class BusquedaFamiliasService {

    /** El controlador traduce cada motivo a su codigo HTTP: 404 o 409. */
    public enum Motivo { NO_ENCONTRADO, CONFLICTO }

    public static class BusquedaRechazada extends RuntimeException {
        private final Motivo motivo;

        public BusquedaRechazada(Motivo motivo, String mensaje) {
            super(mensaje);
            this.motivo = motivo;
        }

        public Motivo getMotivo() { return motivo; }
    }

    private final ExpedienteService expedientes;
    private final DictamenIdoneidadRepository dictamenes;

    public BusquedaFamiliasService(ExpedienteService expedientes, DictamenIdoneidadRepository dictamenes) {
        this.expedientes = expedientes;
        this.dictamenes = dictamenes;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> porEdad(String numeroExpediente) {
        Expediente e = expedientes.porNumero(numeroExpediente)
                .orElseThrow(() -> new BusquedaRechazada(Motivo.NO_ENCONTRADO,
                        "No existe un expediente con el número " + numeroExpediente));

        if (e.getEstado() == EstadoExpediente.CERRADO) {
            throw new BusquedaRechazada(Motivo.CONFLICTO,
                    "El expediente está cerrado: el Equipo Técnico debe reabrirlo antes de buscar familias.");
        }
        if (e.getEstadoJuridico() != EstadoJuridico.DECLARADO_ADOPTABLE) {
            throw new BusquedaRechazada(Motivo.CONFLICTO,
                    "La búsqueda de familias solo se habilita para un menor Declarado adoptable. Etapa actual: "
                    + e.getEstadoJuridico().getEtiqueta() + ".");
        }

        LocalDate hoy = LocalDate.now();
        int edad = Period.between(e.getFechaNacimiento(), hoy).getYears();

        List<DictamenIdoneidad> enEspera =
                dictamenes.listaDeEspera(ResultadoIdoneidad.IDONEA, EstadoFamilia.CERRADA);

        List<Map<String, Object>> compatibles = new ArrayList<>();
        int sinRango = 0;
        for (int i = 0; i < enEspera.size(); i++) {
            DictamenIdoneidad d = enEspera.get(i);
            FamiliaSolicitante f = d.getFamilia();
            Integer min = f.getEdadMinima();
            Integer max = f.getEdadMaxima();
            if (min == null || max == null) {
                sinRango++;
                continue;
            }
            if (edad < min || edad > max) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("posicionListaEspera", i + 1);
            m.put("numeroSolicitud", f.getNumeroSolicitud());
            m.put("familia", f.getNombreFamilia());
            m.put("edadMinima", min);
            m.put("edadMaxima", max);
            m.put("rangoEdad", min + " a " + max + " años");
            m.put("fechaIdoneidad", d.getFechaIdoneidad().toString());
            m.put("diasEnEspera", ChronoUnit.DAYS.between(d.getFechaIdoneidad(), hoy));
            compatibles.add(m);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("numeroExpediente", e.getNumeroExpediente());
        respuesta.put("menor", e.getNombreCompleto());
        respuesta.put("edad", edad);
        respuesta.put("criterio", "Familias idóneas que aceptan a un menor de " + edad
                + (edad == 1 ? " año" : " años"));
        respuesta.put("familiasEnEspera", enEspera.size());
        respuesta.put("sinRangoDeEdad", sinRango);
        respuesta.put("total", compatibles.size());
        respuesta.put("familias", compatibles);
        return respuesta;
    }
}
