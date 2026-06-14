package li.adgi.api.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.security.SecureRandom;

public final class GeoUtil {

    private static final int SRID_WGS84 = 4326;
    private static final GeometryFactory FACTORY =
        new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private static final SecureRandom RNG = new SecureRandom();

    private GeoUtil() {}

    public static Point point(BigDecimal lat, BigDecimal lon) {
        if (lat == null || lon == null) return null;
        return point(lat.doubleValue(), lon.doubleValue());
    }

    public static Point point(double lat, double lon) {
        Point p = FACTORY.createPoint(new Coordinate(lon, lat)); // (x=lon, y=lat)
        p.setSRID(SRID_WGS84);
        return p;
    }

    /**
     * Jitter a point by up to ~300m to produce a "approximate" location shown before booking.
     */
    public static double[] jitter(double lat, double lon, double radiusMeters) {
        double dx = (RNG.nextDouble() - 0.5) * 2 * radiusMeters;
        double dy = (RNG.nextDouble() - 0.5) * 2 * radiusMeters;
        // 1 deg lat ≈ 111_000 m; 1 deg lon ≈ 111_000 * cos(lat) m
        double dLat = dy / 111_000.0;
        double dLon = dx / (111_000.0 * Math.cos(Math.toRadians(lat)));
        return new double[] { lat + dLat, lon + dLon };
    }
}
