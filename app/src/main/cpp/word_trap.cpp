#include <jni.h>
#include <string>
#include <algorithm>
#include <cctype>
#include <chrono>
#include <sstream>

static std::string gWord1, gWord2;
static std::string gName1, gName2;
static std::chrono::steady_clock::time_point gStartTime;
static bool gGameStarted = false;
static int  gCurrentPlayer = 0;
static bool gGameOver = false;

static std::string toLower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), ::tolower);
    return s;
}

static bool containsWord(const std::string& message, const std::string& target) {
    std::string msg = toLower(message);
    std::string tgt = toLower(target);
    size_t pos = 0;
    while ((pos = msg.find(tgt, pos)) != std::string::npos) {
        bool leftOk  = (pos == 0) || !std::isalpha((unsigned char)msg[pos - 1]);
        bool rightOk = (pos + tgt.size() >= msg.size()) ||
                       !std::isalpha((unsigned char)msg[pos + tgt.size()]);
        if (leftOk && rightOk) return true;
        ++pos;
    }
    return false;
}

static std::string formatTime(int seconds) {
    if (seconds < 0) seconds = 0;
    int m = seconds / 60, s = seconds % 60;
    std::ostringstream oss;
    oss << (m < 10 ? "0" : "") << m << ":" << (s < 10 ? "0" : "") << s;
    return oss.str();
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_amanabha_dareu_MainActivity_setupGame(
        JNIEnv* env, jobject,
        jstring n1, jstring n2,
        jstring w1, jstring w2) {
    const char* cn1 = env->GetStringUTFChars(n1, nullptr);
    const char* cn2 = env->GetStringUTFChars(n2, nullptr);
    const char* cw1 = env->GetStringUTFChars(w1, nullptr);
    const char* cw2 = env->GetStringUTFChars(w2, nullptr);
    gName1 = std::string(cn1); gName2 = std::string(cn2);
    gWord1 = toLower(std::string(cw1)); gWord2 = toLower(std::string(cw2));
    env->ReleaseStringUTFChars(n1, cn1); env->ReleaseStringUTFChars(n2, cn2);
    env->ReleaseStringUTFChars(w1, cw1); env->ReleaseStringUTFChars(w2, cw2);
    gStartTime = std::chrono::steady_clock::now();
    gGameStarted = true; gGameOver = false; gCurrentPlayer = 0;
}

JNIEXPORT jstring JNICALL
Java_com_amanabha_dareu_MainActivity_getRemainingTime(JNIEnv* env, jobject) {
    if (!gGameStarted) return env->NewStringUTF("07:00");
    auto now = std::chrono::steady_clock::now();
    int elapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(now - gStartTime).count();
    return env->NewStringUTF(formatTime(420 - elapsed).c_str());
}

JNIEXPORT jint JNICALL
Java_com_amanabha_dareu_MainActivity_getRemainingSeconds(JNIEnv* env, jobject) {
    if (!gGameStarted) return 420;
    auto now = std::chrono::steady_clock::now();
    int elapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(now - gStartTime).count();
    int r = 420 - elapsed;
    return r < 0 ? 0 : r;
}

JNIEXPORT jstring JNICALL
Java_com_amanabha_dareu_MainActivity_getCurrentPlayerName(JNIEnv* env, jobject) {
    return env->NewStringUTF((gCurrentPlayer == 0 ? gName1 : gName2).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_amanabha_dareu_MainActivity_submitMessage(JNIEnv* env, jobject, jstring jmsg) {
    if (gGameOver) return env->NewStringUTF("GAMEOVER");
    auto now = std::chrono::steady_clock::now();
    int elapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(now - gStartTime).count();
    if (420 - elapsed <= 0) { gGameOver = true; return env->NewStringUTF("DRAW"); }
    const char* cmsg = env->GetStringUTFChars(jmsg, nullptr);
    std::string msg(cmsg);
    env->ReleaseStringUTFChars(jmsg, cmsg);
    std::string trapWords[2] = { gWord2, gWord1 };
    std::string players[2]   = { gName1, gName2 };
    if (containsWord(msg, trapWords[gCurrentPlayer])) {
        gGameOver = true;
        int w = 1 - gCurrentPlayer;
        std::string result = "WIN:" + players[w] + ":" + trapWords[gCurrentPlayer];
        return env->NewStringUTF(result.c_str());
    }
    gCurrentPlayer = 1 - gCurrentPlayer;
    return env->NewStringUTF("OK");
}

} // extern "C"