package com.onlinecurlearnmanos.career.service;

public final class CareerConsentPolicy {

    public static final String CONSENT_VERSION = "v1";

    public static final String PRIVACY_NOTICE = """
            生涯分析只使用你本人选择授权的数据：学习记录、知识点掌握、学习目标与任务、云班级成绩，以及你在本页填写的专业、年级、兴趣和期望方向。

            这些数据只在生成分析时发送给你当前选择的模型供应商，用于本次建议生成，不会展示给其他学生或教师。后端会保存每次分析使用的数据范围摘要，方便你核对自己授权了什么。

            分析结论只是辅助参考，不构成确定性判断，最终决定仍然由你自己做出。你可以随时取消授权；取消后不会生成新的分析，已有分析记录可以自行删除。""";

    private CareerConsentPolicy() {
    }
}
