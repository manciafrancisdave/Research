#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <time.h>

#include "secrets.h"

const int PIN_X = 34;
const int PIN_Y = 35;
const int PIN_Z = 32;

const int PIN_LED_GREEN  = 25;
const int PIN_LED_YELLOW = 26;
const int PIN_LED_RED    = 27;
const int PIN_BUZZER     = 14;

const int PIN_SDA = 21;
const int PIN_SCL = 22;

LiquidCrystal_I2C lcd(0x27, 16, 2);

#define BUZZER_IS_ACTIVE 1

const float MV_PER_G = 330.0f;

const uint16_t CAL_SAMPLES     = 1000;
const uint8_t  CAL_INTERVAL_MS = 10;
const uint8_t  MA_WINDOW       = 8;
const uint32_t SAMPLE_US       = 5000;

const float    SIGMA_MULTIPLIER  = 3.0f;
const float    MIN_TRIGGER_G     = 0.08f;
const float    ONSET_FRACTION    = 0.50f;
const uint16_t CONFIRM_MS        = 300;
const uint8_t  MIN_SAMPLES_ABOVE = 8;
const uint16_t ALERT_HOLD_MS     = 15000;
const uint16_t COOLDOWN_MS       = 30000;

// Must stay in lockstep with Intensity.fromMagnitude in the mobile app
// (shared/src/commonMain/kotlin/com/siren/mobile/model/Models.kt) and with the
// thresholds published in the research paper:
//   Green  0.000 - 0.010 g   Intensity I-IV    light shaking
//   Yellow 0.010 - 0.120 g   Intensity V-VI    moderate shaking
//   Red    0.120 g and above Intensity VII+    destructive shaking
const float BAND_YELLOW_G = 0.010f;
const float BAND_RED_G    = 0.120f;

float biasX = 1650, biasY = 1650, biasZ = 1650;
float sigmaResultantMv = 1.0f;
float triggerG = MIN_TRIGGER_G;
float onsetG   = MIN_TRIGGER_G * ONSET_FRACTION;

uint16_t bufX[MA_WINDOW], bufY[MA_WINDOW], bufZ[MA_WINDOW];
uint8_t  bufIdx = 0;
uint32_t sumX = 0, sumY = 0, sumZ = 0;
bool     bufPrimed = false;

enum State { IDLE, CONFIRMING, ALERTING, COOLDOWN };
State state = IDLE;

uint32_t tOnset = 0, tDetect = 0, tAlert = 0, tLed = 0, tLcd = 0, tStateEnd = 0;
float    peakG = 0;
uint8_t  samplesAbove = 0;
uint16_t seq = 0;
char     lastType[10] = "shake";

uint32_t nextSampleUs = 0;

String   idToken;
String   refreshToken;
uint32_t tokenRefreshAtMs = 0;
bool     authed = false;

const char* bandName(float g) {
  if (g >= BAND_RED_G)    return "red";
  if (g >= BAND_YELLOW_G) return "yellow";
  return "green";
}

void buzzerOn() {
#if BUZZER_IS_ACTIVE
  digitalWrite(PIN_BUZZER, HIGH);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    tone(PIN_BUZZER, 2500);
  #else
    ledcSetup(0, 2500, 8);
    ledcAttachPin(PIN_BUZZER, 0);
    ledcWrite(0, 128);
  #endif
#endif
}

void buzzerOff() {
#if BUZZER_IS_ACTIVE
  digitalWrite(PIN_BUZZER, LOW);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    noTone(PIN_BUZZER);
  #else
    ledcWrite(0, 0);
  #endif
#endif
}

float readResultantG() {
  uint16_t rx = analogReadMilliVolts(PIN_X);
  uint16_t ry = analogReadMilliVolts(PIN_Y);
  uint16_t rz = analogReadMilliVolts(PIN_Z);

  if (!bufPrimed) {
    for (uint8_t i = 0; i < MA_WINDOW; i++) { bufX[i] = rx; bufY[i] = ry; bufZ[i] = rz; }
    sumX = (uint32_t)rx * MA_WINDOW;
    sumY = (uint32_t)ry * MA_WINDOW;
    sumZ = (uint32_t)rz * MA_WINDOW;
    bufPrimed = true;
  }

  sumX += rx - bufX[bufIdx]; bufX[bufIdx] = rx;
  sumY += ry - bufY[bufIdx]; bufY[bufIdx] = ry;
  sumZ += rz - bufZ[bufIdx]; bufZ[bufIdx] = rz;
  bufIdx = (bufIdx + 1) % MA_WINDOW;

  float dx = (sumX / (float)MA_WINDOW) - biasX;
  float dy = (sumY / (float)MA_WINDOW) - biasY;
  float dz = (sumZ / (float)MA_WINDOW) - biasZ;

  return sqrtf(dx * dx + dy * dy + dz * dz) / MV_PER_G;
}

