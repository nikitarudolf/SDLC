package app.model;

public class LoanResult {

    private final double monthlyPayment;
    private final double totalPayment;
    private final double overpayment;
    private final double overpayRatio;

    public LoanResult(double monthlyPayment, double totalPayment,
                      double overpayment, double overpayRatio) {
        this.monthlyPayment = monthlyPayment;
        this.totalPayment = totalPayment;
        this.overpayment = overpayment;
        this.overpayRatio = overpayRatio;
    }

    public double getMonthlyPayment() {
        return monthlyPayment;
    }

    public double getTotalPayment() {
        return totalPayment;
    }

    public double getOverpayment() {
        return overpayment;
    }

    public double getOverpayRatio() {
        return overpayRatio;
    }
}
