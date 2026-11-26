#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "ovpn_config.hpp"

class CheckClient final : public openvpn::ClientAPI::OpenVPNClient {
public:
    bool tun_builder_new() override { return true; }
    int tun_builder_establish() override { return -1; }
    void event(const openvpn::ClientAPI::Event&) override {}
    void acc_event(const openvpn::ClientAPI::AppCustomControlMessageEvent&) override {}
    void log(const openvpn::ClientAPI::LogInfo&) override {}
    void external_pki_cert_request(openvpn::ClientAPI::ExternalPKICertRequest& r) override { r.error = true; }
    void external_pki_sign_request(openvpn::ClientAPI::ExternalPKISignRequest& r) override { r.error = true; }
    bool socket_protect(openvpn_io::detail::socket_type, std::string, bool) override { return true; }
    bool pause_on_connection_timeout() override { return false; }
};

int main(int argc, char** argv) {
    if (argc < 2) {
        std::cerr << "Usage: qobear_profile_check <file.ovpn> [...]\n";
        return 2;
    }
    int failed = 0;
    for (int i = 1; i < argc; ++i) {
        std::ifstream file(argv[i]);
        if (!file) {
            std::cerr << "FAIL (unreadable): " << argv[i] << '\n';
            failed++;
            continue;
        }
        std::ostringstream ss;
        ss << file.rdbuf();
        try {
            CheckClient client;
            auto result = client.eval_config(qobear::make_openvpn_config(ss.str()));
            if (result.error) {
                std::cerr << "FAIL (" << result.message << "): " << argv[i] << '\n';
                failed++;
            } else {
                std::cout << "PASS: " << argv[i] << " -> " << result.remoteHost << ':' << result.remotePort
                          << ' ' << result.remoteProto << '\n';
            }
        } catch (const std::exception& e) {
            std::cerr << "FAIL (" << e.what() << "): " << argv[i] << '\n';
            failed++;
        }
    }
    return failed == 0 ? 0 : 1;
}
