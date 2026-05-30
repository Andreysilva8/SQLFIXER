package br.com.empresa.intranet.service;

import br.com.empresa.intranet.entity.Usuario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UsuarioFallbackStore {
    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentMap<Long, Usuario> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByEmail = new ConcurrentHashMap<>();

    public Usuario save(Usuario usuario) {
        if (usuario.getId() == null) {
            usuario.setId(ids.getAndIncrement());
        }
        usersById.put(usuario.getId(), usuario);
        if (usuario.getEmail() != null) {
            idsByEmail.put(usuario.getEmail().toLowerCase(), usuario.getId());
        }
        return usuario;
    }

    public List<Usuario> findAll() {
        return new ArrayList<>(usersById.values());
    }

    public Optional<Usuario> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        Long id = idsByEmail.get(email.toLowerCase());
        return id == null ? Optional.empty() : Optional.ofNullable(usersById.get(id));
    }

    public void deleteById(Long id) {
        Usuario removed = usersById.remove(id);
        if (removed != null && removed.getEmail() != null) {
            idsByEmail.remove(removed.getEmail().toLowerCase());
        }
    }
}
