package br.mackenzie.resgatai.comum;

/** Cálculo de distância geográfica (fórmula de Haversine), usado pelo Motor de Roteamento. */
public final class Geo {

    private static final double RAIO_TERRA_KM = 6371.0;

    private Geo() {}

    /** Distância em km entre dois pontos (latitude/longitude em graus). */
    public static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
