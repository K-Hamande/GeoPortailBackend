package bf.anptic.geoportail.repository;

import bf.anptic.geoportail.model.NotificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    List<NotificationToken> findBySite_SiteIdAndActifTrue(String siteId);

    Optional<NotificationToken> findBySite_SiteIdAndEndpoint(String siteId, String endpoint);
}
