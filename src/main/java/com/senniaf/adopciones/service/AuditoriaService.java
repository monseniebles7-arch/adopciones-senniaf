package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EventoExpediente;
import com.senniaf.adopciones.model.EventoFamilia;
import com.senniaf.adopciones.model.EventoSistema;
import com.senniaf.adopciones.repository.EventoExpedienteRepository;
import com.senniaf.adopciones.repository.EventoFamiliaRepository;
import com.senniaf.adopciones.repository.EventoSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Vista de auditoria de solo lectura, construida con los historiales existentes. */
@Service
public class AuditoriaService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EventoExpedienteRepository eventosExpediente;
    private final EventoFamiliaRepository eventosFamilia;
    private final EventoSistemaRepository eventosSistema;

    public AuditoriaService(EventoExpedienteRepository eventosExpediente,
                           EventoFamiliaRepository eventosFamilia,
                           EventoSistemaRepository eventosSistema) {
        this.eventosExpediente = eventosExpediente;
        this.eventosFamilia = eventosFamilia;
        this.eventosSistema = eventosSistema;
    }

    /** Combina los dos historiales y pagina el resultado mas reciente primero. */
    @Transactional(readOnly = true)
    public Map<String, Object> buscar(String usuario, String tipo, String entidad,
                                     LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = Math.max(1, Math.min(tamano, 100));
        String usuarioFiltro = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        String tipoFiltro = normalizar(tipo);
        String entidadFiltro = normalizar(entidad);

        List<Fila> todas = new ArrayList<>();
        for (EventoExpediente ev : eventosExpediente.findAll()) {
            todas.add(new Fila("EXPEDIENTE", ev.getExpediente().getNumeroExpediente(), ev.getTipo().name(),
                    ev.getUsuario(), ev.getRol(), ev.getFechaHora(), ev.getId()));
        }
        for (EventoFamilia ev : eventosFamilia.findAll()) {
            todas.add(new Fila("FAMILIA", ev.getFamilia().getNumeroSolicitud(), ev.getTipo().name(),
                    ev.getUsuario(), ev.getRol(), ev.getFechaHora(), ev.getId()));
        }
        for (EventoSistema ev : eventosSistema.findAll()) {
            todas.add(new Fila(ev.getEntidad(), ev.getReferencia(), ev.getTipo(),
                    ev.getUsuario(), ev.getRol(), ev.getFechaHora(), ev.getId()));
        }

        List<Fila> filtradas = todas.stream()
                .filter(f -> usuarioFiltro.isEmpty() || f.usuario().toLowerCase(Locale.ROOT).contains(usuarioFiltro))
                .filter(f -> tipoFiltro.isEmpty() || f.tipo().equals(tipoFiltro))
                .filter(f -> entidadFiltro.isEmpty() || f.entidad().equals(entidadFiltro))
                .filter(f -> desde == null || !f.fechaHora().toLocalDate().isBefore(desde))
                .filter(f -> hasta == null || !f.fechaHora().toLocalDate().isAfter(hasta))
                .sorted(Comparator.comparing(Fila::fechaHora).reversed()
                        .thenComparing(Fila::entidad).thenComparing(Fila::id, Comparator.reverseOrder()))
                .toList();

        int inicio = (int) Math.min((long) paginaSegura * tamanoSeguro, filtradas.size());
        int fin = Math.min(inicio + tamanoSeguro, filtradas.size());
        List<Map<String, Object>> contenido = filtradas.subList(inicio, fin).stream().map(this::aMapa).toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("contenido", contenido);
        respuesta.put("pagina", paginaSegura);
        respuesta.put("tamano", tamanoSeguro);
        respuesta.put("total", filtradas.size());
        respuesta.put("totalPaginas", filtradas.isEmpty() ? 0 : (filtradas.size() + tamanoSeguro - 1) / tamanoSeguro);
        return respuesta;
    }

    private Map<String, Object> aMapa(Fila f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entidad", f.entidad());
        m.put("referencia", f.referencia());
        m.put("tipo", f.tipo());
        m.put("usuario", f.usuario());
        m.put("rol", f.rol());
        m.put("fechaHora", f.fechaHora().format(FORMATO));
        m.put("accion", etiqueta(f.tipo()));
        return m;
    }

    private static String etiqueta(String tipo) {
        return switch (tipo) {
            case "CREACION" -> "Creación del registro";
            case "MODIFICACION" -> "Modificación del registro";
            case "CONSULTA" -> "Consulta del registro";
            case "CAMBIO_ESTADO" -> "Cambio de estado";
            case "DOCUMENTO" -> "Acceso o gestión de documento";
            case "DICTAMEN" -> "Dictamen de idoneidad";
            case "ESTADO_JURIDICO" -> "Cambio de etapa jurídica";
            case "ALTA_USUARIO" -> "Creación de cuenta de usuario";
            default -> tipo;
        };
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private record Fila(String entidad, String referencia, String tipo, String usuario, String rol,
                        LocalDateTime fechaHora, Long id) { }
}
