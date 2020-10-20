package org.geogebra.common.kernel.interval;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;
import static org.geogebra.common.kernel.interval.IntervalConstants.WHOLE;
import static org.geogebra.common.kernel.interval.IntervalConstants.ZERO;
import static org.geogebra.common.kernel.interval.IntervalTest.interval;
import static org.geogebra.common.kernel.interval.IntervalTest.shouldEqual;

import org.junit.Test;

public class IntervalDivisionTest {

	@Test
	public void testDivisionPositiveWithZero() {
		shouldEqual(WHOLE, interval(1, 2).divide(interval(-1, 1)));
		shouldEqual(WHOLE, interval(0, 2).divide(interval(0, 1)));
		shouldEqual(WHOLE, interval(0, 2).divide(interval(-1, 0)));
		shouldEqual(interval(1, Double.POSITIVE_INFINITY),
				interval(1, 2).divide(interval(0, 1)));
		shouldEqual(interval(NEGATIVE_INFINITY, -1)	,
				interval(1, 2).divide(interval(0, 1)));
	}

	@Test
	public void testDivisionNegativeWithZero() {
		shouldEqual(WHOLE, interval(-2, -1).divide(interval(-1, 1)));
		shouldEqual(WHOLE, interval(-2, 0).divide(interval(0, 1)));
		shouldEqual(WHOLE, interval(-2, 0).divide(interval(-1, 0)));
		shouldEqual(interval(-NEGATIVE_INFINITY, -1),
				interval(-2, -1).divide(interval(0, 1)));
		shouldEqual(interval(-NEGATIVE_INFINITY, -1),
				interval(-2, -1).divide(interval(0, 1)));
	}

	@Test
	public void testDivisionMixedWithZero() {
		shouldEqual(WHOLE, interval(-2, 3).divide(interval(-1, 1)));
	}

	@Test
	public void testDivisionZeroWithZero() {
		shouldEqual(interval(0, 0), ZERO.divide(interval(-1, 1)));
		shouldEqual(interval(0, 0), ZERO.divide(interval(-1, 0)));
		shouldEqual(interval(0, 0), ZERO.divide(interval(0, 1)));
	}

	@Test
	public void testDivisionWithoutZero() {
		shouldEqual(interval(1 / 4.0, 2 / 3.0),
				interval(1, 2).divide(interval(3, 4)));

		shouldEqual(interval(0, 2 / 3.0),
				interval(1, 2).divide(interval(3, POSITIVE_INFINITY)));

		shouldEqual(interval(0, POSITIVE_INFINITY),
				interval(1, POSITIVE_INFINITY).divide(interval(3, POSITIVE_INFINITY)));

		shouldEqual(interval(1 / 4.0, 2 / 3.0),
				interval(-2, -1).divide(interval(-4, -3)));
	}
}