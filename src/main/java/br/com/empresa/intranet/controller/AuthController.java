package br.com.empresa.intranet.controller;

import br.com.empresa.intranet.entity.Usuario;
import br.com.empresa.intranet.repository.UsuarioRepository;
import br.com.empresa.intranet.service.UsuarioFallbackStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioFallbackStore fallbackStore;

    public AuthController(UsuarioRepository repository, PasswordEncoder passwordEncoder, UsuarioFallbackStore fallbackStore) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.fallbackStore = fallbackStore;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Usuario usuario) {
        try {
            if (findByEmail(usuario.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body("Email ja cadastrado!");
            }

            usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
            save(usuario);
            return ResponseEntity.ok("Usuario cadastrado com sucesso!");
        } catch (Exception ex) {
            return ResponseEntity.status(503).body("Nao foi possivel cadastrar: " + ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Usuario usuario) {
        try {
            Optional<Usuario> user = findByEmail(usuario.getEmail());
            if (user.isEmpty()) {
                return ResponseEntity.status(404).body("Usuario nao encontrado!");
            }

            String senhaSalva = user.get().getSenha();
            boolean senhaValida = senhaSalva != null
                    && (passwordEncoder.matches(usuario.getSenha(), senhaSalva) || senhaSalva.equals(usuario.getSenha()));

            if (!senhaValida) {
                return ResponseEntity.status(401).body("Senha incorreta!");
            }

            return ResponseEntity.ok("Login realizado com sucesso!");
        } catch (Exception ex) {
            return ResponseEntity.status(503).body("Nao foi possivel fazer login: " + ex.getMessage());
        }
    }

    private Optional<Usuario> findByEmail(String email) {
        try {
            Optional<Usuario> user = repository.findByEmail(email);
            return user.isPresent() ? user : fallbackStore.findByEmail(email);
        } catch (Exception ex) {
            return fallbackStore.findByEmail(email);
        }
    }

    private void save(Usuario usuario) {
        try {
            repository.save(usuario);
        } catch (Exception ex) {
            fallbackStore.save(usuario);
        }
    }
}
