package com.shaboozey.kiskae.backemup;

import java.util.Calendar;
import java.util.Date;

/**
 *
 * @author Kiskae
 */
public class BEUUtil {

    public static Date timeLastMidnight() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date findNextInstance(Date start, int hours) {
        Calendar c = Calendar.getInstance();
        c.setTime(start);
        Calendar now = Calendar.getInstance();
        while (c.before(now)) {
            c.add(Calendar.HOUR_OF_DAY, hours);
        }
        return c.getTime();
    }
}
