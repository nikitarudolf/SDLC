package app.model;

public class RawInput {

    private final String amount;
    private final String rate;
    private final String years;

    public RawInput(String amount, String rate, String years) {
        this.amount = amount == null ? "" : amount;
        this.rate = rate == null ? "" : rate;
        this.years = years == null ? "" : years;
    }

    public static RawInput empty() {
        return new RawInput("", "", "");
    }

    public String getAmount() {
        return amount;
    }

    public String getRate() {
        return rate;
    }

    public String getYears() {
        return years;
    }
}
