package com.senniaf.adopciones.service;

import com.senniaf.adopciones.repository.EventoFamiliaRepository;
import com.senniaf.adopciones.repository.FamiliaRepository;
import com.senniaf.adopciones.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamiliaDocumentosDuplicadosTest {

    @Mock private FamiliaRepository familias;
    @Mock private EventoFamiliaRepository eventos;
    @Mock private UsuarioRepository usuarios;
    @InjectMocks private FamiliaService servicio;

    @Test
    @DisplayName("Normaliza y compara ambos documentos contra los dos documentos existentes")
    void comparaDocumentosNormalizadosDeAmbosSolicitantes() {
        when(familias.contarPorDocumentosNormalizados("89001234", "P12345")).thenReturn(1L);

        assertTrue(servicio.documentosYaRegistrados(" 8-900-1234 ", "P/123.45"));

        verify(familias).contarPorDocumentosNormalizados("89001234", "P12345");
    }

    @Test
    @DisplayName("No consulta el repositorio si no hay documentos")
    void noConsultaSinDocumentos() {
        assertFalse(servicio.documentosYaRegistrados("  ", null));

        verify(familias, never()).contarPorDocumentosNormalizados(null, null);
    }
}
