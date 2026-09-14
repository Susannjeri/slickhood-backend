package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.KycMatrixRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycMatrixReleaseRepo extends JpaRepository<KycMatrixRelease, Long> {
    Optional<KycMatrixRelease> findFirstByStatusAndActiveTrueOrderByVersionNoDesc(String status);
    List<KycMatrixRelease> findAllByOrderByVersionNoDesc();
}
