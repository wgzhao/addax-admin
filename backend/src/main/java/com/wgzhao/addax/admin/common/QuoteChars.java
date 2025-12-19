package com.wgzhao.addax.admin.common;

// Simple pair to represent left/right quote characters (some DBs use asymmetric quotes like [ ])
public record QuoteChars(String left, String right) {
}
