package com.railpredictor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@code evaluation.calibration.*} (Phase 20). Mirrors {@code PredictionEvaluationConfig}'s
 * role for {@code prediction.evaluation.*}. */
@Configuration
@EnableConfigurationProperties(EvaluationCalibrationProperties.class)
public class EvaluationCalibrationConfig {
}
