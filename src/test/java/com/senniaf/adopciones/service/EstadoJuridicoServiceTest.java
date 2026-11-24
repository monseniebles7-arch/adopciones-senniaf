package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.model.CambioEtapaJuridica;
import com.senniaf.adopciones.model.DocumentoExpediente;
import com.senniaf.adopciones.model.ResolucionJudicial;
import com.senniaf.adopciones.repository.CambioEtapaJuridicaRepository;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import com.senniaf.adopciones.repository.ResolucionJudicialRepository;
import org.springframework.mock.web.MockMultipartFile;
import com.senniaf.adopciones.service.EstadoJuridicoService.Motivo;
import com.senniaf.adopciones.service.EstadoJuridicoService.Rechazo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de HU-07.2 (cambiar la etapa juridica) y de la
 * reapertura de expedientes (decision del PO, Sprint 8).
 */
@ExtendWith(MockitoExtension.class)
class EstadoJuridicoServiceTest {

    private static final String NUMERO = "EXP-2026-0001";
    private static final String OBSERVACION = "Sentencia del Juzgado de Niñez del 10 de noviembre.";

    @Mock private ExpedienteRepository expedientes;
    @Mock private ExpedienteService expedienteService;
    // Sprint 9: resolucion judicial (HU-07.3) e historial juridico (HU-07.4).
    @Mock private DocumentoService documentos;
    @Mock private ResolucionJudicialRepository resoluciones;
    @Mock private CambioEtapaJuridicaRepository cambios;
    @InjectMocks private EstadoJuridicoService servicio;

