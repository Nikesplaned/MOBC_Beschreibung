package app.thecity.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.gson.Gson;

import app.thecity.AppConfig;
import app.thecity.data.ThisApplication;
import dreamspace.ads.sdk.data.AdNetworkType;

public class AppConfigExt {

    // Definiere statische Variable für die allgemeine App-Konfiguration
    public static AppConfig.General general = new AppConfig.General();

    // Definiere statische Variable für die Werbekonfiguration
    public static AppConfig.Ads ads = new AppConfig.Ads();

    // Setze Daten aus dem Remote Config
    public static void setFromRemoteConfig(FirebaseRemoteConfig remote) {
        // Allgemeine Konfiguration
        if (!remote.getString("web_url").isEmpty())
            AppConfig.general.web_url = remote.getString("web_url");

        if (!remote.getString("city_lat").isEmpty()) {
            try {
                AppConfig.general.city_lat = Double.parseDouble(remote.getString("city_lat"));
            } catch (Exception ignored) {
            }
        }

        if (!remote.getString("city_lng").isEmpty()) {
            try {
                AppConfig.general.city_lng = Double.parseDouble(remote.getString("city_lng"));
            } catch (Exception ignored) {
            }
        }

        if (!remote.getString("enable_news_info").isEmpty()) {
            AppConfig.general.enable_news_info = Boolean.parseBoolean(remote.getString("enable_news_info"));
        }

        // Weitere allgemeine Konfigurationseinstellungen

        // Werbekonfiguration
        if (!remote.getString("ad_enable").isEmpty()) {
            AppConfig.ads.ad_enable = Boolean.parseBoolean(remote.getString("ad_enable"));
        }

        if (!remote.getString("ad_network").isEmpty()) {
            try {
                AppConfig.ads.ad_network = AdNetworkType.valueOf(remote.getString("ad_network"));
            } catch (Exception ignored) {
            }
        }

        // Weitere Werbekonfigurationseinstellungen

        saveToSharedPreference(); // Speichere die Konfiguration in den Shared Preferences
    }

    // Setze Daten aus den Shared Preferences
    public static void setFromSharedPreference() {
        Context context = ThisApplication.getInstance();
        SharedPreferences pref = context.getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        String jsonGeneral = pref.getString("APP_CONFIG_GENERAL", null);
        String jsonAds = pref.getString("APP_CONFIG_ADS", null);

        if (!TextUtils.isEmpty(jsonGeneral)) {
            AppConfig.general = new Gson().fromJson(jsonGeneral, AppConfig.General.class);
        }

        if (!TextUtils.isEmpty(jsonAds)) {
            AppConfig.ads = new Gson().fromJson(jsonAds, AppConfig.Ads.class);
        }
    }

    // Speichere die Konfigurationsdaten in den Shared Preferences
    private static void saveToSharedPreference() {
        Context context = ThisApplication.getInstance();
        SharedPreferences pref = context.getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        pref.edit().putString("APP_CONFIG_GENERAL", new Gson().toJson(AppConfig.general)).apply();
        pref.edit().putString("APP_CONFIG_ADS", new Gson().toJson(AppConfig.ads)).apply();
    }
}
