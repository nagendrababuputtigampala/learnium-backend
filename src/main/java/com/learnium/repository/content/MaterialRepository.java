package com.learnium.repository.content;

import com.learnium.model.content.Material;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    @Query("select m from Material m where m.centre.centreId = :centreId")
    List<Material> findByCentreId(@Param("centreId") UUID centreId);
}
