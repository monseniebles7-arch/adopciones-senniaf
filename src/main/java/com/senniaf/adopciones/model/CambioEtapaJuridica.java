package com.senniaf.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una linea del historial juridico del menor (HU-07.4, Sprint 9).
 *
 * El historial general del expediente mezcla consultas, documentos y
 * cambios de datos. Esta tabla guarda solo los cambios de etapa juridica,
 * con sus datos separados (etapa anterior, etapa nueva, observacion y
 * resolucion), para poder reconstruir el recorrido legal del caso.
 *
 * Las etapas se guardan como texto (el nombre del enum) y no como columna
 * ENUM de H2: asi agregar una etapa en el futuro no exige tocar la base
 * (ver config/EsquemaEventos).
 *
 * Igual que el historial general, las filas no se editan ni se borran.
 */
@Entity
@Table(name = "cambios_etapa_juridica",
       indexes = @Index(name = "idx_cambio_etapa_expediente", columnList = "expediente_id"))
public class CambioEtapaJuridica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Column(name = "etapa_anterior", nullable = false, length = 30)
    private String etapaAnterior;

    @Column(name = "etapa_nueva", nullable = false, length = 30)
    private String etapaNueva;

    @Column(nullable = false, length = 1000)
    private String observacion;

    /** Solo para el paso a Declarado adoptable (HU-07.3). */
    @ManyToOne
    @JoinColumn(name = "resolucion_id")
    private ResolucionJudicial resolucion;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(nullable = false, length = 20)
    private String rol;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    /**
     * La linea del historial general que registro el mismo cambio. Sirve
     * para no mostrar dos veces un cambio: los cambios del Sprint 8 solo
     * existen en el historial general y se leen desde ahi.
     */
    @Column(name = "evento_id")
    private Long eventoId;

    protected CambioEtapaJuridica() {
        // Requerido por JPA
    }

    public CambioEtapaJuridica(Expediente expediente, EstadoJuridico anterior, EstadoJuridico nueva,
                               String observacion, ResolucionJudicial resolucion,
                               String usuario, String rol, Long eventoId) {
        this.expediente = expediente;
        this.etapaAnterior = anterior.name();
        this.etapaNueva = nueva.name();
        this.observacion = observacion;
        this.resolucion = resolucion;
        this.usuario = usuario;
        this.rol = rol;
        this.eventoId = eventoId;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Expediente getExpediente() { return expediente; }
    public String getEtapaAnterior() { return etapaAnterior; }
    public String getEtapaNueva() { return etapaNueva; }
    public String getObservacion() { return observacion; }
    public ResolucionJudicial getResolucion() { return resolucion; }
    public String getUsuario() { return usuario; }
    public String getRol() { return rol; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public Long getEventoId() { return eventoId; }
}
