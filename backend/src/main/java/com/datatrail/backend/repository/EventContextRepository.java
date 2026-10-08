package com.datatrail.backend.repository;

import com.datatrail.backend.entity.EventContext;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventContextRepository extends JpaRepository<EventContext, Long> {
}