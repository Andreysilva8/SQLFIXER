package br.com.empresa.intranet.controller;

import br.com.empresa.intranet.entity.Usuario;
import br.com.empresa.intranet.repository.UsuarioRepository;
import br.com.empresa.intranet.service.UsuarioFallbackStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin
public class UsuarioController {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioFallbackStore fallbackStore;

    public UsuarioController(UsuarioRepository repository, PasswordEncoder passwordEncoder, UsuarioFallbackStore fallbackStore) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.fallbackStore = fallbackStore;
    }

    @PostMapping
    public ResponseEntity<?> salvar(@RequestBody Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Usuario nao pode ser nulo."));
        }
        try {
            if (usuario.getSenha() != null && !usuario.getSenha().startsWith("$2")) {
                usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
            }
            return ResponseEntity.ok(repository.save(usuario));
        } catch (Exception ex) {
            return ResponseEntity.ok(fallbackStore.save(usuario));
        }
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        try {
            return ResponseEntity.ok(repository.findAll());
        } catch (Exception ex) {
            return ResponseEntity.ok(fallbackStore.findAll());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Id nao pode ser nulo."));
        }
        try {
            repository.deleteById(id);
            fallbackStore.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            fallbackStore.deleteById(id);
            return ResponseEntity.noContent().build();
        }
    }
}
