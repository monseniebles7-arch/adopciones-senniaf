package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.CambioEtapaJuridica;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.EventoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.repository.CambioEtapaJuridicaRepository;
import com.senniaf.adopciones.repository.EventoExpedienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HU-07.4 · Historial juridico del menor (Sprint 9).
 *
 * "Como usuario autorizado, quiero ver todos los cambios de etapa juridica
 * del menor, para reconstruir el recorrido legal del caso."
 *
 * Es una linea de tiempo separada del historial general: no muestra
 * consultas ni documentos, solo el recorrido legal, del ingreso al cambio
 * mas reciente, con la resolucion de cada cambio cuando la hay.
 *
 * Los cambios del Sprint 9 en adelante se leen de cambios_etapa_juridica.
 * Los del Sprint 8 solo quedaron en el historial general; se leen de ahi
 * (su texto lo armaba siempre el mismo codigo) y se marcan como anteriores
 * a la resolucion obligatoria.
 */
@Service
public class HistorialJuridicoService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Texto que escribia el Sprint 8: "Etapa jurídica: A -> B. Observación: texto". */
    private static final Pattern TEXTO_SPRINT_8 =
            Pattern.compile("^Etapa jurídica: (.+?) -> (.+?)\\. Observación: (.*)$", Pattern.DOTALL);

    private static final Map<String, EstadoJuridico> POR_ETIQUETA = Arrays.stream(EstadoJuridico.values())
            .collect(Collectors.toMap(EstadoJuridico::getEtiqueta, Function.identity()));

    private final CambioEtapaJuridicaRepository cambios;
    private final EventoExpedienteRepository eventos;

    public HistorialJuridicoService(CambioEtapaJuridicaRepository cambios, EventoExpedienteRepository eventos) {
        this.cambios = cambios;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> linea(Expediente e) {
        List<Linea> lineas = new ArrayList<>();

        // Punto de partida: todo expediente nace En proteccion (HU-07.1).
        LocalDateTime ingreso = e.getFechaCreacion() != null ? e.getFechaCreacion()
                : e.getFechaEstadoJuridico() != null ? e.getFechaEstadoJuridico() : LocalDateTime.now();
        lineas.add(new Linea(ingreso, null, EstadoJuridico.EN_PROTECCION,
                "Ingreso al sistema de protección.", e.getCreadoPor(), null, null, false));

        List<CambioEtapaJuridica> registrados = cambios.findByExpedienteIdOrderByFechaHoraAscIdAsc(e.getId());
        Set<Long> eventosEnlazados = registrados.stream()
                .map(CambioEtapaJuridica::getEventoId).filter(Objects::nonNull).collect(Collectors.toSet());

        for (CambioEtapaJuridica c : registrados) {
            lineas.add(new Linea(c.getFechaHora(), EstadoJuridico.valueOf(c.getEtapaAnterior()),
                    EstadoJuridico.valueOf(c.getEtapaNueva()), c.getObservacion(), c.getUsuario(), c.getRol(),
                    EstadoJuridicoService.aMapa(c.getResolucion()), false));
        }

        // Cambios del Sprint 8: solo estan en el historial general.
        for (EventoExpediente ev : eventos.findByExpedienteIdOrderByFechaHoraDescIdDesc(e.getId())) {
            if (ev.getTipo() != TipoEvento.ESTADO_JURIDICO || eventosEnlazados.contains(ev.getId())) continue;
            Matcher m = TEXTO_SPRINT_8.matcher(ev.getDescripcion() == null ? "" : ev.getDescripcion());
            if (!m.matches()) continue;
            EstadoJuridico desde = POR_ETIQUETA.get(m.group(1));
            EstadoJuridico hacia = POR_ETIQUETA.get(m.group(2));
            if (desde == null || hacia == null) continue;
            lineas.add(new Linea(ev.getFechaHora(), desde, hacia, m.group(3).trim(), ev.getUsuario(), ev.getRol(),
                    null, true));
        }

        lineas.sort(Comparator.comparing(Linea::fechaHora));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("numeroExpediente", e.getNumeroExpediente());
        respuesta.put("etapaActual", e.getEstadoJuridico().name());
        respuesta.put("etapaActualEtiqueta", e.getEstadoJuridico().getEtiqueta());
        respuesta.put("total", lineas.size());
        respuesta.put("linea", lineas.stream().map(HistorialJuridicoService::aMapa).toList());
        return respuesta;
    }

    private static Map<String, Object> aMapa(Linea l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fechaHora", l.fechaHora().format(FECHA_HORA));
        m.put("etapaAnterior", l.desde() == null ? null : l.desde().name());
        m.put("etapaAnteriorEtiqueta", l.desde() == null ? null : l.desde().getEtiqueta());
        m.put("etapa", l.hacia().name());
        m.put("etapaEtiqueta", l.hacia().getEtiqueta());
        m.put("observacion", l.observacion());
        m.put("usuario", l.usuario());
        m.put("rol", l.rol());
        m.put("resolucion", l.resolucion());
        // Antes de la HU-07.3 el paso a Declarado adoptable no exigia resolucion.
        m.put("sinResolucionPrevia", l.anteriorAlSprint9() && l.hacia() == EstadoJuridico.DECLARADO_ADOPTABLE);
        return m;
    }

    private record Linea(LocalDateTime fechaHora, EstadoJuridico desde, EstadoJuridico hacia, String observacion,
                         String usuario, String rol, Map<String, Object> resolucion, boolean anteriorAlSprint9) {
    }
}
