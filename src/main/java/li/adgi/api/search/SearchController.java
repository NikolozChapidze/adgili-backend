package li.adgi.api.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import li.adgi.api.common.PageResponse;
import li.adgi.api.listing.ListingDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Listing search and discovery (public)")
public class SearchController {

    private final SearchService service;

    @GetMapping("/listings")
    @Operation(summary = "Search LIVE listings with filters and optional radius")
    public PageResponse<ListingDtos.ListingSummary> search(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minArea,
            @RequestParam(required = false) BigDecimal maxArea,
            @RequestParam(required = false) Boolean access247,
            @RequestParam(required = false) Boolean gated,
            @RequestParam(required = false) Boolean cctv,
            @RequestParam(required = false) Boolean indoor,
            @RequestParam(required = false) BigDecimal centerLat,
            @RequestParam(required = false) BigDecimal centerLon,
            @RequestParam(required = false) Integer radiusMeters,
            @RequestParam(required = false, defaultValue = "RECOMMENDED") SearchDtos.SortBy sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var q = new SearchDtos.SearchQuery(
            district, neighborhood, minPrice, maxPrice, minArea, maxArea,
            access247, gated, cctv, indoor,
            centerLat, centerLon, radiusMeters,
            sort, page, size);
        return service.search(q);
    }
}
