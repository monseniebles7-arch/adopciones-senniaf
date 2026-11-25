package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    List<Usuario> findByRolAndHabilitadoTrueOrderByIdAsc(Rol rol);
}
