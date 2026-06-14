package li.adgi.api.search;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public class SearchDtos {

    public record SearchQuery(
        String district,
        String neighborhood,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minArea,
        BigDecimal maxArea,
        Boolean access247,
        Boolean gated,
        Boolean cctv,
        Boolean indoor,

        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal centerLat,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal centerLon,
        Integer radiusMeters,

        SortBy sort,
        int page,
        int size
    ) {
        public SearchQuery {
            if (size <= 0 || size > 100) size = 20;
            if (page < 0) page = 0;
            if (sort == null) sort = SortBy.RECOMMENDED;
        }
    }

    public enum SortBy {
        RECOMMENDED, PRICE_ASC, PRICE_DESC, AREA_DESC, NEWEST, CLOSEST
    }
}
