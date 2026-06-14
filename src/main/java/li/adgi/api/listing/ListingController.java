package li.adgi.api.listing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import li.adgi.api.common.PageResponse;
import li.adgi.api.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Listings", description = "Garage/storage listings CRUD and moderation")
public class ListingController {

    private final ListingService service;
    private final ListingRepository repo;
    private final CurrentUser currentUser;

    // ---------- Public read ----------

    @GetMapping("/api/v1/listings/{listingId}")
    @Operation(summary = "Get public listing detail (exact address hidden)")
    public ListingDtos.ListingDetail getPublic(@PathVariable UUID listingId) {
        return service.getDetailPublic(listingId);
    }

    // ---------- Host-scoped ----------

    @PostMapping("/api/v1/listings")
    @Operation(summary = "Create listing draft")
    public ListingDtos.ListingDetail create(@Valid @RequestBody ListingDtos.CreateListingRequest req) {
        var l = service.createDraft(currentUser.get().userId(), req);
        return service.toDetail(l, true);
    }

    @GetMapping("/api/v1/host/listings")
    @Operation(summary = "List listings owned by current host")
    public PageResponse<ListingDtos.ListingDetail> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listForHost(currentUser.get().userId(),
            PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result).map(l -> service.toDetail(l, true));
    }

    @PatchMapping("/api/v1/host/listings/{listingId}")
    @Operation(summary = "Update own listing (LIVE listings go back to PENDING_REVIEW)")
    public ListingDtos.ListingDetail update(
            @PathVariable UUID listingId,
            @Valid @RequestBody ListingDtos.CreateListingRequest req) {
        var l = service.update(currentUser.get().userId(), listingId, req);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/host/listings/{listingId}/submit-for-review")
    @Operation(summary = "Submit a DRAFT or REJECTED listing for moderation")
    public ListingDtos.ListingDetail submit(@PathVariable UUID listingId) {
        var l = service.submitForReview(currentUser.get().userId(), listingId);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/host/listings/{listingId}/pause")
    @Operation(summary = "Pause a LIVE listing")
    public ListingDtos.ListingDetail pause(@PathVariable UUID listingId) {
        var l = service.pause(currentUser.get().userId(), listingId);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/host/listings/{listingId}/unpause")
    @Operation(summary = "Unpause a paused listing back to LIVE")
    public ListingDtos.ListingDetail unpause(@PathVariable UUID listingId) {
        var l = service.unpause(currentUser.get().userId(), listingId);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/host/listings/{listingId}/photos")
    @Operation(summary = "Add a photo (provide a pre-uploaded file URL)")
    public ListingDtos.PhotoView addPhoto(
            @PathVariable UUID listingId,
            @Valid @RequestBody ListingDtos.AddPhotoRequest req) {
        return ListingDtos.PhotoView.from(
            service.addPhoto(currentUser.get().userId(), listingId, req));
    }

    @DeleteMapping("/api/v1/host/listings/{listingId}/photos/{photoId}")
    @Operation(summary = "Delete a photo")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable UUID listingId, @PathVariable UUID photoId) {
        service.deletePhoto(currentUser.get().userId(), listingId, photoId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Admin moderation ----------

    @GetMapping("/api/v1/admin/listings")
    @Operation(summary = "[Admin] List listings by moderation status")
    public PageResponse<ListingDtos.ListingDetail> adminList(
            @RequestParam(defaultValue = "PENDING") ListingEnums.ModerationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
            repo.findByModerationStatus(status, PageRequest.of(page, Math.min(size, 100))))
            .map(l -> service.toDetail(l, true));
    }

    @GetMapping("/api/v1/admin/listings/{listingId}")
    @Operation(summary = "[Admin] Get listing detail with full exact location")
    public ListingDtos.ListingDetail adminGet(@PathVariable UUID listingId) {
        return service.getDetail(listingId, currentUser.get().userId(), true);
    }

    @PostMapping("/api/v1/admin/listings/{listingId}/approve")
    @Operation(summary = "[Admin] Approve a pending listing")
    public ListingDtos.ListingDetail adminApprove(@PathVariable UUID listingId) {
        var l = service.adminApprove(currentUser.get().userId(), listingId);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/admin/listings/{listingId}/reject")
    @Operation(summary = "[Admin] Reject a pending listing")
    public ListingDtos.ListingDetail adminReject(
            @PathVariable UUID listingId,
            @RequestBody(required = false) ListingDtos.AdminModerationRequest body) {
        String reason = body == null ? null : body.reason();
        var l = service.adminReject(currentUser.get().userId(), listingId, reason);
        return service.toDetail(l, true);
    }

    @PostMapping("/api/v1/admin/listings/{listingId}/block")
    @Operation(summary = "[Admin] Block a listing (policy violation)")
    public ListingDtos.ListingDetail adminBlock(
            @PathVariable UUID listingId,
            @RequestBody(required = false) ListingDtos.AdminModerationRequest body) {
        String reason = body == null ? null : body.reason();
        var l = service.adminBlock(currentUser.get().userId(), listingId, reason);
        return service.toDetail(l, true);
    }
}
