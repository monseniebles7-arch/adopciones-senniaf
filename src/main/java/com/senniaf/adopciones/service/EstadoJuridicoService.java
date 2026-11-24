package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.CambioEtapaJuridica;
import com.senniaf.adopciones.model.DocumentoExpediente;
import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.EventoExpediente;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.ResolucionJudicial;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.CambioEtapaJuridicaRepository;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import com.senniaf.adopciones.repository.ResolucionJudicialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Etapa juridica del menor y reapertura de expedientes (Sprints 8 y 9).
 *
 * HU-07.2 y decisiones del PO:
 *   1. Con el expediente Cerrado no se cambia la etapa: primero se reabre.
 *   2. Solo se avanza; no se vuelve a una etapa anterior.
 *   3. Cambiar la etapa exige una observacion de al menos 20 caracteres.
 *   4. Solo el Equipo Tecnico reabre un expediente cerrado, con motivo.
 *
 * HU-07.3 (Sprint 9): pasar a Declarado adoptable exige registrar la
 * resolucion judicial (numero, fecha, juzgado y documento). Por eso ese paso
 * tiene su propio metodo, declararAdoptable, y cambiarEtapa lo rechaza.
 *
 * HU-07.4 (Sprint 9): cada cambio de etapa queda ademas en la tabla
 * cambios_etapa_juridica, de la que se arma el historial juridico.
 *
 * Quien puede llamar a cada metodo lo decide la ruta (SecurityConfig):
 * cambiarEtapa y declararAdoptable estan bajo /api/juez/** y reabrir bajo
 * /api/tecnico/**.
 */
@Service
public class EstadoJuridicoService {

    static final int MINIMO_TEXTO = 20;
    static final int MAXIMO_TEXTO = 1000;
    static final int MINIMO_DATO_RESOLUCION = 3;
    static final int MAXIMO_NUMERO_RESOLUCION = 60;
    static final int MAXIMO_JUZGADO = 150;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Nombre de cada dato de la resolucion, para los mensajes de error. */
    private static final Map<String, String> NOMBRE_CAMPO = Map.of(
            "observacion", "la observación (al menos " + MINIMO_TEXTO + " caracteres)",
            "resolucion_numero", "el número de la resolución",
            "resolucion_fecha", "la fecha de la resolución (no puede ser futura)",
            "resolucion_juzgado", "el juzgado que la emitió",
            "archivo", "el documento de la resolución");

    /** El controlador traduce cada motivo a su codigo HTTP: 404, 400 o 409. */
    public enum Motivo { NO_ENCONTRADO, DATOS_INVALIDOS, CONFLICTO }

    public static class Rechazo extends RuntimeException {
        private final Motivo motivo;
        private final List<String> campos;

        public Rechazo(Motivo motivo, String mensaje, List<String> campos) {
            super(mensaje);
            this.motivo = motivo;
            this.campos = campos == null ? List.of() : List.copyOf(campos);
        }

        public Motivo getMotivo() { return motivo; }
        public List<String> getCampos() { return campos; }
    }

    private final ExpedienteRepository expedientes;
    private final ExpedienteService expedienteService;
    private final DocumentoService documentos;
    private final ResolucionJudicialRepository resoluciones;
    private final CambioEtapaJuridicaRepository cambios;

    public EstadoJuridicoService(ExpedienteRepository expedientes, ExpedienteService expedienteService,
                                 DocumentoService documentos, ResolucionJudicialRepository resoluciones,
                                 CambioEtapaJuridicaRepository cambios) {
        this.expedientes = expedientes;
        this.expedienteService = expedienteService;
        this.documentos = documentos;
        this.resoluciones = resoluciones;
        this.cambios = cambios;
    }

    // =====================================================================
    //  HU-07.2 · Cambiar la etapa (solo Juez)
    // =====================================================================

    @Transactional
    public Expediente cambiarEtapa(String numero, Map<String, Object> datos, Usuario usuario) {
        Expediente e = buscarAbierto(numero);

        // Datos: etapa valida y observacion suficiente (decision 3).
        List<String> malos = new ArrayList<>();
        EstadoJuridico nueva = leerEtapa(datos.get("etapa"));
        if (nueva == null) {
            malos.add("etapa");
        }
        String observacion = texto(datos.get("observacion"));
        if (!observacionValida(observacion)) {
            malos.add("observacion");
        }
        if (!malos.isEmpty()) {
            String mensaje = malos.contains("etapa")
                    ? "La etapa indicada no existe."
                    : "El cambio de etapa exige una observación de entre " + MINIMO_TEXTO
                      + " y " + MAXIMO_TEXTO + " caracteres.";
            throw new Rechazo(Motivo.DATOS_INVALIDOS, mensaje, malos);
        }

        EstadoJuridico actual = e.getEstadoJuridico();
        validarTransicion(actual, nueva);

        // HU-07.3: Declarado adoptable no se marca sin resolucion judicial.
        if (nueva == EstadoJuridico.DECLARADO_ADOPTABLE) {
            throw new Rechazo(Motivo.DATOS_INVALIDOS,
                    "Para pasar a Declarado adoptable hay que registrar la resolución judicial: "
                    + "número, fecha, juzgado y documento.",
                    List.of("resolucion_numero", "resolucion_fecha", "resolucion_juzgado", "archivo"));
        }

        aplicarCambio(e, actual, nueva, observacion, null, usuario);
        return e;
    }

