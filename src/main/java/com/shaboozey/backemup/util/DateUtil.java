package com.shaboozey.backemup.util;

import java.util.Calendar;
import java.util.Date;

/**
 * @author Kiskae
 */
public class DateUtil {

    public static Date timeLastMidnight() {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * Finds the next occurrence of the specified interval
     * after the current point in time, starting at initial.
     *
     * @param initial  The time to begin searching
     * @param interval The interval in hours to search through
     * @return The next date that is {@code initial + k * interval} that is after the current date.
     */
    public static Date findNextTime(final Date initial, final int interval) {
        final Calendar it = Calendar.getInstance();
        it.setTime(initial);
        final Calendar now = Calendar.getInstance();
        while (it.before(now)) {
            it.add(Calendar.HOUR_OF_DAY, interval);
        }
        return it.getTime();
    }
}
