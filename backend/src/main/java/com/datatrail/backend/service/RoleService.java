package com.datatrail.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.datatrail.backend.entity.Role;
import com.datatrail.backend.repository.RoleRepository;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role createRole(Role role) {

        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new RuntimeException("Role already exists");
        }

        return roleRepository.save(role);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));
    }

    public void deleteRole(Long id) {
        roleRepository.deleteById(id);
    }
}