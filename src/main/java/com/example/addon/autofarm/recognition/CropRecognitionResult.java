package com.example.addon.autofarm.recognition;

import com.example.addon.autofarm.model.CropProfile;

/**
 * 作物识别结果 - 已知作物或未知。
 *
 * @param known 是否识别为已知作物
 * @param profile 作物档案（已知时非空）
 * @param reason 未知原因（已知时为空）
 */
public record CropRecognitionResult(boolean known, CropProfile profile, String reason) {

    /**
     * 创建已知作物结果。
     */
    public static CropRecognitionResult known(CropProfile profile) {
        return new CropRecognitionResult(true, profile, "");
    }

    /**
     * 创建未知作物结果。
     */
    public static CropRecognitionResult unknown(String reason) {
        return new CropRecognitionResult(false, null, reason);
    }
}
