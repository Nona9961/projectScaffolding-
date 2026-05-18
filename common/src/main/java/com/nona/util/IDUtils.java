package com.nona.util;


import com.nona.persistence.Sequence;
import com.nona.annotation.ScaffoldGenerated;

@ScaffoldGenerated
public class IDUtils {
    private static final Sequence sequence = new Sequence(1, 1);

    private IDUtils() throws IllegalAccessException {
        throw new IllegalAccessException();
    }
    public static Long generateID() {
        return sequence.nextId();
    }

}
