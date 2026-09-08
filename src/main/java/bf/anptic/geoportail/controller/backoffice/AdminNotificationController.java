package bf.anptic.geoportail.controller.backoffice;

import bf.anptic.geoportail.dto.NotificationTokenResponse;
import bf.anptic.geoportail.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/backoffice/api/v1/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationTokenResponse> listTokens(@RequestParam(required = false) String siteId) {
        return notificationService.listTokens(siteId);
    }

    @DeleteMapping("/{id}")
    public void deleteToken(@PathVariable Long id, Authentication authentication) {
        notificationService.deleteToken(id, authentication.getName());
    }
}