package com.carmanager.build;

import java.util.ArrayList;
import java.util.List;

/** Validation de build, également compilée dans les tests JVM, jamais dans l'APK. */
public final class AdMobConfiguration {
    public static final String DEBUG_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    public static final String DEBUG_BANNER_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final String SAMPLE_PUBLISHER = "ca-app-pub-3940256099942544";

    private AdMobConfiguration() {}

    public static List<String> releaseErrors(String appId, String bannerId) {
        List<String> errors = new ArrayList<>();
        validate("ADMOB_APP_ID", appId, "ca-app-pub-[0-9]{16}~[0-9]{10}", errors);
        validate("ADMOB_BANNER_AD_UNIT_ID", bannerId, "ca-app-pub-[0-9]{16}/[0-9]{10}", errors);
        return List.copyOf(errors);
    }

    private static void validate(String key, String value, String pattern, List<String> errors) {
        if (value == null || value.isBlank()) errors.add(key + " : valeur manquante");
        else if (!value.matches(pattern)) errors.add(key + " : format invalide");
        else if (value.startsWith(SAMPLE_PUBLISHER)) errors.add(key + " : publisher Google de demonstration interdit en release");
    }

    public static boolean isDebugTestConfiguration(String appId, String bannerId) {
        return DEBUG_APP_ID.equals(appId) && DEBUG_BANNER_ID.equals(bannerId);
    }
}