    private Expediente expediente;
    private Usuario juez;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        expediente = new Expediente();
        expediente.setNumeroExpediente(NUMERO);
        expediente.setEstado(EstadoExpediente.EN_REVISION);
        expediente.setEstadoJuridico(EstadoJuridico.EN_PROTECCION);
        juez = new Usuario("juez1", "$2a$10$hashSimuladoDePrueba", Rol.JUEZ);
        admin = new Usuario("admin", "$2a$10$hashSimuladoDePrueba", Rol.ADMINISTRADOR);
    }

    private void encontrado() {
        when(expedientes.buscarParaCambio(NUMERO)).thenReturn(Optional.of(expediente));
    }

    private Map<String, Object> cambio(String etapa, String observacion) {
        return Map.of("etapa", etapa, "observacion", observacion);
    }

    // ------------------------------------------------------------- HU-07.3

    private Map<String, String> resolucionCompleta() {
        Map<String, String> d = new java.util.HashMap<>();
        d.put("observacion", OBSERVACION);
        d.put("resolucion_numero", "RES-123-2026");
        d.put("resolucion_fecha", "2026-11-10");
        d.put("resolucion_juzgado", "Juzgado de Niñez y Adolescencia de Panamá");
        return d;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "resolucion.pdf", "application/pdf",
                "%PDF-1.4 prueba".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("HU-07.3: el cambio normal no puede pasar a Declarado adoptable sin resolucion")
    void adoptableSinResolucionSeRechaza() {
        expediente.setEstadoJuridico(EstadoJuridico.EN_PROCESO_JUDICIAL);
        encontrado();

        Rechazo r = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("DECLARADO_ADOPTABLE", OBSERVACION), juez));

        assertEquals(Motivo.DATOS_INVALIDOS, r.getMotivo());
        assertTrue(r.getCampos().contains("archivo"));
        assertEquals(EstadoJuridico.EN_PROCESO_JUDICIAL, expediente.getEstadoJuridico());
        verify(expedientes, never()).save(any());
    }

    @Test
    @DisplayName("HU-07.3: si falta un dato de la resolucion, el error nombra cual falta y no guarda nada")
    void resolucionIncompletaNombraElDato() {
        expediente.setEstadoJuridico(EstadoJuridico.EN_PROCESO_JUDICIAL);
        encontrado();
        Map<String, String> datos = resolucionCompleta();
        datos.put("resolucion_juzgado", "");
        datos.put("resolucion_fecha", java.time.LocalDate.now().plusDays(3).toString());

        Rechazo r = assertThrows(Rechazo.class, () -> servicio.declararAdoptable(NUMERO, datos, null, juez));

        assertEquals(Motivo.DATOS_INVALIDOS, r.getMotivo());
        assertEquals(java.util.List.of("resolucion_fecha", "resolucion_juzgado", "archivo"), r.getCampos());
        assertTrue(r.getMessage().contains("el juzgado que la emitió"));
        verify(documentos, never()).adjuntar(any(), any(), anyString(), any());
        verify(resoluciones, never()).save(any());
    }

    @Test
    @DisplayName("HU-07.3: con la resolucion completa el menor queda Declarado adoptable y el cambio enlaza la resolucion")
    void declaraAdoptableConResolucion() {
        expediente.setEstadoJuridico(EstadoJuridico.EN_PROCESO_JUDICIAL);
        encontrado();
        when(documentos.adjuntar(eq(expediente), any(), eq("resolucion"), eq(juez)))
                .thenReturn(org.mockito.Mockito.mock(DocumentoExpediente.class));
        when(resoluciones.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.declararAdoptable(NUMERO, resolucionCompleta(), pdf(), juez);

        assertEquals(EstadoJuridico.DECLARADO_ADOPTABLE, expediente.getEstadoJuridico());
        verify(resoluciones).save(any(ResolucionJudicial.class));
        verify(cambios).save(org.mockito.ArgumentMatchers.argThat((CambioEtapaJuridica c) ->
                c.getEtapaNueva().equals("DECLARADO_ADOPTABLE") && c.getResolucion() != null
                        && c.getResolucion().getNumeroResolucion().equals("RES-123-2026")));
        verify(expedienteService).registrar(eq(expediente), eq(TipoEvento.ESTADO_JURIDICO),
                contains("Resolución N.º RES-123-2026"), eq(juez));
    }

    @Test
    @DisplayName("HU-07.3: solo se declara adoptable desde En proceso judicial")
    void adoptableDesdeProteccionEsConflicto() {
        encontrado();   // el expediente esta En proteccion

        Rechazo r = assertThrows(Rechazo.class,
                () -> servicio.declararAdoptable(NUMERO, resolucionCompleta(), pdf(), juez));

        assertEquals(Motivo.CONFLICTO, r.getMotivo());
        verify(documentos, never()).adjuntar(any(), any(), anyString(), any());
    }

    // ------------------------------------------------------------- HU-07.2

    @Test
    @DisplayName("HU-07.2: el Juez avanza la etapa y queda en el historial")
    void cambiaLaEtapa() {
        encontrado();

        servicio.cambiarEtapa(NUMERO, cambio("EN_PROCESO_JUDICIAL", OBSERVACION), juez);

        assertEquals(EstadoJuridico.EN_PROCESO_JUDICIAL, expediente.getEstadoJuridico());
        assertNotNull(expediente.getFechaEstadoJuridico());
        verify(expedienteService).registrar(eq(expediente), eq(TipoEvento.ESTADO_JURIDICO),
                contains("En protección -> En proceso judicial"), eq(juez));
    }

    @Test
    @DisplayName("Decision 1: con el expediente cerrado no se cambia la etapa")
    void cerradoNoAdmiteCambio() {
        expediente.setEstado(EstadoExpediente.CERRADO);
        encontrado();

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("EN_PROCESO_JUDICIAL", OBSERVACION), juez));

        assertEquals(Motivo.CONFLICTO, ex.getMotivo());
        verify(expedientes, never()).save(any());
    }

    @Test
    @DisplayName("HU-07.2 c2: saltarse una etapa se rechaza")
    void saltoNoPermitido() {
        encontrado();

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("ADOPTADO", OBSERVACION), juez));

        assertEquals(Motivo.CONFLICTO, ex.getMotivo());
        assertEquals(EstadoJuridico.EN_PROTECCION, expediente.getEstadoJuridico());
    }

    @Test
    @DisplayName("HU-07.2 c3: una etapa final no admite mas cambios")
    void etapaFinalNoCambia() {
        expediente.setEstadoJuridico(EstadoJuridico.ADOPTADO);
        encontrado();

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("REINTEGRADO", OBSERVACION), juez));

        assertEquals(Motivo.CONFLICTO, ex.getMotivo());
        assertTrue(ex.getMessage().contains("etapa final"));
    }

    @Test
    @DisplayName("Decision 3: la observacion es obligatoria y de al menos 20 caracteres")
    void observacionCorta() {
        encontrado();

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("EN_PROCESO_JUDICIAL", "Corta."), juez));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("observacion"));
    }

    @Test
    @DisplayName("Una etapa que no existe se rechaza indicando el campo")
    void etapaInexistente() {
        encontrado();

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa(NUMERO, cambio("LIBERADO", OBSERVACION), juez));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("etapa"));
    }

    @Test
    @DisplayName("Un expediente inexistente se informa como no encontrado")
    void expedienteInexistente() {
        when(expedientes.buscarParaCambio("EXP-1999-9999")).thenReturn(Optional.empty());

        Rechazo ex = assertThrows(Rechazo.class,
                () -> servicio.cambiarEtapa("EXP-1999-9999", cambio("EN_PROCESO_JUDICIAL", OBSERVACION), juez));

        assertEquals(Motivo.NO_ENCONTRADO, ex.getMotivo());
    }

    // ------------------------------------------------------- Reapertura

    @Test
    @DisplayName("Decision 4: el Administrador reabre un expediente cerrado por el sistema")
    void reabreUnCierreAutomatico() {
        expediente.setEstado(EstadoExpediente.CERRADO);
        expediente.setFechaCierreAutomatico(LocalDateTime.now().minusDays(1));
        encontrado();

        servicio.reabrir(NUMERO, Map.of("motivo", "Falta inscribir la sentencia en el Registro Civil."), admin);

        assertEquals(EstadoExpediente.EN_REVISION, expediente.getEstado());
        assertNull(expediente.getFechaCierreAutomatico());
        verify(expedienteService).registrar(eq(expediente), eq(TipoEvento.CAMBIO_ESTADO),
                contains("lo había cerrado el sistema"), eq(admin));
    }

    @Test
    @DisplayName("Decision 4: reabrir exige motivo y un expediente cerrado")
    void reabrirExigeMotivoYCerrado() {
        expediente.setEstado(EstadoExpediente.CERRADO);
        encontrado();
        Rechazo sinMotivo = assertThrows(Rechazo.class,
                () -> servicio.reabrir(NUMERO, Map.of("motivo", "Porque si."), admin));
        assertEquals(Motivo.DATOS_INVALIDOS, sinMotivo.getMotivo());

        expediente.setEstado(EstadoExpediente.EN_REVISION);
        Rechazo abierto = assertThrows(Rechazo.class,
                () -> servicio.reabrir(NUMERO, Map.of("motivo", "Falta inscribir la sentencia."), admin));
        assertEquals(Motivo.CONFLICTO, abierto.getMotivo());
        verify(expedienteService, never()).registrar(any(), any(), anyString(), any(Usuario.class));
    }
}
