package app.model;

public class LoanInput {

    private static final double MAX_AMOUNT = 100_000_000.0;
    private static final double MAX_RATE = 200.0;
    private static final int MAX_YEARS = 50;

    private final double amount;
    private final double annualRate;
    private final int years;

    private LoanInput(double amount, double annualRate, int years) {
        this.amount = amount;
        this.annualRate = annualRate;
        this.years = years;
    }

    public static LoanInput parse(RawInput raw) throws ValidationException {
        double amount = parseDouble(raw.getAmount(), "Сумма кредита");
        if (amount <= 0) {
            throw new ValidationException("Сумма кредита должна быть больше нуля.");
        }
        if (amount > MAX_AMOUNT) {
            throw new ValidationException("Сумма кредита не должна превышать "
                    + (long) MAX_AMOUNT + " BYN.");
        }

        double rate = parseDouble(raw.getRate(), "Процентная ставка");
        if (rate < 0) {
            throw new ValidationException("Процентная ставка не может быть отрицательной.");
        }
        if (rate > MAX_RATE) {
            throw new ValidationException("Процентная ставка не должна превышать "
                    + (long) MAX_RATE + " % годовых.");
        }

        int years = parseInt(raw.getYears(), "Срок кредита");
        if (years < 1) {
            throw new ValidationException("Срок кредита должен быть не менее 1 года.");
        }
        if (years > MAX_YEARS) {
            throw new ValidationException("Срок кредита не должен превышать "
                    + MAX_YEARS + " лет.");
        }

        return new LoanInput(amount, rate, years);
    }

    private static double parseDouble(String text, String fieldName) throws ValidationException {
        String value = normalize(text, fieldName);
        double result;
        try {
            result = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Поле \"" + fieldName
                    + "\" должно быть числом. Введено: \"" + text.trim() + "\".");
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new ValidationException("Поле \"" + fieldName + "\" содержит недопустимое число.");
        }
        return result;
    }

    private static int parseInt(String text, String fieldName) throws ValidationException {
        String value = normalize(text, fieldName);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Поле \"" + fieldName
                    + "\" должно быть целым числом лет. Введено: \"" + text.trim() + "\".");
        }
    }

    private static String normalize(String text, String fieldName) throws ValidationException {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new ValidationException("Поле \"" + fieldName + "\" не заполнено.");
        }
        return value.replace(',', '.').replace(" ", "");
    }

    public double getAmount() {
        return amount;
    }

    public double getAnnualRate() {
        return annualRate;
    }

    public int getYears() {
        return years;
    }

    public int getMonths() {
        return years * 12;
    }
}