    // =====================================================================
    //  HU-07.3 · Declarar adoptable con resolucion judicial (solo Juez)
    // =====================================================================

    /**
     * Pasa al menor a Declarado adoptable registrando la resolucion que lo
     * sustenta. Los datos llegan como texto desde un formulario multipart:
     * observacion, resolucion_numero, resolucion_fecha (AAAA-MM-DD) y
     * resolucion_juzgado, mas el archivo.
     *
     * Primero se valida todo y despues se guarda: si algo falta, no queda
     * ningun archivo ni cambio a medias.
     */
    @Transactional
    public Expediente declararAdoptable(String numero, Map<String, String> datos, MultipartFile archivo,
                                        Usuario usuario) {
        Expediente e = buscarAbierto(numero);
        EstadoJuridico actual = e.getEstadoJuridico();
        validarTransicion(actual, EstadoJuridico.DECLARADO_ADOPTABLE);

        String observacion = texto(datos.get("observacion"));
        String numeroResolucion = texto(datos.get("resolucion_numero"));
        String juzgado = texto(datos.get("resolucion_juzgado"));
        LocalDate fecha = leerFecha(datos.get("resolucion_fecha"));

        List<String> malos = new ArrayList<>();
        if (!observacionValida(observacion)) malos.add("observacion");
        if (numeroResolucion.length() < MINIMO_DATO_RESOLUCION
                || numeroResolucion.length() > MAXIMO_NUMERO_RESOLUCION) malos.add("resolucion_numero");
        if (fecha == null || fecha.isAfter(LocalDate.now())) malos.add("resolucion_fecha");
        if (juzgado.length() < MINIMO_DATO_RESOLUCION || juzgado.length() > MAXIMO_JUZGADO) {
            malos.add("resolucion_juzgado");
        }
        if (archivo == null || archivo.isEmpty()) malos.add("archivo");
        if (!malos.isEmpty()) {
            throw new Rechazo(Motivo.DATOS_INVALIDOS, "Falta o no es válido: "
                    + malos.stream().map(NOMBRE_CAMPO::get).collect(Collectors.joining(", ")) + ".", malos);
        }

        // El documento se valida (tipo, tamano y contenido) y se guarda cifrado
        // con las mismas reglas de HU-04, en la categoria "resolucion".
        DocumentoExpediente documento;
        try {
            documento = documentos.adjuntar(e, archivo, "resolucion", usuario);
        } catch (DocumentoService.DocumentoInvalido ex) {
            throw new Rechazo(Motivo.DATOS_INVALIDOS, ex.getMessage(), List.of("archivo"));
        }

        ResolucionJudicial resolucion = resoluciones.save(new ResolucionJudicial(
                e, numeroResolucion, fecha, juzgado, documento, usuario.getUsername()));

        aplicarCambio(e, actual, EstadoJuridico.DECLARADO_ADOPTABLE, observacion, resolucion, usuario);
        return e;
    }

    // =====================================================================
    //  Decision 4 · Reabrir (solo Equipo Técnico)
    // =====================================================================

    @Transactional
    public Expediente reabrir(String numero, Map<String, Object> datos, Usuario usuario) {
        Expediente e = expedientes.buscarParaCambio(numero)
                .orElseThrow(() -> new Rechazo(Motivo.NO_ENCONTRADO,
                        "No existe un expediente con el número " + numero, null));

        if (e.getEstado() != EstadoExpediente.CERRADO) {
            throw new Rechazo(Motivo.CONFLICTO, "Solo un expediente cerrado se puede reabrir.", null);
        }
        String motivo = texto(datos.get("motivo"));
        if (motivo.length() < MINIMO_TEXTO || motivo.length() > MAXIMO_TEXTO) {
            throw new Rechazo(Motivo.DATOS_INVALIDOS, "Reabrir un expediente exige un motivo de entre "
                    + MINIMO_TEXTO + " y " + MAXIMO_TEXTO + " caracteres.", List.of("motivo"));
        }

        LocalDateTime cierreAutomatico = e.getFechaCierreAutomatico();
        LocalDateTime ahora = LocalDateTime.now();
        e.setEstado(EstadoExpediente.EN_REVISION);
        e.setFechaCierreAutomatico(null);
        e.setFechaActualizacion(ahora);
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        expedienteService.registrar(e, TipoEvento.CAMBIO_ESTADO,
                recortar("Expediente reabierto: Cerrado -> En revisión"
                        + (cierreAutomatico != null
                           ? " (lo había cerrado el sistema el " + cierreAutomatico.format(FECHA_HORA) + ")"
                           : "")
                        + ". Motivo: " + motivo), usuario);
        return e;
    }

