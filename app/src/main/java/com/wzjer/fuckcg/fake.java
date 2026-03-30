package com.wzjer.fuckcg;

import android.content.Context;
import android.util.Log;

import com.wzjer.fuckcg.cg.SportLatLngBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// 创建虚假运动数据的核心类
public class fake {
    private static final String TAG = "fake";
    private static volatile Context appContext;

    public static void bind(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static com.wzjer.fuckcg.cg.UploadJsonSports generateFakeSportBean(Context context) {
        bind(context);
        com.wzjer.fuckcg.cg.SportBean sportBean = new com.wzjer.fuckcg.cg.SportBean();
        com.wzjer.fuckcg.cg.UploadJsonSports uploadJsonSports = new com.wzjer.fuckcg.cg.UploadJsonSports();
        try {
            double TARGET_KM = 2;
            String sportId = UUID.randomUUID().toString();

            double plannedOdometerKm = TARGET_KM + Math.random() / 3.0;
            double plannedMinutes = 5.0 * TARGET_KM + Math.random() * TARGET_KM * 3.0;
            long durationMillis = Math.max(60_000L, (long) (plannedMinutes * 60_000));
            double durationMinutes = durationMillis / 60_000.0;

            long now = System.currentTimeMillis();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Calendar calBegin = Calendar.getInstance();
            calBegin.setTimeInMillis(now - durationMillis);
            String beginTime = dateFormat.format(calBegin.getTime());
            long beginTimestamp = calBegin.getTimeInMillis();

            Calendar calEnd = Calendar.getInstance();
            calEnd.setTimeInMillis(now);
            String endTime = dateFormat.format(calEnd.getTime());

            int isValid = 1;
            String isValidReason = "";

            double targetMeters = plannedOdometerKm * 1000.0;
            int stepCount = estimateStepCount(targetMeters);

            List<SportLatLngBean> routeData = generateFakeRouteData(appContext, beginTimestamp, durationMinutes);
            if (routeData.isEmpty()) {
                routeData = createFallbackRouteData(beginTimestamp, durationMinutes, plannedOdometerKm);
            }
            routeData = normalizeRouteData(routeData, beginTimestamp, durationMillis, targetMeters, stepCount);

            double odometer = targetMeters / 1000.0;
            double speed = odometer / durationMinutes * 60.0;
            String avgSpeed = format2(speed);
            String avgPace = formatPaceFromSpeed(speed);
            int paceKmCount = Math.max(0, (int) Math.floor(odometer));
            double remainKm = Math.max(0.0, odometer - paceKmCount);
            long lastOdometerMillis = Math.round((remainKm / Math.max(speed, 0.0001)) * 3_600_000.0);
            String lastOdometerTime = formatDurationHms(lastOdometerMillis);
            String activeTime = formatDurationHms(durationMillis);
            double calorie = Math.round((320.0 / 30.0) * durationMinutes * 10.0) / 10.0;
            double stepMinute = stepCount / durationMinutes;

            int minuteCount = Math.max(1, (int) Math.round(durationMinutes));
            List<Double> minuteSpeedValues = buildMinuteSpeedSeries(speed, minuteCount);
            double maxSpeedVal = speed;
            double minSpeedVal = speed;
            for (int i = 0; i < minuteSpeedValues.size(); i++) {
                double v = minuteSpeedValues.get(i);
                maxSpeedVal = Math.max(maxSpeedVal, v);
                minSpeedVal = Math.min(minSpeedVal, v);

                com.wzjer.fuckcg.cg.CminuteSpeedstr cminuteSpeedstr = new com.wzjer.fuckcg.cg.CminuteSpeedstr();
                cminuteSpeedstr.min = String.valueOf(i + 1);
                cminuteSpeedstr.v = format2(v);
                sportBean.addCminuteSpeedstr(cminuteSpeedstr);
            }
            String maxSpeed = format2(maxSpeedVal);
            String minSpeed = format2(minSpeedVal);

            for (int i = 1; i <= paceKmCount; i++) {
                com.wzjer.fuckcg.cg.Cpacestr cpacestr = new com.wzjer.fuckcg.cg.Cpacestr();
                cpacestr.km = String.valueOf(i);
                double kmSpeed = speed * (0.97 + Math.random() * 0.06);
                cpacestr.t = formatPaceFromSpeed(kmSpeed);
                sportBean.addPaceStr(cpacestr);
            }

            for (SportLatLngBean point : routeData) {
                sportBean.addSportLatLngBean(point);
            }

            // 汇总字段回填，确保与轨迹数据一致。
            sportBean.sportId = sportId;
            sportBean.startDate = beginTime;
            sportBean.endDate = endTime;
            sportBean.startDateLong = beginTimestamp;
            sportBean.endDateLong = now;
            sportBean.distance = odometer;
            sportBean.validOdometer = odometer;
            sportBean.totalStep = stepCount;
            sportBean.stepMinute = format2(stepMinute);
            sportBean.avgSpeed = avgSpeed;
            sportBean.maxSpeedPerHour = maxSpeed;
            sportBean.minSpeedPerHour = minSpeed;
            sportBean.lastOdometerTime = lastOdometerTime;
            sportBean.avgPace = avgPace;
            sportBean.cal = calorie;
            sportBean.dlTime = activeTime;
            sportBean.dllc = format2(odometer);
            sportBean.activityStatus = com.wzjer.fuckcg.cg.SportBean.ACTIVITY_STATUS_FINISH;
            sportBean.validCount = isValid;
            sportBean.isValidReason = isValidReason;

            // 生成最终上传体
            uploadJsonSports.activeTime = activeTime;
            uploadJsonSports.alreadyPassPoint = "1;2;3;4;5";
            uploadJsonSports.alreadyPassPointResult = "";
            uploadJsonSports.avgPace = avgPace;
            uploadJsonSports.avgSpeed = avgSpeed;
            uploadJsonSports.beganPoint = routeData.get(0).a + "|" + routeData.get(0).o;
            uploadJsonSports.beginTime = beginTime;
            uploadJsonSports.calorie = calorie;
            uploadJsonSports.coordinate = routeData;
            uploadJsonSports.endPoint = routeData.get(routeData.size() - 1).a + "|" + routeData.get(routeData.size() - 1).o;
            uploadJsonSports.endTime = endTime;
            uploadJsonSports.indoor = 0;
            uploadJsonSports.isValid = 1;
            uploadJsonSports.isValidReason = "";
            uploadJsonSports.lastOdometerTime = lastOdometerTime;
            uploadJsonSports.maxSpeedPerHour = maxSpeed;
            uploadJsonSports.minSpeedPerHour = minSpeed;
            uploadJsonSports.minuteSpeed = sportBean.minuteSpeedStr;
            uploadJsonSports.modementMode = "1";
            uploadJsonSports.name = "学生真实姓名，请注意填充";
            uploadJsonSports.needPassPointCount = "5";
            uploadJsonSports.odometer = format2(odometer);
            uploadJsonSports.pace = sportBean.paceStr;
            uploadJsonSports.phoneVersion = "Android,31,13|2.9.5"; // 感谢 小米13Pro 在本次破解中的大力支持
            uploadJsonSports.planRouteName = "校内定向线路";
            uploadJsonSports.routeId = "81";
            uploadJsonSports.routePolylineBh = "14756949";
            uploadJsonSports.sportId = sportId;
            uploadJsonSports.sportImages = "";
            uploadJsonSports.stepCount = stepCount;
            uploadJsonSports.stepMinute = format2(stepMinute);
            uploadJsonSports.xh = "学生真实学号，请注意填充";
            uploadJsonSports.randomPointStr = "[]";
        } catch (Exception e) {
            Log.e(TAG, "generateFakeSportBean failed", e);
        }
        return uploadJsonSports;
    }

    public static com.wzjer.fuckcg.cg.UploadJsonSports generateFakeSportBean(Context context, String studentId, String studentName) {
        com.wzjer.fuckcg.cg.UploadJsonSports uploadJsonSports = generateFakeSportBean(context);
        uploadJsonSports.xh = studentId == null ? "" : studentId.trim();
        uploadJsonSports.name = studentName == null ? "" : studentName.trim();
        return uploadJsonSports;
    }

    public static com.wzjer.fuckcg.cg.UploadJsonSports generateFakeSportBean(Context context, String studentId, String studentName, long beginTimestampMs) {
        bind(context);
        com.wzjer.fuckcg.cg.SportBean sportBean = new com.wzjer.fuckcg.cg.SportBean();
        com.wzjer.fuckcg.cg.UploadJsonSports uploadJsonSports = new com.wzjer.fuckcg.cg.UploadJsonSports();
        try {
            double TARGET_KM = 2;
            String sportId = UUID.randomUUID().toString();

            double plannedOdometerKm = TARGET_KM + Math.random() / 3.0;
            double plannedMinutes = 5.0 * TARGET_KM + Math.random() * TARGET_KM * 3.0;
            long durationMillis = Math.max(60_000L, (long) (plannedMinutes * 60_000));
            double durationMinutes = durationMillis / 60_000.0;

            // beginTime 锁定为传入的 beginTimestampMs，endTime = beginTime + durationMillis
            long endTimestamp = beginTimestampMs + durationMillis;

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Calendar calBegin = Calendar.getInstance();
            calBegin.setTimeInMillis(beginTimestampMs);
            String beginTime = dateFormat.format(calBegin.getTime());

            Calendar calEnd = Calendar.getInstance();
            calEnd.setTimeInMillis(endTimestamp);
            String endTime = dateFormat.format(calEnd.getTime());

            int isValid = 1;
            String isValidReason = "";

            double targetMeters = plannedOdometerKm * 1000.0;
            int stepCount = estimateStepCount(targetMeters);

            List<SportLatLngBean> routeData = generateFakeRouteData(appContext, beginTimestampMs, durationMinutes);
            if (routeData.isEmpty()) {
                routeData = createFallbackRouteData(beginTimestampMs, durationMinutes, plannedOdometerKm);
            }
            routeData = normalizeRouteData(routeData, beginTimestampMs, durationMillis, targetMeters, stepCount);

            double odometer = targetMeters / 1000.0;
            double speed = odometer / durationMinutes * 60.0;
            String avgSpeed = format2(speed);
            String avgPace = formatPaceFromSpeed(speed);
            int paceKmCount = Math.max(0, (int) Math.floor(odometer));
            double remainKm = Math.max(0.0, odometer - paceKmCount);
            long lastOdometerMillis = Math.round((remainKm / Math.max(speed, 0.0001)) * 3_600_000.0);
            String lastOdometerTime = formatDurationHms(lastOdometerMillis);
            String activeTime = formatDurationHms(durationMillis);
            double calorie = Math.round((320.0 / 30.0) * durationMinutes * 10.0) / 10.0;
            double stepMinute = stepCount / durationMinutes;

            int minuteCount = Math.max(1, (int) Math.round(durationMinutes));
            List<Double> minuteSpeedValues = buildMinuteSpeedSeries(speed, minuteCount);
            double maxSpeedVal = speed;
            double minSpeedVal = speed;
            for (int i = 0; i < minuteSpeedValues.size(); i++) {
                double v = minuteSpeedValues.get(i);
                maxSpeedVal = Math.max(maxSpeedVal, v);
                minSpeedVal = Math.min(minSpeedVal, v);

                com.wzjer.fuckcg.cg.CminuteSpeedstr cminuteSpeedstr = new com.wzjer.fuckcg.cg.CminuteSpeedstr();
                cminuteSpeedstr.min = String.valueOf(i + 1);
                cminuteSpeedstr.v = format2(v);
                sportBean.addCminuteSpeedstr(cminuteSpeedstr);
            }
            String maxSpeed = format2(maxSpeedVal);
            String minSpeed = format2(minSpeedVal);

            for (int i = 1; i <= paceKmCount; i++) {
                com.wzjer.fuckcg.cg.Cpacestr cpacestr = new com.wzjer.fuckcg.cg.Cpacestr();
                cpacestr.km = String.valueOf(i);
                double kmSpeed = speed * (0.97 + Math.random() * 0.06);
                cpacestr.t = formatPaceFromSpeed(kmSpeed);
                sportBean.addPaceStr(cpacestr);
            }

            for (SportLatLngBean point : routeData) {
                sportBean.addSportLatLngBean(point);
            }

            sportBean.sportId = sportId;
            sportBean.startDate = beginTime;
            sportBean.endDate = endTime;
            sportBean.startDateLong = beginTimestampMs;
            sportBean.endDateLong = endTimestamp;
            sportBean.distance = odometer;
            sportBean.validOdometer = odometer;
            sportBean.totalStep = stepCount;
            sportBean.stepMinute = format2(stepMinute);
            sportBean.avgSpeed = avgSpeed;
            sportBean.maxSpeedPerHour = maxSpeed;
            sportBean.minSpeedPerHour = minSpeed;
            sportBean.lastOdometerTime = lastOdometerTime;
            sportBean.avgPace = avgPace;
            sportBean.cal = calorie;
            sportBean.dlTime = activeTime;
            sportBean.dllc = format2(odometer);
            sportBean.activityStatus = com.wzjer.fuckcg.cg.SportBean.ACTIVITY_STATUS_FINISH;
            sportBean.validCount = isValid;
            sportBean.isValidReason = isValidReason;

            uploadJsonSports.activeTime = activeTime;
            uploadJsonSports.alreadyPassPoint = "1;2;3;4;5";
            uploadJsonSports.alreadyPassPointResult = "";
            uploadJsonSports.avgPace = avgPace;
            uploadJsonSports.avgSpeed = avgSpeed;
            uploadJsonSports.beganPoint = routeData.get(0).a + "|" + routeData.get(0).o;
            uploadJsonSports.beginTime = beginTime;
            uploadJsonSports.calorie = calorie;
            uploadJsonSports.coordinate = routeData;
            uploadJsonSports.endPoint = routeData.get(routeData.size() - 1).a + "|" + routeData.get(routeData.size() - 1).o;
            uploadJsonSports.endTime = endTime;
            uploadJsonSports.indoor = 0;
            uploadJsonSports.isValid = 1;
            uploadJsonSports.isValidReason = "";
            uploadJsonSports.lastOdometerTime = lastOdometerTime;
            uploadJsonSports.maxSpeedPerHour = maxSpeed;
            uploadJsonSports.minSpeedPerHour = minSpeed;
            uploadJsonSports.minuteSpeed = sportBean.minuteSpeedStr;
            uploadJsonSports.modementMode = "1";
            uploadJsonSports.name = studentName == null ? "" : studentName.trim();
            uploadJsonSports.needPassPointCount = "5";
            uploadJsonSports.odometer = format2(odometer);
            uploadJsonSports.pace = sportBean.paceStr;
            uploadJsonSports.phoneVersion = "Android,31,13|2.9.5";
            uploadJsonSports.planRouteName = "校内定向线路";
            uploadJsonSports.routeId = "81";
            uploadJsonSports.routePolylineBh = "14756949";
            uploadJsonSports.sportId = sportId;
            uploadJsonSports.sportImages = "";
            uploadJsonSports.stepCount = stepCount;
            uploadJsonSports.stepMinute = format2(stepMinute);
            uploadJsonSports.xh = studentId == null ? "" : studentId.trim();
            uploadJsonSports.randomPointStr = "[]";
        } catch (Exception e) {
            Log.e(TAG, "generateFakeSportBean failed", e);
        }
        return uploadJsonSports;
    }

    private static String padZero(double num)  {
        int intValue = (int) num; // 将double转换为int，截断小数部分
        if (intValue < 10) {
            return "0" + intValue;
        }
        return String.valueOf(intValue);
    }

    // Keep generated values near baseline while avoiding implausible outliers.
    private static double jitter(double base, double ratio) {
        double factor = 1.0 + (ThreadLocalRandom.current().nextDouble(-1.0, 1.0) * ratio);
        return base * factor;
    }

    private static int jitterInt(int base, double ratio) {
        return (int) Math.max(0, Math.round(jitter(base, ratio)));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<SportLatLngBean> generateFakeRouteData(Context context, long beginTimestamp, double minutes) {
        List<SportLatLngBean> routeData = new ArrayList<>();
        Context safeContext = context != null ? context.getApplicationContext() : appContext;
        if (safeContext == null) {
            Log.w(TAG, "Context is null, skip loading route data from assets.");
            return routeData;
        }

        long expectedCount = Math.max(1L, (long) (minutes * 60 / 2));
        long ts = beginTimestamp;

        try (InputStream is = safeContext.getAssets().open("example.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                json.append(line);
            }

            JSONArray array = new JSONArray(json.toString());
            int count = (int) Math.min(expectedCount, array.length());
            double totalDistance = 0.0;

            for (int i = 0; i < count; i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                SportLatLngBean bean = new SportLatLngBean();

                // Add meter-level coordinate jitter so each run is close to example.json but not identical.
                double rawLat = obj.optDouble("a", 0.0);
                double rawLng = obj.optDouble("o", 0.0);
                double latJitter = ThreadLocalRandom.current().nextDouble(-0.00003, 0.00003);
                double lngJitter = ThreadLocalRandom.current().nextDouble(-0.00003, 0.00003);
                bean.a = rawLat + latJitter;
                bean.o = rawLng + lngJitter;

                // Lightly vary telemetry around baseline values.
                bean.ac = Math.max(0.0, jitter(obj.optDouble("ac", 0.0), 0.15));
                bean.d = Math.max(0.0, jitter(obj.optDouble("d", 0.0), 0.08));
                bean.s = clamp(Math.max(0.0, jitter(obj.optDouble("s", 0.0), 0.10)), 0.0, 15.0);
                bean.st = jitterInt(obj.optInt("st", 0), 0.08);
                bean.sta = jitterInt(obj.optInt("sta", 0), 0.05);
                bean.v = obj.optInt("v", 1);

                totalDistance += bean.d;
                bean.da = totalDistance;

                // 轨迹起始时间与本次运动对齐，点间隔保持 2 秒。
                bean.t = ts;
                ts += 2000L;

                routeData.add(bean);
            }
        } catch (Exception e) {
            Log.e(TAG, "generateFakeRouteData failed", e);
        }

        return routeData;
    }

    private static List<SportLatLngBean> createFallbackRouteData(long beginTimestamp, double minutes, double targetKm) {
        List<SportLatLngBean> routeData = new ArrayList<>();
        long durationMillis = Math.max(2000L, (long) (minutes * 60_000));
        double totalMeters = Math.max(targetKm * 1000.0, 1.0);
        double baseLat = 32.060255;
        double baseLng = 118.796877;
        double deltaLng = totalMeters / 111_320.0;

        SportLatLngBean start = new SportLatLngBean();
        start.a = baseLat;
        start.o = baseLng;
        start.ac = 0.0;
        start.d = 0.0;
        start.da = 0.0;
        start.s = 0.0;
        start.st = 0;
        start.sta = 0;
        start.t = beginTimestamp;
        start.v = 1;
        routeData.add(start);

        SportLatLngBean end = new SportLatLngBean();
        end.a = baseLat;
        end.o = baseLng + deltaLng;
        end.ac = 0.0;
        end.d = totalMeters;
        end.da = totalMeters;
        end.s = totalMeters / Math.max(1.0, durationMillis / 1000.0);
        end.st = 120;
        end.sta = Math.max(1, (int) Math.round(totalMeters / 0.64));
        end.t = beginTimestamp + durationMillis;
        end.v = 1;
        routeData.add(end);

        return routeData;
    }

    private static String format2(double value) {
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private static int estimateStepCount(double targetMeters) {
        double strideMeters = 0.62 + Math.random() * 0.10;
        return Math.max(1000, (int) Math.round(targetMeters / strideMeters));
    }

    private static String formatDurationHms(long durationMillis) {
        long totalSeconds = Math.max(0L, durationMillis / 1000L);
        long h = totalSeconds / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;
        return padZero(h) + ":" + padZero(m) + ":" + padZero(s);
    }

    private static List<Double> buildMinuteSpeedSeries(double avgSpeed, int minuteCount) {
        List<Double> values = new ArrayList<>();
        if (minuteCount <= 0) {
            values.add(Math.max(0.1, avgSpeed));
            return values;
        }

        double rawSum = 0.0;
        for (int i = 0; i < minuteCount; i++) {
            double factor = 0.92 + Math.random() * 0.16;
            double v = Math.max(0.1, avgSpeed * factor);
            values.add(v);
            rawSum += v;
        }

        double mean = rawSum / minuteCount;
        double scale = mean > 0 ? avgSpeed / mean : 1.0;
        for (int i = 0; i < values.size(); i++) {
            values.set(i, Math.max(0.1, values.get(i) * scale));
        }
        return values;
    }

    private static List<SportLatLngBean> normalizeRouteData(List<SportLatLngBean> routeData,
                                                             long beginTimestamp,
                                                             long durationMillis,
                                                             double targetMeters,
                                                             int totalStep) {
        if (routeData == null || routeData.isEmpty()) {
            return routeData;
        }

        if (routeData.size() == 1) {
            SportLatLngBean clone = new SportLatLngBean();
            clone.a = routeData.get(0).a;
            clone.o = routeData.get(0).o;
            clone.v = 1;
            clone.t = beginTimestamp + durationMillis;
            clone.d = targetMeters;
            clone.da = targetMeters;
            clone.sta = totalStep;
            routeData.add(clone);
        }

        long prevTs = beginTimestamp;
        double prevDa = 0.0;
        int prevSta = 0;
        int count = routeData.size();

        for (int i = 0; i < count; i++) {
            SportLatLngBean point = routeData.get(i);
            if (point == null) {
                continue;
            }

            double progress = (double) i / (double) (count - 1);
            long currentTs = beginTimestamp + Math.round(durationMillis * progress);
            point.t = currentTs;
            point.ac = 0.0;
            point.v = point.v <= 0 ? 1 : point.v;

            if (i == 0) {
                point.d = 0.0;
                point.da = 0.0;
                point.s = 0.0;
                point.st = 0;
                point.sta = 0;
                prevTs = currentTs;
                continue;
            }

            double da = targetMeters * progress;
            double d = Math.max(0.0, da - prevDa);
            long dtMs = Math.max(1L, currentTs - prevTs);
            double dtSec = dtMs / 1000.0;
            int sta = (int) Math.round(totalStep * progress);
            int stepDelta = Math.max(0, sta - prevSta);
            int cadence = (int) Math.round(stepDelta * 60.0 / dtSec);

            point.da = da;
            point.d = d;
            point.s = d / dtSec;
            point.st = Math.max(0, cadence);
            point.sta = Math.min(totalStep, Math.max(prevSta, sta));

            prevDa = da;
            prevTs = currentTs;
            prevSta = point.sta;
        }

        SportLatLngBean last = routeData.get(routeData.size() - 1);
        last.t = beginTimestamp + durationMillis;
        last.da = targetMeters;
        last.sta = totalStep;
        return routeData;
    }

    private static String formatPaceFromSpeed(double speedKmPerHour) {
        if (Double.isNaN(speedKmPerHour) || Double.isInfinite(speedKmPerHour) || speedKmPerHour <= 0) {
            return "99'59''";
        }

        double paceMinutes = 60.0 / speedKmPerHour;
        int min = (int) paceMinutes;
        int sec = (int) Math.round((paceMinutes - min) * 60.0);

        if (sec >= 60) {
            min += 1;
            sec = 0;
        }

        if (min > 99) {
            min = 99;
            sec = 59;
        }

        return padZero(min) + "'" + padZero(sec) + "''";
    }
}
