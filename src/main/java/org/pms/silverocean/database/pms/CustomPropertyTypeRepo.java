package org.pms.silverocean.database.pms;
import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.CustomPropertyType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CustomPropertyTypeRepo extends JpaRepository<CustomPropertyType,Long>{
    Optional<CustomPropertyType> findByCode(String code);
    boolean existsByCode(String code);
    List<CustomPropertyType> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from CustomPropertyType t where t.code=:code")
    Optional<CustomPropertyType> findForUpdate(@Param("code") String code);
}
