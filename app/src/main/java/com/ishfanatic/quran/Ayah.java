package com.ishfanatic.quran;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One ayah: the Arabic, the translation (may be empty) and where it is in the Quran. */
final class Ayah {
    final String arabic;
    final String translation;
    final int surah;
    final int number;
    final String surahEnglish;
    final String surahArabic;

    Ayah(String arabic, String translation, int surah, int number, String surahEnglish, String surahArabic) {
        this.arabic = arabic;
        this.translation = translation;
        this.surah = surah;
        this.number = number;
        this.surahEnglish = surahEnglish;
        this.surahArabic = surahArabic;
    }

    /** "AL-BAQARAH 2:255 · البقرة". */
    String reference() {
        return surahEnglish.toUpperCase(java.util.Locale.ROOT) + "  " + surah + ":" + number + "  ·  " + surahArabic;
    }

    /**
     * An alquran.cloud answer for /v1/ayah/{n}/editions/quran-uthmani[,translation]: "data" is one object per
     * edition, the Arabic first.
     */
    static Ayah parse(JSONObject answer) throws JSONException {
        JSONArray data = answer.getJSONArray("data");
        JSONObject ar = data.getJSONObject(0);
        JSONObject surah = ar.getJSONObject("surah");
        String translation = data.length() > 1 ? data.getJSONObject(1).optString("text", "") : "";
        return new Ayah(ar.getString("text").trim(), translation.trim(), surah.getInt("number"), ar.getInt("numberInSurah"),
                surah.optString("englishName", ""), surah.optString("name", ""));
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("arabic", arabic).put("translation", translation).put("surah", surah)
                .put("number", number).put("surahEnglish", surahEnglish).put("surahArabic", surahArabic);
    }

    static Ayah fromJson(JSONObject o) throws JSONException {
        return new Ayah(o.getString("arabic"), o.optString("translation", ""), o.getInt("surah"), o.getInt("number"),
                o.optString("surahEnglish", ""), o.optString("surahArabic", ""));
    }
}
