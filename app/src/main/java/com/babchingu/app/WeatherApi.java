package com.babchingu.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 외부 API 연동: 기상청_단기예보 조회서비스 (공공데이터포털)
 * https://www.data.go.kr/data/15084084/openapi.do
 *
 * 그중 '초단기실황조회(getUltraSrtNcst)'로 학교 근처의 지금 기온(T1H)과
 * 강수형태(PTY)를 받아서 홈 화면의 식사 추천 문구로 바꿔준다.
 */
public class WeatherApi {

    // 결과를 화면에 알려주는 통로
    public interface Callback {
        void onResult(String message);
    }

    // 공공데이터포털 > 마이페이지 > 개발계정 > 기상청_단기예보 조회서비스 의
    // "일반 인증키(Decoding)"를 붙여넣기 (Encoding 키를 넣어도 동작함)
    private static final String SERVICE_KEY = "fcf1d310fb2ccbf89ce8108b8215424dc0bc46e44f09e38461ec4c813b8a4d41";

    // 기상청 격자 좌표: 서울특별시 노원구
    private static final int NX = 61;
    private static final int NY = 129;

    private static final String BASE_URL =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";

    public static void fetch(Callback cb) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // 안드로이드는 화면 담당(메인) 스레드에서 인터넷 접속을 막기 때문에 새 스레드에서 실행
        new Thread(() -> {
            String message;
            HttpURLConnection conn = null;
            try {
                String[] base = baseDateTime();

                // 이미 인코딩된 키(%가 들어 있음)면 그대로, 아니면 인코딩해서 사용
                String key = SERVICE_KEY.contains("%")
                        ? SERVICE_KEY
                        : URLEncoder.encode(SERVICE_KEY, "UTF-8");

                String urlStr = BASE_URL
                        + "?serviceKey=" + key
                        + "&pageNo=1&numOfRows=10&dataType=JSON"
                        + "&base_date=" + base[0]
                        + "&base_time=" + base[1]
                        + "&nx=" + NX + "&ny=" + NY;

                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                // response.body.items.item[] 에서 T1H(기온), PTY(강수형태) 찾기
                JSONArray items = new JSONObject(sb.toString())
                        .getJSONObject("response")
                        .getJSONObject("body")
                        .getJSONObject("items")
                        .getJSONArray("item");

                double temp = Double.NaN;
                int pty = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String category = it.getString("category");
                    String value = it.getString("obsrValue");
                    if (category.equals("T1H")) temp = Double.parseDouble(value);
                    else if (category.equals("PTY")) pty = (int) Double.parseDouble(value);
                }

                message = Double.isNaN(temp)
                        ? "🍚 오늘도 같이 밥 먹어요"
                        : makeMessage(pty, temp);
            } catch (Exception e) {
                // 키가 없거나 틀렸을 때, 인터넷이 안 될 때도 앱은 꺼지지 않게
                message = "🍚 오늘도 같이 밥 먹어요";
            } finally {
                if (conn != null) conn.disconnect();
            }

            String result = message;
            // 화면 갱신은 다시 메인 스레드에서
            mainHandler.post(() -> cb.onResult(result));
        }).start();
    }

    /**
     * 초단기실황은 매시 정각 관측값이 몇십 분 뒤에 올라온다.
     * 그래서 지금이 45분 전이면 한 시간 전 자료를 요청한다. (자정 넘어가면 날짜도 하루 전)
     * 반환: {base_date "yyyyMMdd", base_time "HH00"}
     */
    private static String[] baseDateTime() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        if (c.get(Calendar.MINUTE) < 45) {
            c.add(Calendar.HOUR_OF_DAY, -1);
        }
        String date = String.format(Locale.KOREA, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        String time = String.format(Locale.KOREA, "%02d00", c.get(Calendar.HOUR_OF_DAY));
        return new String[]{date, time};
    }

    // PTY(강수형태): 0 없음, 1 비, 2 비/눈, 3 눈, 5 빗방울, 6 빗방울눈날림, 7 눈날림
    private static String makeMessage(int pty, double temp) {
        String t = Math.round(temp) + "°C";

        if (pty == 1 || pty == 2 || pty == 5 || pty == 6) {
            return "☔ 비 와요 (" + t + ") · 오늘은 학식 같이 어때요?";
        }
        if (pty == 3 || pty == 7) {
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
