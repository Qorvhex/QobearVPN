#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include <openvpn/client/dns_options.hpp>
#include "ovpn_config.hpp"

namespace api = openvpn::ClientAPI;

namespace {

// Strict UTF-16 <-> UTF-8 conversion avoiding JNI Modified UTF-8 aborts on 4-byte Unicode or \u0000.
std::string jstring_to_utf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const jsize len = env->GetStringLength(value);
    if (len <= 0) return {};
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("GetStringChars failed");

    std::string out;
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len) {
            uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + (((cp - 0xD800) << 10) | (low - 0xDC00));
                ++i;
            }
        }
        if (cp == 0) {
            env->ReleaseStringChars(value, chars);
            throw std::runtime_error("NUL byte in string");
        } else if (cp <= 0x7F) {
            out.push_back(static_cast<char>(cp));
        } else if (cp <= 0x7FF) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

jstring utf8_to_jstring(JNIEnv* env, const std::string& input) {
    std::vector<jchar> utf16;
    utf16.reserve(input.size());
    size_t i = 0;
    while (i < input.size()) {
        uint8_t c = static_cast<uint8_t>(input[i]);
        uint32_t cp = 0xFFFD;
        if (c <= 0x7F) {
            cp = c;
            i += 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < input.size()) {
            uint8_t c1 = static_cast<uint8_t>(input[i + 1]);
            if ((c1 & 0xC0) == 0x80) {
                cp = ((c & 0x1F) << 6) | (c1 & 0x3F);
                if (cp < 0x80) cp = 0xFFFD;
                i += 2;
            } else {
                i += 1;
            }
        } else if ((c & 0xF0) == 0xE0 && i + 2 < input.size()) {
            uint8_t c1 = static_cast<uint8_t>(input[i + 1]);
            uint8_t c2 = static_cast<uint8_t>(input[i + 2]);
            if ((c1 & 0xC0) == 0x80 && (c2 & 0xC0) == 0x80) {
                cp = ((c & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F);
                if (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF)) cp = 0xFFFD;
                i += 3;
            } else {
                i += 1;
            }
        } else if ((c & 0xF8) == 0xF0 && i + 3 < input.size()) {
            uint8_t c1 = static_cast<uint8_t>(input[i + 1]);
            uint8_t c2 = static_cast<uint8_t>(input[i + 2]);
            uint8_t c3 = static_cast<uint8_t>(input[i + 3]);
            if ((c1 & 0xC0) == 0x80 && (c2 & 0xC0) == 0x80 && (c3 & 0xC0) == 0x80) {
                cp = ((c & 0x07) << 18) | ((c1 & 0x3F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F);
                if (cp < 0x10000 || cp > 0x10FFFF) cp = 0xFFFD;
                i += 4;
            } else {
                i += 1;
            }
        } else {
            i += 1;
        }

        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    static constexpr jchar kEmptyChar = 0;
    return env->NewString(utf16.empty() ? &kEmptyChar : utf16.data(), static_cast<jsize>(utf16.size()));
}

bool contains_sensitive_token(const std::string& line) {
    std::string lower = line;
    std::transform(lower.begin(), lower.end(), lower.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    for (const char* secret : {
             "password", "passwd", "auth-user-pass", "-----", "<ca>",
             "<cert>", "<key>", "<tls", "private", "username",
             "session-id", "token"
         }) {
        if (lower.find(secret) != std::string::npos) return true;
    }
    return false;
}

class Client final : public api::OpenVPNClient {
public:
    JNIEnv* env = nullptr;
    jobject callback = nullptr;
    jclass cls = nullptr;
    std::atomic<bool> cancelled{false};

    bool call(const char* name, const std::string& value, int number = 0) {
        if (!callback || !env || !cls) return false;
        jmethodID mid = env->GetMethodID(cls, name, "(Ljava/lang/String;I)Z");
        if (!mid) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return false;
        }
        jstring text = utf8_to_jstring(env, value);
        if (!text || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (text) env->DeleteLocalRef(text);
            return false;
        }
        bool result = env->CallBooleanMethod(callback, mid, text, number);
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return result;
    }

    void sendEvent(const std::string& name, bool error) {
        if (!callback || !env || !cls) return;
        jmethodID mid = env->GetMethodID(cls, "onCoreEvent", "(Ljava/lang/String;Z)V");
        if (!mid) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return;
        }
        jstring text = utf8_to_jstring(env, name);
        if (!text || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (text) env->DeleteLocalRef(text);
            return;
        }
        env->CallVoidMethod(callback, mid, text, static_cast<jboolean>(error));
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled = true;
            stop();
        }
    }

    void sendLog(const std::string& line) {
        if (!callback || !env || !cls) return;
        jmethodID mid = env->GetMethodID(cls, "onCoreLog", "(Ljava/lang/String;)V");
        if (!mid) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return;
        }
        jstring text = utf8_to_jstring(env, line);
        if (!text || env->ExceptionCheck()) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (text) env->DeleteLocalRef(text);
            return;
        }
        env->CallVoidMethod(callback, mid, text);
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    bool tun_builder_new() override { return call("configureTun", "new"); }

    bool tun_builder_add_address(const std::string& address, int prefix, const std::string&, bool, bool) override {
        return call("addAddress", address, prefix);
    }

    bool tun_builder_add_route(const std::string& address, int prefix, int, bool) override {
        return call("addRoute", address, prefix);
    }

    bool tun_builder_reroute_gw(bool v4, bool v6, unsigned int) override {
        return (!v4 || call("addRoute", "0.0.0.0", 0)) && (!v6 || call("addRoute", "::", 0));
    }

    bool tun_builder_set_remote_address(const std::string&, bool) override { return true; }

    bool tun_builder_set_mtu(int mtu) override { return call("configureTun", "mtu", mtu); }

    bool tun_builder_set_session_name(const std::string&) override { return true; }

    bool tun_builder_set_dns_options(const openvpn::DnsOptions& dns) override {
        for (const auto& [priority, server] : dns.servers) {
            (void)priority;
            for (const auto& addr : server.addresses) {
                if (addr.port && addr.port != 53) return false;
                if (!call("addDns", addr.address)) return false;
            }
        }
        for (const auto& domain : dns.search_domains) {
            if (!call("addSearchDomain", domain.domain)) return false;
        }
        return true;
    }

    int tun_builder_establish() override {
        if (cancelled || !callback || !env || !cls) return -1;
        jmethodID mid = env->GetMethodID(cls, "establishTun", "()I");
        if (!mid) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return -1;
        }
        int fd = env->CallIntMethod(callback, mid);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return -1;
        }
        return fd;
    }

    bool socket_protect(openvpn_io::detail::socket_type fd, std::string, bool) override {
        return call("configureTun", "protect", static_cast<int>(fd));
    }

    bool pause_on_connection_timeout() override { return false; }

    void event(const api::Event& event) override {
        sendEvent(event.name, event.error || event.fatal);
    }

    void acc_event(const api::AppCustomControlMessageEvent&) override {}

    void log(const api::LogInfo& info) override {
        std::string line = info.text;
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) line.pop_back();
        if (line.empty() || line.size() > 400 || contains_sensitive_token(line)) return;
        __android_log_print(ANDROID_LOG_INFO, "QobearOpenVPN", "%s", line.c_str());
        sendLog(line);
    }

    void external_pki_cert_request(api::ExternalPKICertRequest& r) override { r.error = true; }
    void external_pki_sign_request(api::ExternalPKISignRequest& r) override { r.error = true; }

    void connect_pre_run() override {
        if (cancelled) stop();
    }

    void clock_tick() override {
        if (cancelled) {
            stop();
            return;
        }
        if (!callback || !env || !cls) return;
        jmethodID mid = env->GetMethodID(cls, "onCoreTraffic", "(JJ)V");
        if (!mid) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return;
        }
        auto stats = tun_stats();
        env->CallVoidMethod(callback, mid,
                            static_cast<jlong>(stats.bytesOut),
                            static_cast<jlong>(stats.bytesIn));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled = true;
            stop();
        }
    }
};

