package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Utility;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Set;

public interface UtilitiesRepo extends CrudRepository<Utility, Long> {
    @Query("SELECT u.name FROM Utility u")
    Set<String> queryAllUtilitiesNames();

    List<Utility> findAllByActiveTrue();
}
