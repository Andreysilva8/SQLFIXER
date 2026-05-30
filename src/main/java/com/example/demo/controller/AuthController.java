package com.example.demo.controller;

import com.example.demo.model.Usuario;
import com.example.demo.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioRepository repository;

    public AuthController(UsuarioRepository repository) {
        this.repository = repository;
    }

    // CADASTRO
    @PostMapping("/register")
    public String register(@RequestBody Usuario usuario) {

        Optional<Usuario> existente = repository.findByEmail(usuario.getEmail());

        if (existente.isPresent()) {
            return "Email já cadastrado!";
        }

        repository.save(usuario);
        return "Usuário cadastrado com sucesso!";
    }

    // LOGIN
    @PostMapping("/login")
    public String login(@RequestBody Usuario usuario) {

        Optional<Usuario> user = repository.findByEmail(usuario.getEmail());

        if (user.isEmpty()) {
            return "Usuário não encontrado!";
        }

        if (!user.get().getSenha().equals(usuario.getSenha())) {
            return "Senha incorreta!";
        }

        return "Login realizado com sucesso!";
    }
}