std::mutex clientsMutex;
std::unordered_map<jlong, std::shared_ptr<Client>> clients;
std::atomic<jlong> sequence{1};

std::shared_ptr<Client> find(jlong id) {
    std::lock_guard<std::mutex> lock(clientsMutex);
    const auto it = clients.find(id);
    return it == clients.end() ? nullptr : it->second;
}

}  // namespace

#define JNI_METHOD(name) Java_com_qorvhex_qobearvpn_core_OpenVpnNative_##name

extern "C" JNIEXPORT jint JNICALL JNI_METHOD(apiVersion)(JNIEnv*, jobject) {
    return 1;
}

extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(evaluate)(JNIEnv* env, jobject, jstring text) {
    try {
        Client client;
        auto result = client.eval_config(qobear::make_openvpn_config(jstring_to_utf8(env, text)));
        return utf8_to_jstring(env, result.error ? "PROFILE_UNSUPPORTED" : "");
    } catch (...) {
        return utf8_to_jstring(env, "PROFILE_INVALID");
    }
}

extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv*, jobject) {
    try {
        auto client = std::make_shared<Client>();
        auto id = sequence++;
        std::lock_guard<std::mutex> lock(clientsMutex);
        clients.emplace(id, std::move(client));
        return id;
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(stop)(JNIEnv*, jobject, jlong id) {
    if (auto client = find(id)) {
        client->cancelled = true;
        client->stop();
    }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(release)(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(clientsMutex);
    clients.erase(id);
}

extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(run)(
    JNIEnv* env, jobject, jlong id, jstring text,
    jstring user, jstring password, jobject callback) {
    auto client = find(id);
    if (!client) return utf8_to_jstring(env, "CORE_UNAVAILABLE");

    client->env = env;
    client->callback = callback;
    client->cls = env->GetObjectClass(callback);

    std::string error;
    try {
        auto config = qobear::make_openvpn_config(jstring_to_utf8(env, text));
        auto evaluation = client->eval_config(config);
        if (evaluation.error) {
            error = "PROFILE_UNSUPPORTED";
        } else if (!client->cancelled) {
            api::ProvideCreds creds;
            creds.username = jstring_to_utf8(env, user);
            creds.password = jstring_to_utf8(env, password);
            auto status = client->provide_creds(creds);
            if (status.error) {
                error = "CREDENTIALS_REJECTED";
            } else if (!client->cancelled) {
                auto result = client->connect();
                if (result.error && !client->cancelled) {
                    error = "CONNECTION_FAILED";
                }
            }
        }
    } catch (...) {
        error = "CORE_ERROR";
    }

    if (client->cls) env->DeleteLocalRef(client->cls);
    client->cls = nullptr;
    client->callback = nullptr;
    client->env = nullptr;
    return utf8_to_jstring(env, error);
}