    // =====================================================================
    //  Ajuste de datos al arrancar (HU-07.1, criterio 2)
    // =====================================================================

    @Transactional
    public int asignarEtapaInicial() {
        return expedientes.asignarEtapaInicial(EstadoJuridico.EN_PROTECCION);
    }

    // =====================================================================

    /** Decision 1: un expediente cerrado no admite cambios de etapa. */
    private Expediente buscarAbierto(String numero) {
        Expediente e = expedientes.buscarParaCambio(numero)
                .orElseThrow(() -> new Rechazo(Motivo.NO_ENCONTRADO,
                        "No existe un expediente con el número " + numero, null));
        if (e.getEstado() == EstadoExpediente.CERRADO) {
            throw new Rechazo(Motivo.CONFLICTO,
                    "El expediente está cerrado: el Equipo Técnico debe reabrirlo antes de cambiar la etapa jurídica.",
                    null);
        }
        return e;
    }

    /** HU-07.2, criterios 2 y 3: solo transiciones hacia adelante. */
    private static void validarTransicion(EstadoJuridico actual, EstadoJuridico nueva) {
        if (actual.esFinal()) {
            throw new Rechazo(Motivo.CONFLICTO,
                    actual.getEtiqueta() + " es una etapa final: ya no admite cambios.", null);
        }
        if (!actual.puedePasarA(nueva)) {
            String permitidas = actual.siguientes().stream()
                    .map(EstadoJuridico::getEtiqueta).collect(Collectors.joining(" o "));
            throw new Rechazo(Motivo.CONFLICTO,
                    "Desde " + actual.getEtiqueta() + " solo se puede pasar a " + permitidas + ".", null);
        }
    }

    /** Guarda la etapa nueva y deja el cambio en los dos historiales. */
    private void aplicarCambio(Expediente e, EstadoJuridico actual, EstadoJuridico nueva, String observacion,
                               ResolucionJudicial resolucion, Usuario usuario) {
        LocalDateTime ahora = LocalDateTime.now();
        e.setEstadoJuridico(nueva);
        e.setFechaEstadoJuridico(ahora);
        e.setFechaActualizacion(ahora);
        e.setActualizadoPor(usuario.getUsername());
        expedientes.save(e);

        String descripcion = "Etapa jurídica: " + actual.getEtiqueta() + " -> " + nueva.getEtiqueta()
                + ". Observación: " + observacion;
        if (resolucion != null) {
            descripcion += " Resolución N.º " + resolucion.getNumeroResolucion() + " del "
                    + resolucion.getFechaResolucion() + ", " + resolucion.getJuzgado() + ".";
        }
        EventoExpediente evento = expedienteService.registrar(e, TipoEvento.ESTADO_JURIDICO,
                recortar(descripcion), usuario);

        cambios.save(new CambioEtapaJuridica(e, actual, nueva, observacion, resolucion,
                usuario.getUsername(), usuario.getRol().name(), evento == null ? null : evento.getId()));
    }

    /** Datos de la resolucion para mostrar en el historial juridico. */
    public static Map<String, Object> aMapa(ResolucionJudicial r) {
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero", r.getNumeroResolucion());
        m.put("fecha", r.getFechaResolucion().toString());
        m.put("juzgado", r.getJuzgado());
        m.put("documentoId", r.getDocumento().getId());
        m.put("documentoNombre", r.getDocumento().getNombreOriginal());
        m.put("registradaPor", r.getRegistradaPor());
        return m;
    }

    private static boolean observacionValida(String observacion) {
        return observacion.length() >= MINIMO_TEXTO && observacion.length() <= MAXIMO_TEXTO;
    }

    private static EstadoJuridico leerEtapa(Object valor) {
        if (valor == null) return null;
        try {
            return EstadoJuridico.valueOf(valor.toString().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDate leerFecha(Object valor) {
        String t = texto(valor);
        if (t.isEmpty()) return null;
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String recortar(String s) {
        return s.length() <= 1000 ? s : s.substring(0, 999) + "…";
    }
}
