
package Leethnuth.syso.Service;

import java.util.List;

public class PredictionResult {

    private final int percent;
    private final String decision;
    private final List<String> reasons;

    public PredictionResult(int percent, String decision, List<String> reasons) {
        this.percent  = percent;
        this.decision = decision;
        this.reasons  = reasons;
    }

    public int getPercent()          { return percent; }
    public String getDecision()      { return decision; }
    public List<String> getReasons() { return reasons; }

    @Override
    public String toString() {
        return "PredictionResult{" +
                "percent=" + percent +
                ", decision='" + decision + '\'' +
                ", reasons=" + reasons +
                '}';
    }
}

