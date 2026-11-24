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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Resolucion judicial que sustenta la adoptabilidad de un menor (HU-07.3, Sprint 9).
 *
 * Sin una resolucion registrada, con su numero, fecha, juzgado y documento,
 * el Juez no puede pasar al menor a Declarado adoptable. El documento se
 * guarda cifrado como cualquier otro documento del expediente (HU-04), en la
 * categoria "resolucion"; aqui solo se guarda la referencia.
 */
@Entity
@Table(name = "resoluciones_judiciales",
       indexes = @Index(name = "idx_resolucion_expediente", columnList = "expediente_id"))
public class ResolucionJudicial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Column(name = "numero_resolucion", nullable = false, length = 60)
    private String numeroResolucion;

    @Column(name = "fecha_resolucion", nullable = false)
    private LocalDate fechaResolucion;

    @Column(nullable = false, length = 150)
    private String juzgado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoExpediente documento;

    @Column(name = "registrada_por", nullable = false, length = 60)
    private String registradaPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    protected ResolucionJudicial() {
        // Requerido por JPA
    }

    public ResolucionJudicial(Expediente expediente, String numeroResolucion, LocalDate fechaResolucion,
                              String juzgado, DocumentoExpediente documento, String registradaPor) {
        this.expediente = expediente;
        this.numeroResolucion = numeroResolucion;
        this.fechaResolucion = fechaResolucion;
        this.juzgado = juzgado;
        this.documento = documento;
        this.registradaPor = registradaPor;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Expediente getExpediente() { return expediente; }
    public String getNumeroResolucion() { return numeroResolucion; }
    public LocalDate getFechaResolucion() { return fechaResolucion; }
    public String getJuzgado() { return juzgado; }
    public DocumentoExpediente getDocumento() { return documento; }
    public String getRegistradaPor() { return registradaPor; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
