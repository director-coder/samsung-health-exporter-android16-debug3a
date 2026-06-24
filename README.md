# HealthConnectHaMqtt Android 16 diagnostic build

Android app that reads Health Connect data on the phone and publishes it to MQTT for Home Assistant / HAOS.

## Build

This project is intended to be built by GitHub Actions with:

- compileSdk 36
- targetSdk 36
- Android Gradle Plugin 8.9.1
- Gradle 8.11.1
- Health Connect SDK `androidx.health.connect:connect-client:1.1.0`

Push this ZIP contents to GitHub and run the included `Build debug APK` workflow.

## MQTT base topics

Default base topic example:

```text
home/health/pavel
```

The app publishes:

- `availability`
- `last_sync`
- `health_connect_available/state`
- `permission_status/state`
- `steps_today/state`
- `distance_meters_today/state`
- `active_calories_kcal_today/state`
- `total_calories_kcal_today/state`
- `floors_climbed_today/state`
- `heart_rate_avg_bpm/state`
- `heart_rate_min_bpm/state`
- `heart_rate_max_bpm/state`
- `latest_weight_kg/state`
- `latest_height_cm/state`
- `sleep_minutes_last_night/state`
- `exercise_sessions_today/state`
- `exercise_minutes_today/state`
- `latest_respiratory_rate_bpm/state`
- `latest_oxygen_saturation_percent/state`

Home Assistant MQTT Discovery configs are published automatically.

## Diagnostic changes in this build

This build is meant to diagnose why Samsung Health / Health Connect values are visible on the phone but some MQTT sensors show `n/a` or are missing.

Changes:

1. Numeric state topics are now always published. If a value cannot be read, the state payload is `unknown` instead of the topic being skipped.
2. Reader errors are no longer silently swallowed. Short error names/messages are published under debug topics.
3. Steps now use a fallback path: aggregate `StepsRecord.COUNT_TOTAL`, then raw `StepsRecord` records summed manually.
4. Active calories now use a fallback path: aggregate `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, then raw `ActiveCaloriesBurnedRecord` records summed manually.
5. Distance and floors also have raw-record fallback sums.
6. Heart rate now uses aggregate avg/min/max first, then falls back to raw `HeartRateRecord.samples`.
7. Sleep window was widened to catch overnight sleep sessions with awkward boundaries: from two days ago 18:00 until today 12:00, capped at now.
8. The app publishes a combined debug string to:

```text
home/health/pavel/debug/state
```

It also publishes individual debug values, for example:

```text
home/health/pavel/debug/version
home/health/pavel/debug/steps_source
home/health/pavel/debug/steps_aggregate
home/health/pavel/debug/steps_records_count
home/health/pavel/debug/active_calories_source
home/health/pavel/debug/heart_rate_records_count
home/health/pavel/debug/heart_rate_samples_count
home/health/pavel/debug/sleep_records_count
home/health/pavel/debug/oxygen_saturation_records_count
home/health/pavel/debug/respiratory_rate_records_count
```

After pressing **Sync now**, check both the normal sensor topics and `debug/state` / `debug/*` topics.
