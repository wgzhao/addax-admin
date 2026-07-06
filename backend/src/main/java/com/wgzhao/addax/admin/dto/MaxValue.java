package com.wgzhao.addax.admin.dto;

/**
 * Represents the maximum value of a column, supporting both numeric and string types
 * 
 * This sealed interface ensures type safety when handling max values of different column types.
 * Numeric columns return NumericMax, and string/text columns return StringMax.
 */
public sealed interface MaxValue permits MaxValue.NumericMax, MaxValue.StringMax
{
    /**
     * Get the raw value
     */
    Object getValue();

    /**
     * Record for numeric (integer, decimal, float, etc.) column max values
     */
    record NumericMax(Long value) implements MaxValue
    {
        @Override
        public Object getValue()
        {
            return value;
        }
    }

    /**
     * Record for string/text column max values (lexicographic order)
     */
    record StringMax(String value) implements MaxValue
    {
        @Override
        public Object getValue()
        {
            return value;
        }
    }
}
