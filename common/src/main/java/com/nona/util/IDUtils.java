package com.nona.util;


import com.nona.persisitence.Sequence;

public class IDUtils {
    private static final Sequence sequence = new Sequence(1, 1);

    private IDUtils() throws IllegalAccessException {
        throw new IllegalAccessException();
    }
    public static Long generateID() {
        return sequence.nextId();
    }

}
