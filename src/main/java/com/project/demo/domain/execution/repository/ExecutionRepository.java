package com.project.demo.domain.execution.repository;

import com.project.demo.domain.execution.entity.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
}
