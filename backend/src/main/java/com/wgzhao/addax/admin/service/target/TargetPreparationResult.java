package com.wgzhao.addax.admin.service.target;

/**
 * Outcome of a target's pre-run preparation. Returning the stage and reason instead of a bare
 * boolean keeps the diagnosis with the adapter that knows it: the caller records one failure fact
 * at the most specific stage, instead of a specific partition fact followed by a generic duplicate.
 */
public record TargetPreparationResult(boolean success, String stage, String summary, String detail,
    String errorClass)
{
    public static TargetPreparationResult ok()
    {
        return new TargetPreparationResult(true, null, null, null, null);
    }

    public static TargetPreparationResult failed(String stage, String summary, String detail,
        String errorClass)
    {
        return new TargetPreparationResult(false, stage, summary, detail, errorClass);
    }
}
