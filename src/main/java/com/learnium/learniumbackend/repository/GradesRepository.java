package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradesRepository extends JpaRepository<Grade, Integer> {

}
