package Leethnuth.syso.Service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import Leethnuth.syso.Order;
import Leethnuth.syso.Repository.Repo;

@Service
public class Service1 {

    private final Repo repo;

    public Service1(Repo repo) {
        this.repo = repo;
    }

    private static final double BIAS        = -2.5;
    private static final double W_ADDRESS   =  0.8;
    private static final double W_SLOT      =  0.6;
    private static final double W_PAST_FAIL =  1.2;
    private static final double W_ZONE_RATE =  1.5;
    private static final double W_COD       =  0.4;
    private static final double W_WEATHER   =  0.5;
    private static final double W_DAY       =  0.3;

    // ── STEP 1: User places order → predict + save ───────────────────
    public PredictionResult predict(Order order) {

        double addressScore = encodeArea(order.getArea());
        double slotScore    = encodeSlot(order.getSlotScore());
        double pastFail     = Math.min(order.getPastFail(), 3) / 3.0;
        double zoneRate     = order.getZoneRate();
        double codScore     = order.isCod() ? 1.0 : 0.0;
        double weatherScore = order.getWeatherScore();
        double dayScore     = encodeDay(order.getDayOfWeek());

        double z = BIAS
                + (W_ADDRESS   * addressScore)
                + (W_SLOT      * slotScore)
                + (W_PAST_FAIL * pastFail)
                + (W_ZONE_RATE * zoneRate)
                + (W_COD       * codScore)
                + (W_WEATHER   * weatherScore)
                + (W_DAY       * dayScore);

        double probability = sigmoid(z);
        int percent = (int) (probability * 100);

        String decision = percent >= 50
                ? "DO NOT DELIVER — High failure risk"
                : "SAFE TO DELIVER";

        // Save order with no feedback yet (deliveryStatus = null = pending)
        order.setPredictionResult(decision);
        order.setPredictionPercent(percent);
        order.setDeliveryStatus(null);  // pending — user hasn't confirmed yet
        repo.save(order);

        return new PredictionResult(percent, decision, buildReasons(order, addressScore));
    }

    // ── STEP 2: User gives feedback after delivery attempt ───────────
    // Call this from your controller:
    // POST /feedback?orderId=5&status=SUCCESS  (or FAILED)
    public String submitFeedback(Long orderId, String status) {
        Order order = repo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        String normalized = status.trim().toUpperCase();

        if (!normalized.equals("SUCCESS") && !normalized.equals("FAILED")) {
            return "Invalid status. Use SUCCESS or FAILED.";
        }

        order.setDeliveryStatus(normalized);
        repo.save(order);

        // Tell caller what the area now looks like
        String area = order.getArea().trim().toUpperCase();
        long total   = repo.countConfirmedByArea(area);
        long failed  = repo.countFailedByArea(area);
        int rate     = total > 0 ? (int)((double) failed / total * 100) : 0;

        return "Feedback saved. Area '" + order.getArea() + "' now has "
                + failed + " failures out of " + total
                + " confirmed orders (" + rate + "% failure rate).";
    }

    // ── Area encoder — 100% driven by past user feedback ────────────
    // No hardcoded values. No developer input.
    // If no feedback exists yet for this area → default safe (0.0)
    private double encodeArea(String area) {
        if (area == null || area.isBlank()) return 0.0;

        String key = area.trim().toUpperCase();

        long total  = repo.countConfirmedByArea(key);
        long failed = repo.countFailedByArea(key);

        if (total == 0) {
            // Area never seen before OR no feedback given yet → treat as safe
            return 0.0;
        }

        // Real failure rate from user feedback
        return (double) failed / total;
    }

    // ── Sigmoid ──────────────────────────────────────────────────────
    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    // ── Slot encoder ─────────────────────────────────────────────────
    private double encodeSlot(double hour) {
        if (hour >= 18) return 1.0;
        if (hour >= 14) return 0.5;
        return 0.0;
    }

    // ── Day encoder ──────────────────────────────────────────────────
    private double encodeDay(int day) {
        return (day == 6 || day == 7) ? 1.0 : 0.3;
    }

    // ── Reason builder ───────────────────────────────────────────────
    private List<String> buildReasons(Order order, double areaScore) {
        List<String> reasons = new ArrayList<>();

        if (areaScore >= 0.5)
            reasons.add("Area '" + order.getArea() + "' has "
                    + (int)(areaScore * 100) + "% failure rate from past deliveries");
        else if (areaScore == 0.0)
            reasons.add("Area '" + order.getArea() + "' is new — defaulting to safe");

        if (order.getSlotScore() >= 18)
            reasons.add("Evening delivery slot — higher risk");

        if (order.getPastFail() >= 2)
            reasons.add("Customer missed " + order.getPastFail() + " previous deliveries");

        if (order.getZoneRate() > 0.25)
            reasons.add("Zone has " + (int)(order.getZoneRate() * 100) + "% historical failure rate");

        if (order.isCod())
            reasons.add("COD order — customer less committed");

        if (order.getWeatherScore() == 1.0)
            reasons.add("Bad weather expected in delivery window");

        return reasons;
    }
}