package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.CambioEtapaJuridica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** HU-07.4: cambios de etapa juridica de un expediente. */
public interface CambioEtapaJuridicaRepository extends JpaRepository<CambioEtapaJuridica, Long> {

    /** Del cambio mas antiguo al mas reciente: el orden de una linea de tiempo. */
    List<CambioEtapaJuridica> findByExpedienteIdOrderByFechaHoraAscIdAsc(Long expedienteId);
}
