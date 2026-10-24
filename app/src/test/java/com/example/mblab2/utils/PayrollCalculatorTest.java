package com.example.mblab2.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PayrollCalculatorTest {
    @Test
    public void overtimeIsComputedPerDayBeforeMonthlySum() {
        double overtime = PayrollCalculator.calculateDailyOvertime(8)
                + PayrollCalculator.calculateDailyOvertime(10);
        assertEquals(2, overtime, 0);
        assertEquals(950_000, PayrollCalculator.calculateMonthlySalary(18, overtime, 50_000), 0);
    }

    @Test
    public void zeroHoursAndShortShiftsDoNotProduceOvertime() {
        assertEquals(0, PayrollCalculator.calculateDailyOvertime(0), 0);
        assertEquals(0, PayrollCalculator.calculateDailyOvertime(7.5), 0);
        assertEquals(0, PayrollCalculator.calculateMonthlySalary(0, 0, 50_000), 0);
    }

    @Test
    public void finalSalaryRoundsHalfUpWithoutRoundingHours() {
        assertEquals(2, PayrollCalculator.calculateMonthlySalary(0.00003, 0, 50_000), 0);
        assertEquals(1, PayrollCalculator.calculateMonthlySalary(0.00002, 0, 50_000), 0);
        assertEquals(0.25, PayrollCalculator.calculateDailyOvertime(8.25), 0);
    }

    @Test
    public void rejectsNegativeNonFiniteAndInconsistentHours() {
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PayrollCalculator.calculateDailyOvertime(invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> PayrollCalculator.calculateMonthlySalary(invalid, 0, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> PayrollCalculator.calculateMonthlySalary(10, invalid, 1));
        }
        assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculator.calculateMonthlySalary(8, 9, 50_000));
    }

    @Test
    public void rejectsInvalidRatesAndOverflow() {
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PayrollCalculator.calculateMonthlySalary(8, 0, rate));
        }
        assertThrows(IllegalArgumentException.class,
                () -> PayrollCalculator.calculateMonthlySalary(Double.MAX_VALUE, 0, Double.MAX_VALUE));
    }
}
