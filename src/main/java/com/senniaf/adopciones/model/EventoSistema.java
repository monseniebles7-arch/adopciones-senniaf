package com.senniaf.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Auditoria de acciones administrativas que no pertenecen a una ficha de caso. */
@Entity
@Table(name = "eventos_sistema",
       indexes = @Index(name = "idx_evento_sistema_fecha", columnList = "fecha_hora"))
public class EventoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 40)
    private String entidad;

    @Column(nullable = false, length = 60)
    private String referencia;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(nullable = false, length = 20)
    private String rol;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected EventoSistema() { }

    public EventoSistema(String tipo, String entidad, String referencia, String usuario, String rol) {
        this.tipo = tipo;
        this.entidad = entidad;
        this.referencia = referencia;
        this.usuario = usuario;
        this.rol = rol;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getEntidad() { return entidad; }
    public String getReferencia() { return referencia; }
    public String getUsuario() { return usuario; }
    public String getRol() { return rol; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
