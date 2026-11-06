package com.miao.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void parsePortUsesTheHostsValueWhenValid() {
        assertEquals(10000, App.parsePort("10000", 8080));
        assertEquals(10000, App.parsePort(" 10000 ", 8080));
    }

    @Test
    void parsePortFallsBackWhenMissingOrInvalid() {
        assertEquals(8080, App.parsePort(null, 8080));
        assertEquals(8080, App.parsePort(" ", 8080));
        assertEquals(8080, App.parsePort("abc", 8080));
        assertEquals(8080, App.parsePort("0", 8080));
        assertEquals(8080, App.parsePort("70000", 8080));
    }
}
