package com.lisa.app.v2;

import android.content.Context;
import com.lisa.app.LisaCapabilitySystem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** V2-only intent resolver. NON esegue azioni e NON modifica il router V1. */
public final class LisaV2Dictionary {
    public static final String ASSET = "v2/dizionario.json";
    private final Map<String, Entry> byAlias;

    public static final class Entry {
        public final String id;
        public final LisaCapabilitySystem.Cap capability;
        public final Map<String, String> params;

        private Entry(String id, LisaCapabilitySystem.Cap capability,
                      Map<String, String> params) {
            this.id = id;
            this.capability = capability;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    private LisaV2Dictionary(Map<String, Entry> items) {
        byAlias = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public static LisaV2Dictionary load(Context context)
            throws IOException, JSONException {
        if (context == null) throw new IllegalArgumentException("context_null");
        try (InputStream in = context.getAssets().open(ASSET);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > 131072)
                    throw new IOException("dizionario_v2_troppo_grande");
                out.write(buf, 0, n);
            }
            return parse(new JSONObject(new String(out.toByteArray(),
                    StandardCharsets.UTF_8)));
        }
    }

    private static LisaV2Dictionary parse(JSONObject root) throws JSONException {
        if (root.getInt("schema_version") != 1)
            throw new JSONException("schema_version_non_supportata");
        if (!"lisa_v2_only".equals(root.getString("scope")))
            throw new JSONException("scope_non_v2");
        JSONArray intents = root.getJSONArray("intents");
        if (intents.length() == 0) throw new JSONException("intents_vuoti");
        Map<String, Entry> aliases = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < intents.length(); i++) {
            JSONObject item = intents.getJSONObject(i);
            String id = item.getString("id").trim();
            if (id.isEmpty() || !ids.add(id))
                throw new JSONException("id_vuoto_o_duplicato: " + id);
            LisaCapabilitySystem.Cap cap = null;
            if (item.has("capability")) {
                try {
                    cap = LisaCapabilitySystem.Cap.valueOf(
                            item.getString("capability"));
                } catch (IllegalArgumentException e) {
                    throw new JSONException("capability_sconosciuta: " + id);
                }
            }
            Map<String, String> params = new LinkedHashMap<>();
            JSONObject jsonParams = item.optJSONObject("params");
            if (item.has("params") && jsonParams == null)
                throw new JSONException("params_non_oggetto: " + id);
            if (jsonParams != null) {
                java.util.Iterator<String> keys = jsonParams.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!(jsonParams.opt(key) instanceof String))
                        throw new JSONException("parametro_non_stringa: " + id);
                    params.put(key, jsonParams.getString(key));
                }
            }
            Entry entry = new Entry(id, cap, params);
            JSONArray list = item.getJSONArray("aliases");
            if (list.length() == 0) throw new JSONException("alias_vuoti: " + id);
            for (int j = 0; j < list.length(); j++) {
                String alias = normalize(list.getString(j));
                if (alias.isEmpty() || aliases.putIfAbsent(alias, entry) != null)
                    throw new JSONException("alias_duplicato_o_vuoto: " + alias);
            }
        }
        return new LisaV2Dictionary(aliases);
    }

    /** Solo interpretazione. Il futuro dispatcher verifica contesto e sicurezza. */
    public Entry resolve(String input) {
        return byAlias.get(normalize(input));
    }

    public int aliasCount() { return byAlias.size(); }

    public static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ITALIAN)
                .replaceAll("[,;:!?.]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