void setLeds(bool g, bool y, bool r) {
  digitalWrite(PIN_LED_GREEN,  g ? HIGH : LOW);
  digitalWrite(PIN_LED_YELLOW, y ? HIGH : LOW);
  digitalWrite(PIN_LED_RED,    r ? HIGH : LOW);
}

void lcdTwoLines(const char* a, const char* b) {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print(a);
  lcd.setCursor(0, 1); lcd.print(b);
}

void printCalibration() {
  Serial.println("CAL,done");
  Serial.printf("CAL,bias_mv,%.2f,%.2f,%.2f\n", biasX, biasY, biasZ);
  Serial.printf("CAL,sigma_mv,%.4f\n", sigmaResultantMv);
  Serial.printf("CAL,sigma_g,%.5f\n", sigmaResultantMv / MV_PER_G);
  Serial.printf("CAL,trigger_g,%.4f\n", triggerG);
  Serial.printf("CAL,onset_g,%.4f\n", onsetG);
  Serial.printf("CAL,mv_per_g,%.2f\n", MV_PER_G);
}

void calibrate() {
  state = IDLE;
  setLeds(false, false, false);
  buzzerOff();

  lcdTwoLines("Calibrating...", "Keep it still");
  Serial.println("CAL,start,keep the sensor still");

  float refX = analogReadMilliVolts(PIN_X);
  float refY = analogReadMilliVolts(PIN_Y);
  float refZ = analogReadMilliVolts(PIN_Z);

  double sX = 0, sY = 0, sZ = 0;
  double qX = 0, qY = 0, qZ = 0;

  for (uint16_t i = 0; i < CAL_SAMPLES; i++) {
    double dx = analogReadMilliVolts(PIN_X) - refX;
    double dy = analogReadMilliVolts(PIN_Y) - refY;
    double dz = analogReadMilliVolts(PIN_Z) - refZ;
    sX += dx; qX += dx * dx;
    sY += dy; qY += dy * dy;
    sZ += dz; qZ += dz * dz;

    if (i % 100 == 0) { lcd.setCursor(14, 0); lcd.print((int)(i / 100)); }
    delay(CAL_INTERVAL_MS);
  }

  const double n = CAL_SAMPLES;
  biasX = refX + sX / n;
  biasY = refY + sY / n;
  biasZ = refZ + sZ / n;

  double varX = (qX - (sX * sX) / n) / (n - 1);
  double varY = (qY - (sY * sY) / n) / (n - 1);
  double varZ = (qZ - (sZ * sZ) / n) / (n - 1);
  if (varX < 0) varX = 0;
  if (varY < 0) varY = 0;
  if (varZ < 0) varZ = 0;

  sigmaResultantMv = sqrt(varX + varY + varZ);

  float thresholdG = (SIGMA_MULTIPLIER * sigmaResultantMv) / MV_PER_G;
  triggerG = max(thresholdG, MIN_TRIGGER_G);
  onsetG   = triggerG * ONSET_FRACTION;

  bufPrimed = false;
  printCalibration();

  lcdTwoLines("S.I.R.E.N. ready", "Monitoring...");
  setLeds(true, false, false);
}

void connectWifi() {
  Serial.printf("WiFi: connecting to %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 30000) {
    delay(500);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("WiFi: connected, ip=");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("WiFi: FAILED -- check SSID/password and that it is 2.4 GHz");
  }
}

void syncClock() {
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  Serial.print("NTP: syncing");
  uint32_t start = millis();
  while (time(nullptr) < 1700000000 && millis() - start < 20000) {
    delay(300);
    Serial.print('.');
  }
  Serial.println();
  Serial.printf("NTP: epoch=%lu\n", (unsigned long)time(nullptr));
}

String isoNowUtc() {
  time_t now = time(nullptr);
  struct tm t;
  gmtime_r(&now, &t);
  char buf[25];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &t);
  return String(buf);
}

int postJson(const String& url, const String& body, String& out, const char* contentType = "application/json") {
  WiFiClientSecure client;

  client.setInsecure();

  HTTPClient http;
  http.setTimeout(12000);
  if (!http.begin(client, url)) {
    out = "begin() failed";
    return -1;
  }
  http.addHeader("Content-Type", contentType);
  if (idToken.length() && url.indexOf("firestore.googleapis.com") >= 0) {
    http.addHeader("Authorization", "Bearer " + idToken);
  }

  int code = http.POST(body);
  out = http.getString();
  http.end();
  return code;
}

