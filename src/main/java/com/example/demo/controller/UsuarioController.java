package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Usuario;
import com.example.demo.repository.UsuarioRepository;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin
public class UsuarioController {

    private final UsuarioRepository repository;

    public UsuarioController(UsuarioRepository repository) {
        this.repository = repository;
    }

    // SALVAR NO BANCO
    @PostMapping
    public Usuario salvar(@RequestBody Usuario usuarios) {
        if (usuarios == null) {
            throw new IllegalArgumentException("Usuario cannot be null");
        }
        return repository.save(usuarios);
    }

    // LISTAR DO BANCO
    @GetMapping
    public List<Usuario> listar() {
        return repository.findAll();
    }

    // DELETAR
    @DeleteMapping("/{id}")
    public void deletar(@PathVariable Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        repository.deleteById(id);
    }
}