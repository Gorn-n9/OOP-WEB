interface ParkingRate {
    int calculateFee(int hours);
}

final class HourlyParking implements ParkingRate {
    @Override
    public int calculateFee(int hours) {
        return 25 * hours;
    }
}

final class CappedParking implements ParkingRate {
    @Override
    public int calculateFee(int hours) {
        return Math.min(40 * hours, 120);
    }
}

interface Ticket {
    int calculateTotal(int people);
}

final class AdultTicket implements Ticket {
    @Override
    public int calculateTotal(int people) {
        return 150 * people;
    }
}

final class StudentTicket implements Ticket {
    @Override
    public int calculateTotal(int people) {
        return 90 * people;
    }
}

interface CharacterAttack {
    int calculateDamage(int power);
}

final class WarriorAttack implements CharacterAttack {
    @Override
    public int calculateDamage(int power) {
        return power + 10;
    }
}

final class MageAttack implements CharacterAttack {
    @Override
    public int calculateDamage(int power) {
        return power * 2;
    }
}
