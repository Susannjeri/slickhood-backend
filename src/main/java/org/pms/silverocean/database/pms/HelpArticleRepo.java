package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.HelpArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HelpArticleRepo extends JpaRepository<HelpArticle, Long> {
    List<HelpArticle> findByPublishedTrueAndActiveTrueOrderByCategoryAscTitleAsc();
    List<HelpArticle> findByActiveTrueOrderByCategoryAscTitleAsc();
    Optional<HelpArticle> findByIdAndActiveTrue(long id);
    boolean existsBySlugAndIdNot(String slug, long id);
}