bool signIn() {
  String url = String("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=") + FIREBASE_API_KEY;

  JsonDocument req;
  req["email"] = ESP32_ACCOUNT_EMAIL;
  req["password"] = ESP32_ACCOUNT_PASSWORD;
  req["returnSecureToken"] = true;
  String body;
  serializeJson(req, body);

  String resp;
  int code = postJson(url, body, resp);
  if (code != 200) {
    Serial.printf("AUTH: sign-in failed http=%d %s\n", code, resp.c_str());
    authed = false;
    return false;
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp)) {
    Serial.println("AUTH: could not parse sign-in response");
    return false;
  }

  idToken      = doc["idToken"].as<String>();
  refreshToken = doc["refreshToken"].as<String>();
  uint32_t expiresIn = doc["expiresIn"].as<String>().toInt();
  if (expiresIn < 60) expiresIn = 3600;
  tokenRefreshAtMs = millis() + (expiresIn - 300) * 1000UL;
  authed = true;

  Serial.printf("AUTH: signed in as %s, token good for %lus\n",
                ESP32_ACCOUNT_EMAIL, (unsigned long)expiresIn);
  return true;
}

bool refreshIdToken() {
  if (!refreshToken.length()) return signIn();

  String url = String("https://securetoken.googleapis.com/v1/token?key=") + FIREBASE_API_KEY;
  String body = "grant_type=refresh_token&refresh_token=" + refreshToken;

  String resp;
  int code = postJson(url, body, resp, "application/x-www-form-urlencoded");
  if (code != 200) {
    Serial.printf("AUTH: refresh failed http=%d, falling back to sign-in\n", code);
    return signIn();
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp)) return signIn();

  idToken      = doc["id_token"].as<String>();
  refreshToken = doc["refresh_token"].as<String>();
  uint32_t expiresIn = doc["expires_in"].as<String>().toInt();
  if (expiresIn < 60) expiresIn = 3600;
  tokenRefreshAtMs = millis() + (expiresIn - 300) * 1000UL;
  authed = true;

  Serial.println("AUTH: token refreshed");
  return true;
}

void keepAuthFresh() {
  if (!authed) { signIn(); return; }
  if ((int32_t)(millis() - tokenRefreshAtMs) >= 0) refreshIdToken();
}

String makeAlertId(uint16_t s) {
  return String(NODE_ID) + "-" + String((unsigned long)time(nullptr)) + "-" + String(s);
}

bool createAlert(const char* intensity, float magnitudeG, uint16_t s, String& alertIdOut) {
  alertIdOut = makeAlertId(s);

  String url = String("https://firestore.googleapis.com/v1/projects/") + FIREBASE_PROJECT_ID +
               "/databases/(default)/documents/alerts?documentId=" + alertIdOut;

  JsonDocument doc;
  JsonObject f = doc["fields"].to<JsonObject>();
  f["intensity"]["stringValue"]     = intensity;
  f["magnitudeG"]["doubleValue"]    = magnitudeG;
  f["detectedAt"]["timestampValue"] = isoNowUtc();
  f["source"]["stringValue"]        = "esp32";
  f["nodeId"]["stringValue"]        = NODE_ID;
  f["closed"]["booleanValue"]       = false;

  String body;
  serializeJson(doc, body);

  String resp;
  int code = postJson(url, body, resp);

  if (code == 401 || code == 403) {
    Serial.println("FS: token rejected, refreshing and retrying once");
    if (refreshIdToken()) code = postJson(url, body, resp);
  }

  if (code == 200) return true;

  Serial.printf("FS: create failed http=%d %s\n", code, resp.c_str());
  return false;
}

void uploadAlert(const char* band, float g) {
  uint32_t t0 = millis();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.printf("CLOUD,ERR,offline,%lu\n", (unsigned long)(millis() - t0));
    return;
  }
  keepAuthFresh();
  if (!authed) {
    Serial.printf("CLOUD,ERR,auth,%lu\n", (unsigned long)(millis() - t0));
    return;
  }

  String alertId;
  if (createAlert(band, g, seq, alertId)) {
    Serial.printf("CLOUD,OK,%s,%lu\n", alertId.c_str(), (unsigned long)(millis() - t0));
  } else {
    Serial.printf("CLOUD,ERR,write,%lu\n", (unsigned long)(millis() - t0));
  }
}

