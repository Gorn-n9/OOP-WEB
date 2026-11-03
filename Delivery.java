abstract class Delivery {
    private final int weightKg;

    protected Delivery(int weightKg) {
        if (weightKg < 1 || weightKg > 30) {
            throw new IllegalArgumentException("Weight must be between 1 and 30 kg");
        }
        this.weightKg = weightKg;
    }

    protected final int weightKg() {
        return weightKg;
    }

    public abstract String name();

    public abstract String rate();

    public abstract int calculateFee();
}

final class StandardDelivery extends Delivery {
    StandardDelivery(int weightKg) {
        super(weightKg);
    }

    @Override
    public String name() {
        return "ส่งธรรมดา";
    }

    @Override
    public String rate() {
        return "ค่าพื้นฐาน 25 บาท + 10 บาทต่อกิโลกรัม";
    }

    @Override
    public int calculateFee() {
        return 25 + 10 * weightKg();
    }
}

final class ExpressDelivery extends Delivery {
    ExpressDelivery(int weightKg) {
        super(weightKg);
    }

    @Override
    public String name() {
        return "ส่งด่วน";
    }

    @Override
    public String rate() {
        return "ค่าพื้นฐาน 45 บาท + 18 บาทต่อกิโลกรัม";
    }

    @Override
    public int calculateFee() {
        return 45 + 18 * weightKg();
    }
}
