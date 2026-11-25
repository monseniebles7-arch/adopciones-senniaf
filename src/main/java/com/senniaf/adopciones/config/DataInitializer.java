package com.senniaf.adopciones.config;

import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.UsuarioRepository;
import com.senniaf.adopciones.service.FamiliaService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea usuarios de ejemplo (uno por rol) al iniciar la aplicacion, unicamente
 * si no existen todavia. Util para pruebas manuales en Postman/Swagger, para
 * el frontend PHP y para las pruebas de integracion.
 *
 * Los usuarios que se creen desde la pantalla "Crear usuario" se suman a
 * estos. Como la base H2 es en memoria, se pierden al reiniciar: eso se
 * resuelve al migrar a PostgreSQL.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final FamiliaService familiaService;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                           FamiliaService familiaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.familiaService = familiaService;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("admin", "Admin123!", Rol.ADMINISTRADOR, "Administrador General");
        crearSiNoExiste("tecnico1", "Tecnico123!", Rol.TECNICO, "Tecnico de Ejemplo");
        crearSiNoExiste("juez1", "Juez123!", Rol.JUEZ, "Juez de Ejemplo");
        familiaService.asignarPendientesSinResponsable();
    }

    private void crearSiNoExiste(String username, String passwordSinHashear, Rol rol, String nombreCompleto) {
        usuarioRepository.findByUsername(username).orElseGet(() -> {
            Usuario usuario = new Usuario(username, passwordEncoder.encode(passwordSinHashear), rol, nombreCompleto);
            return usuarioRepository.save(usuario);
        });
    }
}