void fireAlert(float g) {
  tAlert = millis();
  const char* band = bandName(g);

  bool isRed    = (g >= BAND_RED_G);
  bool isYellow = (!isRed && g >= BAND_YELLOW_G);
  setLeds(!isRed && !isYellow, isYellow, isRed);
  tLed = millis();

  char line2[20];
  // 3 decimals, not 2: the Green band is only 0.010 g wide, so "%.2f" would
  // round a 0.005 g reading to "0.01 g" -- exactly the Yellow boundary.
  snprintf(line2, sizeof(line2), "%.3f g  %s", g, band);
  if (isRed)         lcdTwoLines("RED - TAKE COVER", line2);
  else if (isYellow) lcdTwoLines("YELLOW - ALERT",   line2);
  else               lcdTwoLines("GREEN - MINOR",    line2);
  tLcd = millis();

  if (isRed) buzzerOn();

  seq++;

  Serial.printf("TRIAL,%u,%s,%.3f,%s,%lu,%lu,%lu,%lu,%lu\n",
                seq, lastType, g, band,
                (unsigned long)(tDetect - tOnset),
                (unsigned long)(tAlert - tDetect),
                (unsigned long)(tLed - tAlert),
                (unsigned long)(tLcd - tAlert),
                (unsigned long)(tLcd - tOnset));

  state = ALERTING;
  tStateEnd = millis() + ALERT_HOLD_MS;

  uploadAlert(band, g);
}

void rejectAsNoise(float g) {
  Serial.printf("REJECT,%s,%.3f,%u,below confirmation window\n", lastType, g, samplesAbove);
  state = IDLE;
}

void endAlert() {
  buzzerOff();
  setLeds(true, false, false);
  lcdTwoLines("S.I.R.E.N. ready", "Monitoring...");
  state = COOLDOWN;
  tStateEnd = millis() + COOLDOWN_MS;
}

void handleCommand(char c) {
  switch (c) {
    case 'C': case 'c': calibrate(); break;
    case 'Z': case 'z': printCalibration(); break;
    case 'S': case 's': endAlert(); break;
    case 'W': case 'w':
      Serial.printf("NET,wifi=%d,ip=%s,authed=%d,epoch=%lu\n",
                    WiFi.status() == WL_CONNECTED ? 1 : 0,
                    WiFi.localIP().toString().c_str(),
                    authed ? 1 : 0, (unsigned long)time(nullptr));
      break;
    case 'G': case 'g': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.22f); break;
    case 'Y': case 'y': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.48f); break;
    case 'R': case 'r': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.85f); break;
    default: break;
  }
}

void pumpConsole() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\r' || c == '\n') continue;
    handleCommand(c);
  }
}

void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(PIN_LED_GREEN,  OUTPUT);
  pinMode(PIN_LED_YELLOW, OUTPUT);
  pinMode(PIN_LED_RED,    OUTPUT);
  pinMode(PIN_BUZZER,     OUTPUT);
  buzzerOff();

  analogReadResolution(12);
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  analogSetAttenuation(ADC_ATTEN_DB_12);
#else
  analogSetAttenuation(ADC_11db);
#endif

  Wire.begin(PIN_SDA, PIN_SCL);
  lcd.init();
  lcd.backlight();
  lcdTwoLines("S.I.R.E.N.", "Booting...");

  setLeds(true, true, true); delay(400); setLeds(false, false, false);

  Serial.println("\nBOOT,siren-esp32,v2.0-singleboard");
  Serial.printf("node=%s project=%s\n", NODE_ID, FIREBASE_PROJECT_ID);
  Serial.println("HEADER,seq,type,peak_g,intensity,detect_ms,process_ms,led_ms,lcd_ms,total_ms");

  connectWifi();
  syncClock();
  signIn();

  delay(1500);
  calibrate();
  nextSampleUs = micros();
}

void loop() {
  pumpConsole();

  static uint32_t lastCheck = 0;
  if (millis() - lastCheck > 30000) {
    lastCheck = millis();
    if (WiFi.status() != WL_CONNECTED) connectWifi();
    else keepAuthFresh();
  }

  if ((int32_t)(micros() - nextSampleUs) < 0) return;
  nextSampleUs += SAMPLE_US;

  float g = readResultantG();
  uint32_t now = millis();

  switch (state) {

    case IDLE:
      if (g >= onsetG && tOnset == 0) tOnset = now;
      if (g < onsetG) tOnset = 0;
      if (g >= triggerG) {
        if (tOnset == 0) tOnset = now;
        tDetect      = now;
        peakG        = g;
        samplesAbove = 1;
        strcpy(lastType, "shake");
        state = CONFIRMING;
      }
      break;

    case CONFIRMING:
      if (g > peakG) peakG = g;
      if (g >= triggerG && samplesAbove < 255) samplesAbove++;
      if (now - tDetect >= CONFIRM_MS) {
        if (samplesAbove >= MIN_SAMPLES_ABOVE) fireAlert(peakG);
        else { rejectAsNoise(peakG); tOnset = 0; }
      }
      break;

    case ALERTING:
      if (g > peakG) peakG = g;
      if ((int32_t)(now - tStateEnd) >= 0) endAlert();
      break;

    case COOLDOWN:
      if ((int32_t)(now - tStateEnd) >= 0) {
        state  = IDLE;
        tOnset = 0;
        peakG  = 0;
        nextSampleUs = micros();
      }
      break;
  }
}
