package com.babchingu.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 외부 API 연동: Open-Meteo 현재 날씨 (API 키 불필요)
 * https://open-meteo.com/en/docs
 * 학교(서울 노원구) 좌표의 현재 기온·날씨 코드를 받아 식사 추천 문구로 바꿔준다.
 */
public class WeatherApi {

    // 결과를 화면에 알려주는 통로
    public interface Callback {
        void onResult(String message);
    }

    // 학교 위치 (위도, 경도)
    private static final double LAT = 37.6194;
    private static final double LON = 127.0597;

    private static final String URL_STR =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + LAT
            + "&longitude=" + LON
            + "&current=temperature_2m,weather_code"
            + "&timezone=Asia%2FSeoul";

    public static void fetch(Callback cb) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // 안드로이드는 화면 담당(메인) 스레드에서 인터넷 접속을 막기 때문에 새 스레드에서 실행
        new Thread(() -> {
            String message;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(URL_STR).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject current = new JSONObject(sb.toString())
                        .getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                int code = current.getInt("weather_code");
                message = makeMessage(code, temp);
            } catch (Exception e) {
                message = "🍚 오늘도 같이 밥 먹어요";   // 실패해도 앱이 꺼지지 않게
            } finally {
                if (conn != null) conn.disconnect();
            }

            String result = message;
            // 화면 갱신은 다시 메인 스레드에서
            mainHandler.post(() -> cb.onResult(result));
        }).start();
    }

    // WMO 날씨 코드 → 식사 추천 문구
    private static String makeMessage(int code, double temp) {
        String t = Math.round(temp) + "°C";

        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
        boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean storm = code >= 95;

        if (storm) {
            return "⛈️ 천둥번개 (" + t + ") · 오늘은 학식에서 안전하게!";
        }
        if (rain) {
            return "☔ 비 와요 (" + t + ") · 오늘은 학식 같이 어때요?";
        }
        if (snow) {
            return "❄️ 눈 와요 (" + t + ") · 멀리 가지 말고 학식!";
        }
        if (temp >= 28) {
            return "☀️ 더워요 (" + t + ") · 시원한 거 같이 먹어요";
        }
        if (temp <= 3) {
            return "🥶 추워요 (" + t + ") · 따뜻한 국물 같이?";
        }
        return "🍚 " + t + " · 같이 밥 먹기 좋은 날";
    }
}
