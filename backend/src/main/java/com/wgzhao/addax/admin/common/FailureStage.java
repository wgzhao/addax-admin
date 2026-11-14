package com.wgzhao.addax.admin.common;

/**
 * The step of the collection pipeline that failed. A stage is not a table status: several
 * different stages can fail while the table is in the same state, and the operator needs to know
 * which one to investigate.
 */
public final class FailureStage
{
    public static final String PREPARE = "PREPARE";
    public static final String SOURCE_SCHEMA = "SOURCE_SCHEMA";
    public static final String TARGET_SCHEMA = "TARGET_SCHEMA";
    public static final String PARTITION = "PARTITION";
    public static final String JOB_TEMPLATE = "JOB_TEMPLATE";
    public static final String COLLECT = "COLLECT";
    public static final String QUEUE = "QUEUE";
    public static final String RECOVERY = "RECOVERY";
    public static final String RECONCILE = "RECONCILE";

    private FailureStage() {}

    public static String fromJourKind(String kind)
    {
        if (kind == null) {
            return PREPARE;
        }
        return switch (kind) {
            case JourKind.COLLECT, JourKind.FILLBACK -> COLLECT;
            case JourKind.ADDAX_JOB -> JOB_TEMPLATE;
            case JourKind.SCHEMA, JourKind.UPDATE_COLUMN, JourKind.CREATE_COLUMN -> SOURCE_SCHEMA;
            case JourKind.UPDATE_TABLE -> TARGET_SCHEMA;
            case JourKind.PARTITION -> PARTITION;
            default -> PREPARE;
        };
    }
}
