package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.FinancialJournal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialJournalRepo extends JpaRepository<FinancialJournal,Long>{boolean existsByEventKey(String eventKey);}
