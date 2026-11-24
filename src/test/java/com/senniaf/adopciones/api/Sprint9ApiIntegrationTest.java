package com.senniaf.adopciones.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senniaf.adopciones.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la API del Sprint 9:
 *   HU-07.3 Resolucion judicial obligatoria para Declarado adoptable.
 *   HU-07.4 Historial juridico del menor.
 *   HU-08.1 Busqueda de familias compatibles por edad.
 *
 * Mismas propiedades que las otras pruebas de la API, para compartir un solo
 * arranque de Spring y la misma base H2 en memoria. Como la base es
 * compartida, las pruebas de busqueda comprueban sus propias familias por
 * numero de solicitud y no el total de resultados.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pruebas_api_familias;DB_CLOSE_DELAY=-1",
        "app.documentos.ruta=./target/documentos-prueba"
})
@AutoConfigureMockMvc
class Sprint9ApiIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);
    private static final String OBSERVACION = "Audiencia ante el Juzgado de Niñez y Adolescencia de Panamá.";
    // Sin tildes a proposito: el texto se compara tal cual, y sin
    // project.build.sourceEncoding en el pom Windows compila en Cp1252.
    private static final String JUZGADO = "Juzgado Primero de Ninez y Adolescencia de Panama";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    // ---------------------------------------------------------- utilidades

    private String token(String usuario, String clave) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(usuario, clave))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(cuerpo).get("token").asText();
    }

    private String admin() throws Exception { return token("admin", "Admin123!"); }
    private String tecnico() throws Exception { return token("tecnico1", "Tecnico123!"); }
    private String juez() throws Exception { return token("juez1", "Juez123!"); }

    private ResultActions json(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        var peticion = switch (metodo) {
            case "POST" -> post(ruta);
            case "PATCH" -> patch(ruta);
            default -> get(ruta);
        };
        if (token != null) peticion.header("Authorization", token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion);
    }

    /** Crea un menor con la fecha de nacimiento indicada y devuelve su numero. */
    private String crearExpediente(LocalDate nacimiento) throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("nombres", "Menor");
        d.put("apellidos", "Sprint Nueve");
        d.put("sexo", "F");
        d.put("fecha_nacimiento", nacimiento.toString());
        d.put("tipo_documento", "certificado_nacimiento");
        d.put("numero_documento", "9-900-" + SECUENCIA.getAndIncrement());
        d.put("provincia_nacimiento", "Panamá");
        d.put("distrito_nacimiento", "Panamá");
        d.put("fecha_ingreso", nacimiento.plusMonths(1).toString());
        d.put("modalidad_acogida", "centro_acogimiento");
        d.put("autoridad_remitente", "juzgado_ninez");
        d.put("motivo_ingreso", "negligencia");
        d.put("hermanos", "no");
        d.put("necesidades_especiales", "no");
        d.put("declaracion_autorizacion", true);
        d.put("declaracion_trazabilidad", true);
        String cuerpo = json("POST", "/api/tecnico/expedientes", tecnico(), d)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("numeroExpediente").asText();
    }

    private void aProcesoJudicial(String numero) throws Exception {
        json("PATCH", "/api/juez/expedientes/" + numero + "/estado-juridico", juez(),
                Map.of("etapa", "EN_PROCESO_JUDICIAL", "observacion", OBSERVACION))
                .andExpect(status().isOk());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "resolucion.pdf", "application/pdf",
                "%PDF-1.4 resolucion de prueba".getBytes(StandardCharsets.US_ASCII));
    }

    /** Peticion de declaracion de adoptabilidad con todos los datos, lista para quitarle alguno. */
    private MockMultipartHttpServletRequestBuilder declaracion(String numero, MockMultipartFile archivo,
                                                               Map<String, String> datos) {
        MockMultipartHttpServletRequestBuilder p =
                multipart("/api/juez/expedientes/" + numero + "/declaracion-adoptabilidad");
        if (archivo != null) p.file(archivo);
        datos.forEach(p::param);
        return p;
    }

    private Map<String, String> datosResolucion(String numeroResolucion) {
        Map<String, String> d = new HashMap<>();
        d.put("observacion", OBSERVACION);
        d.put("resolucion_numero", numeroResolucion);
        d.put("resolucion_fecha", LocalDate.now().minusDays(5).toString());
        d.put("resolucion_juzgado", JUZGADO);
        return d;
    }

    private String menorAdoptable(LocalDate nacimiento) throws Exception {
        String numero = crearExpediente(nacimiento);
        aProcesoJudicial(numero);
        mvc.perform(declaracion(numero, pdf(), datosResolucion("RES-" + numero)).header("Authorization", juez()))
                .andExpect(status().isOk());
        return numero;
    }

    /** Familia con el rango de edad indicado, llevada hasta el dictamen pedido. */
    private String familia(int edadMinima, int edadMaxima, String resultado) throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("nombres", "Familia");
        d.put("apellidos", "Busqueda " + edadMinima + "-" + edadMaxima);
        d.put("tipo_documento", "cedula");
        d.put("numero_documento", "9-800-" + SECUENCIA.getAndIncrement());
        d.put("fecha_nacimiento", "1982-03-15");
        d.put("ocupacion", "Docente");
        d.put("telefono", "6000-0000");
        d.put("tiene_segundo", "no");
        d.put("estado_civil", "casada");
        d.put("provincia_residencia", "Panamá");
        d.put("distrito_residencia", "Panamá");
        d.put("direccion", "Calle de prueba, casa 9");
        d.put("tipo_vivienda", "propia");
        d.put("habitaciones", 3);
        d.put("personas_hogar", 2);
        d.put("tiene_hijos", "no");
        d.put("ingreso_rango", "1000_2000");
        d.put("fuente_ingreso", "empleo");
        d.put("modalidad_solicitada", "adopcion");
        d.put("edad_minima", edadMinima);
        d.put("edad_maxima", edadMaxima);
        d.put("sexo_preferencia", "indistinto");
        d.put("acepta_hermanos", "si");
        d.put("acepta_necesidades", "no");
        d.put("motivacion", "Queremos dar un hogar estable a un menor.");
        d.put("declaracion_veracidad", true);
        d.put("declaracion_requisitos", true);
        d.put("declaracion_trazabilidad", true);
        String t = tecnico();
        String cuerpo = json("POST", "/api/tecnico/familias", t, d)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String numero = json.readTree(cuerpo).get("numeroSolicitud").asText();
        json("PATCH", "/api/tecnico/familias/" + numero + "/estado", t, Map.of("estado", "EN_EVALUACION"))
                .andExpect(status().isOk());
        json("POST", "/api/tecnico/familias/" + numero + "/dictamen", t,
                Map.of("resultado", resultado,
                       "observacion", "Evaluación psicosocial y socioeconómica completa del hogar."))
                .andExpect(status().isCreated());
        return numero;
    }

    // ------------------------------------------------------------- HU-07.3

    @Test
    @DisplayName("HU-07.3: el cambio normal a Declarado adoptable se rechaza (400) y pide la resolucion")
    void adoptableSinResolucion400() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(6));
        aProcesoJudicial(numero);

        json("PATCH", "/api/juez/expedientes/" + numero + "/estado-juridico", juez(),
                Map.of("etapa", "DECLARADO_ADOPTABLE", "observacion", OBSERVACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItems("resolucion_numero", "resolucion_fecha",
                        "resolucion_juzgado", "archivo")));

        json("GET", "/api/expedientes/" + numero, juez(), null)
                .andExpect(jsonPath("$.estadoJuridico").value("EN_PROCESO_JUDICIAL"));
    }

    @Test
    @DisplayName("HU-07.3: sin documento, con fecha futura o sin juzgado responde 400 nombrando cada dato")
    void resolucionIncompleta400() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(6));
        aProcesoJudicial(numero);
        Map<String, String> datos = datosResolucion("RES-INCOMPLETA");
        datos.put("resolucion_fecha", LocalDate.now().plusDays(2).toString());
        datos.put("resolucion_juzgado", "");

        mvc.perform(declaracion(numero, null, datos).header("Authorization", juez()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItems("resolucion_fecha", "resolucion_juzgado", "archivo")));

        json("GET", "/api/expedientes/" + numero, juez(), null)
                .andExpect(jsonPath("$.estadoJuridico").value("EN_PROCESO_JUDICIAL"));
    }

    @Test
    @DisplayName("HU-07.3: un archivo que no es lo que dice ser se rechaza (400) y la etapa no cambia")
    void documentoFalso400() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(6));
        aProcesoJudicial(numero);
        MockMultipartFile falso = new MockMultipartFile("archivo", "resolucion.pdf", "application/pdf",
                "MZ ejecutable disfrazado".getBytes(StandardCharsets.US_ASCII));

        mvc.perform(declaracion(numero, falso, datosResolucion("RES-FALSA")).header("Authorization", juez()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos", hasItem("archivo")));

        json("GET", "/api/expedientes/" + numero + "/documentos", juez(), null)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("HU-07.3: con la resolucion completa queda Declarado adoptable y el documento en el expediente")
    void declaraAdoptable200() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(6));
        aProcesoJudicial(numero);

        mvc.perform(declaracion(numero, pdf(), datosResolucion("RES-OK-1")).header("Authorization", juez()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoJuridico").value("DECLARADO_ADOPTABLE"));

        json("GET", "/api/expedientes/" + numero + "/documentos", tecnico(), null)
                .andExpect(jsonPath("$[0].categoria").value("resolucion"))
                .andExpect(jsonPath("$[0].nombre").value("resolucion.pdf"));
    }

    @Test
    @DisplayName("HU-07.3: solo el Juez declara adoptable (Tecnico y Administrador: 403) y solo desde En proceso judicial (409)")
    void declaracionPermisosYTransicion() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(6));

        mvc.perform(declaracion(numero, pdf(), datosResolucion("RES-PERM")).header("Authorization", tecnico()))
                .andExpect(status().isForbidden());
        mvc.perform(declaracion(numero, pdf(), datosResolucion("RES-PERM")).header("Authorization", admin()))
                .andExpect(status().isForbidden());
        // Sigue En proteccion: todavia no hay proceso judicial.
        mvc.perform(declaracion(numero, pdf(), datosResolucion("RES-PERM")).header("Authorization", juez()))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------- HU-07.4

    @Test
    @DisplayName("HU-07.4: el historial juridico muestra el recorrido del menor en orden, con la resolucion")
    void historialJuridico() throws Exception {
        String numero = menorAdoptable(LocalDate.now().minusYears(7));

        String cuerpo = json("GET", "/api/expedientes/" + numero + "/historial-juridico", tecnico(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaActual").value("DECLARADO_ADOPTABLE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode linea = json.readTree(cuerpo).get("linea");

        List<String> etapas = new ArrayList<>();
        linea.forEach(l -> etapas.add(l.get("etapa").asText()));
        assertEquals(List.of("EN_PROTECCION", "EN_PROCESO_JUDICIAL", "DECLARADO_ADOPTABLE"), etapas);
        JsonNode ultima = linea.get(2);
        assertEquals("EN_PROCESO_JUDICIAL", ultima.get("etapaAnterior").asText());
        assertEquals("RES-" + numero, ultima.get("resolucion").get("numero").asText());
        assertEquals(JUZGADO, ultima.get("resolucion").get("juzgado").asText());
        assertTrue(ultima.get("resolucion").get("documentoId").asLong() > 0);
        assertEquals("juez1", ultima.get("usuario").asText());
    }

    @Test
    @DisplayName("HU-07.4: el historial juridico no incluye consultas ni documentos, solo cambios de etapa")
    void historialJuridicoSoloEtapas() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(4));
        // Consultar la ficha deja lineas en el historial general, no en el juridico.
        json("GET", "/api/expedientes/" + numero, tecnico(), null).andExpect(status().isOk());
        json("GET", "/api/expedientes/" + numero, juez(), null).andExpect(status().isOk());

        json("GET", "/api/expedientes/" + numero + "/historial-juridico", juez(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.linea[0].etapa").value("EN_PROTECCION"));
    }

    @Test
    @DisplayName("HU-07.4: el Administrador no ve el historial juridico (403); sin token, 401")
    void historialJuridicoPermisos() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(4));

        json("GET", "/api/expedientes/" + numero + "/historial-juridico", admin(), null)
                .andExpect(status().isForbidden());
        json("GET", "/api/expedientes/" + numero + "/historial-juridico", null, null)
                .andExpect(status().isUnauthorized());
        json("GET", "/api/expedientes/EXP-NO-EXISTE/historial-juridico", tecnico(), null)
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- HU-08.1

    @Test
    @DisplayName("HU-08.1: solo aparecen familias idoneas cuyo rango incluye la edad del menor")
    void busquedaPorEdad() throws Exception {
        // 15 anos cumplidos: un rango 14-16 lo incluye; 0-5 no.
        String numero = menorAdoptable(LocalDate.now().minusYears(15).minusMonths(2));
        String compatible = familia(14, 16, "IDONEA");
        String justoEnElLimite = familia(15, 15, "IDONEA");
        String fueraDeRango = familia(0, 5, "IDONEA");
        String noIdonea = familia(14, 16, "NO_IDONEA");

        json("GET", "/api/tecnico/expedientes/" + numero + "/familias-compatibles", tecnico(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edad").value(15))
                .andExpect(jsonPath("$.familias[*].numeroSolicitud", hasItems(compatible, justoEnElLimite)))
                .andExpect(jsonPath("$.familias[*].numeroSolicitud", not(hasItem(fueraDeRango))))
                .andExpect(jsonPath("$.familias[*].numeroSolicitud", not(hasItem(noIdonea))));
    }

    @Test
    @DisplayName("HU-08.1: las familias salen en el orden de la lista de espera")
    void busquedaEnOrdenDeLista() throws Exception {
        String numero = menorAdoptable(LocalDate.now().minusYears(16).minusMonths(3));
        String primera = familia(16, 17, "IDONEA");
        String segunda = familia(16, 17, "IDONEA");

        String cuerpo = json("GET", "/api/tecnico/expedientes/" + numero + "/familias-compatibles", tecnico(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> orden = new ArrayList<>();
        json.readTree(cuerpo).get("familias").forEach(f -> orden.add(f.get("numeroSolicitud").asText()));
        assertTrue(orden.indexOf(primera) >= 0 && orden.indexOf(primera) < orden.indexOf(segunda),
                "La familia que fue idonea primero debe ir antes: " + orden);
    }

    @Test
    @DisplayName("HU-08.1: un menor que no esta Declarado adoptable no habilita la busqueda (409)")
    void busquedaSinAdoptabilidad409() throws Exception {
        String numero = crearExpediente(LocalDate.now().minusYears(5));
        aProcesoJudicial(numero);

        json("GET", "/api/tecnico/expedientes/" + numero + "/familias-compatibles", tecnico(), null)
                .andExpect(status().isConflict());
        json("GET", "/api/tecnico/expedientes/EXP-NO-EXISTE/familias-compatibles", tecnico(), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-08.1: solo el Equipo Tecnico busca familias (Juez y Administrador: 403)")
    void busquedaPermisos() throws Exception {
        String numero = menorAdoptable(LocalDate.now().minusYears(3));

        json("GET", "/api/tecnico/expedientes/" + numero + "/familias-compatibles", juez(), null)
                .andExpect(status().isForbidden());
        json("GET", "/api/tecnico/expedientes/" + numero + "/familias-compatibles", admin(), null)
                .andExpect(status().isForbidden());
    }
}
