package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.ResolucionJudicial;
import org.springframework.data.jpa.repository.JpaRepository;

/** HU-07.3: resoluciones judiciales de adoptabilidad. */
public interface ResolucionJudicialRepository extends JpaRepository<ResolucionJudicial, Long> {
}
